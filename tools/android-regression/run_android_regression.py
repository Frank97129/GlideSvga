"""Run lifecycle regressions without installing an APK or restarting the host app."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--android-jar', type=Path, required=True)
    parser.add_argument('--d8-jar', type=Path, required=True)
    parser.add_argument('--svga-aar', type=Path, required=True)
    parser.add_argument('--glide-aar', type=Path, required=True)
    parser.add_argument('--dependency', type=Path, action='append', default=[],
                        help='Runtime JAR: Kotlin stdlib, Wire JVM, Okio JVM (repeat option).')
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--rendering', action='store_true', help='Run software and HWUI matte checks.')
    parser.add_argument('--benchmark', action='store_true', help='Run the isolated 720x720 GPU benchmark.')
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    java = args.java_home / 'bin' / ('java' + suffix)
    javac = args.java_home / 'bin' / ('javac' + suffix)
    source = Path(__file__).with_name('MatteLifecycleRegression.java')
    sources = [source]
    if args.rendering or args.benchmark:
        sources.append(source.with_name('MatteRenderingRegression.java'))
    adb = [args.adb, '-s', args.serial]
    remote = '/data/local/tmp/svga-lifecycle-regression.dex'
    with tempfile.TemporaryDirectory(prefix='svga-regression-') as temp:
        folder = Path(temp)
        jars = []
        for name, aar in [('svga', args.svga_aar), ('glide', args.glide_aar)]:
            jar = folder / (name + '.jar')
            with zipfile.ZipFile(aar) as archive:
                jar.write_bytes(archive.read('classes.jar'))
            jars.append(jar)
        jars.extend(args.dependency)
        classes = folder / 'classes'
        classes.mkdir()
        run(javac, '-source', '8', '-target', '8', '-classpath',
            os.pathsep.join(str(p) for p in [args.android_jar, *jars]),
            '-d', classes, *sources)
        probe = folder / 'probe.jar'
        with zipfile.ZipFile(probe, 'w') as archive:
            for file in classes.rglob('*.class'):
                archive.write(file, file.relative_to(classes).as_posix())
        dex = folder / 'dex'
        dex.mkdir()
        run(java, '-Xmx1g', '-cp', args.d8_jar, 'com.android.tools.r8.D8',
            '--min-api', '29', '--lib', args.android_jar, '--output', dex, probe, *jars)
        try:
            run(*adb, 'push', dex / 'classes.dex', remote)
            main_class = ('MatteRenderingRegression' if args.rendering or args.benchmark
                          else 'MatteLifecycleRegression')
            command = (f'CLASSPATH={remote} app_process /system/bin '
                       f'com.opensource.svgaplayer.drawer.{main_class}')
            if args.benchmark:
                command += ' --benchmark'
            run(*adb, 'shell', command)
        finally:
            run(*adb, 'shell', 'rm', '-f', remote)


if __name__ == '__main__':
    main()
