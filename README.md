# GlideSvga

通过 Glide 加载和播放 SVGA 动画的 Android 库。

## 引入依赖

先在项目根目录的 `settings.gradle.kts` 中加入 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

然后在 App 模块的 `build.gradle.kts` 中引入依赖：

```kotlin
plugins {
    kotlin("kapt")
}

dependencies {
    implementation("com.github.Frank97129:GlideSvga:<version-tag>")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
}
```

把 `<version-tag>` 替换成 GitHub Release 或 tag，例如 `v1.0.1`。

当前仓库内的 demo app 使用本地模块方式调试：

```kotlin
implementation(project(":lib-svga"))
```

## 注册 Glide 组件

在使用方 App 中添加一个 `AppGlideModule`：

```kotlin
package your.package.name

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.svga.glide.SVGAGlideEx
import java.io.File

@GlideModule
class CustomGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        val cachePath = context.cacheDir.absolutePath + File.separatorChar + "glide-svga"
        SVGAGlideEx.register(glide, registry, cachePath)
    }
}
```

## 加载 SVGA

```kotlin
import com.svga.glide.clearSvga
import com.svga.glide.loadSvga
import com.svga.glide.pauseSvga
import com.svga.glide.resumeSvga

imageView.loadSvga("file:///android_asset/demo.svga")
imageView.pauseSvga(true)
imageView.resumeSvga(true)
imageView.clearSvga()
```

网络 SVGA 需要在 `AndroidManifest.xml` 中声明网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 发布到 JitPack

提交代码后创建并推送 tag：

```bash
git tag v1.0.1
git push origin v1.0.1
```

JitPack 只构建并发布 `lib-svga` 模块。因为当前只发布一个 artifact，JitPack 会把它挂到仓库根坐标下，其他 Android 项目通过下面的方式引入：

```kotlin
implementation("com.github.Frank97129:GlideSvga:v1.0.1")
```
