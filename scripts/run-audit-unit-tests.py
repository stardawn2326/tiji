"""Run compiled JUnit classes directly when the Windows Gradle worker cannot load Unicode paths.
First compileDebugUnitTestKotlin and exportAuditClasspath with scripts/architecture-audit.init.gradle.
"""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--java", default="D:/android/jdk17/bin/java.exe")
parser.add_argument("--build-dir", default="app/build/architecture-audit-20260929")
parser.add_argument("--classpath", default="outputs/audit-classpath.txt")
args = parser.parse_args()
root = Path(args.build_dir).resolve()
classpath = os.pathsep.join([
    str(root / "tmp/kotlin-classes/debug"),
    str(root / "intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    Path(args.classpath).read_text(encoding="utf-8-sig"),
])
classes = ["com.tiji.mistakes." + path.stem
           for path in sorted(Path("app/src/test/java/com/tiji/mistakes").glob("*Test.kt"))]
if not classes:
    raise SystemExit("No JUnit test sources found")
raise SystemExit(subprocess.call([args.java, "-cp", classpath, "org.junit.runner.JUnitCore", *classes]))
