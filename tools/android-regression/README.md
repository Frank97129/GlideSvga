# SVGA lifecycle regression probe

This probe uses Android's real software Canvas/Bitmap and Glide's real LruBitmapPool
in an isolated `app_process`. It does not install an APK, interact with the app UI,
or restart the app. Run on an authorized Android 10+ device with USB debugging.

Supply a built release AAR plus its matching Glide AAR and runtime dependency JARs.
The `lib_svga_release` internal JVM names are used, so a debug AAR is not suitable.
The runner only compiles the probe; it does not invoke Gradle or download dependencies.

```text
python run_android_regression.py
  --java-home <JDK directory>
  --android-jar <SDK>/platforms/android-36/android.jar
  --d8-jar <SDK>/build-tools/36.0.0/lib/d8.jar
  --svga-aar <release AAR>
  --glide-aar <glide-5.0.5.aar>
  --dependency <kotlin-stdlib-2.2.10.jar>
  --dependency <wire-runtime-jvm-4.4.1.jar>
  --dependency <okio-jvm-3.0.0.jar>
  --adb <SDK>/platform-tools/adb
  --serial <device serial>
```

Join the command onto one line, or use your shell's continuation syntax.
Exit code 0 and `TOTAL_FAILURES=0` confirm the lifecycle checks passed.
`PIXELS` lines are software-rendering signatures for comparing before/after on the
same device. Equal signatures mean unchanged output, not that the old rendering is correct.

Coverage:

- Missing matte closes its content layer and does not swallow following sprites.
- Dynamic drawing exceptions propagate while restoring Canvas save count and
  returning borrowed sprites to their pool.
- Released SVGA images remain live after ownership transfers to Glide.
- Aliased image keys do not put the same Bitmap into the pool twice.
- Repeated resource release is safe; Glide can reuse the returned Bitmap.
- Standalone video cleanup still recycles images it owns.

This is not a hardware-renderer, performance, animation-cancellation, or full-App test.
It does not assert that sharing one mutable matte Bitmap across hardware draw calls
is safe. See `docs/matte-audit-2026-10-29.md` for unresolved audit findings.
