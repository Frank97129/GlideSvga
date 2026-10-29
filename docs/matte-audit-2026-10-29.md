# SVGA 遮罩与资源释放检查（2026-10-29）

基线：`v5.0.8`，修复分支：`codex/glide-5.0.9`。

本轮检查范围是 SVGA 遮罩相关的数据解析、逐帧 sprite 分组、Canvas 绘制、
Bitmap 生命周期及 Glide 回收，不是整个 Ark 项目的全面审计。

## 已确认并修复

### 1. Glide 缓存池收到的 Bitmap 随后被回收（高优先级）

`SVGAGlideResourceDelegate.recycleImage()` 原先先调用 `bitmapPool.put()`，
随后 `recycle()` 调用 `videoItem.clear()`。两者持有的是同一张 imageMap，
后者把刚交给 Glide 的 Bitmap 又 `recycle()` 了。

实际 Android + Glide LruBitmapPool 测试中，再次取图抛出：
`IllegalStateException: Can't call reconfigure() on a recycled bitmap`。
多个图片 key 引用同一个 Bitmap 时，还会重复入池。

修复：按 Bitmap 对象身份去重，先清空 SVGA 图片表再转交 Glide。
独立使用 SVGAVideoEntity 时的释放逻辑不变。没有扩大缓存或改变播放设置。

### 2. 缺失遮罩或绘制异常时，没有恢复绘制状态（中优先级）

`SVGACanvasDrawer.drawFrame()` 原先只在找到遮罩的 `let` 内恢复 saveLayer。
找不到遮罩时，图层保持打开，后续 sprite 也留在未提交的图层内。
动态绘制回调抛异常时，Canvas 状态和借出的 sprite 也没有清理。

修复：组结束时无论遮罩是否存在均关闭对应图层；整帧通过 `finally`
恢复入口 save count，并归还 sprite。原异常仍传给调用者，不吞掉错误。

## 已发现但没有直接修改

### 3. 每次绘制遮罩都会新建全画布 Bitmap 和 Canvas

`ShareValues.shareMatteCanvas()` 中的缓存变量从未赋值。同尺寸连续调用 60 次，
实际产生 60 张 Bitmap。这是确定的分配行为，但取消复用不是用户项目误删代码：
上游在 2019 年修改遮罩逻辑时主动注释了复用代码，提交没有说明具体原因。

- [原复用实现](https://github.com/svga/SVGAPlayer-Android/commit/3df95bb)
- [随后修改遮罩逻辑并注释复用](https://github.com/svga/SVGAPlayer-Android/commit/07e7d1156ecc)

本次没有直接打开这些注释。必须验证同一帧多个遮罩、连续透明帧、尺寸改变，
尤其是硬件绘制记录延迟执行时，共用可变 Bitmap 是否会串图。
这仍可能是内存分配和卡顿热点，但没有证据证明它就是用户本次卡顿的唯一原因。

### 4. 当前设备的软件 Canvas 遮罩输出不符合预期

构造左右两组遮罩，预期左红右蓝；在 Android 16 设备的软件 Canvas 上，
原版实际输出整幅蓝色。中间帧把左遮罩 alpha 设为 0，也没有正确裁剪。
读回遮罩 Bitmap 的 alpha 数据正确，异常位于合成结果。

这是特定设备/绘制路径上的复现，不能据此断言所有 GPU 渲染都同样出错。
本次未更换遮罩格式或混合算法：这类修改会影响画面和内存，应单独验证。
原版与修复版软件像素签名一致，只表示本轮没有改变该行为。

## 其余检查结论与边界

- matte 的 alpha 为 0 时仍保留在 frame sprites 中，属于必要行为；不能把它
  当作普通透明 sprite 删除，否则可能失去用于裁掉内容的透明遮罩。
- 以首个 sprite 是否是 `.matte` 判断整帧是否有遮罩，依赖素材排序。
  本次没有真实反例素材证明该约定被违反，因此没有扩大修改解析/分组规则。
- Path、文字及最后一次遮罩缓冲保存在 drawer 中。停止与释放是不同动作；
  需要保留最后一帧或共享 video 的场景，不能无条件 recycle 全部资源。
- 播放取消、共享 video 的多目标生命周期做了代码检查，但没有完成全链路
  实机回归，不能声称已经验证全部行为。

## 验证记录

在连接的 vivo V2425A / Android 16 上，通过独立 `app_process` 使用真实
Android Bitmap/Canvas 和真实 Glide 5.0.5 LruBitmapPool 运行：

- 回收与异常清理共 10 项断言：原版失败 8 项，修复后失败 0 项。
- 原版与修复版：3 个画布尺寸 × 3 个帧位置的软件输出签名相同。
- 单独验证独立 video 的 clear 仍回收其拥有的图片。
- 修复的两份 Kotlin 源文件已实际编译，再与 v5.0.8 其余类组合运行。

没有运行完整 Gradle 发布构建，没有安装或重启 Ark App，没有证明内存下降了
多少，也没有验证“进场动画全程不卡”。独立硬件绘制探针进程被系统终止，
未获得有效 GPU 对照结果，不作为通过项。不能把本记录当作完整发布验收。

可复用测试见 `tools/android-regression/`。当前提交不改变 Ark 的依赖版本。
