#!/bin/sh
set -eu
cd "$(dirname "$0")"
task_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
task_jar="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
mkdir -p build
printf '%s\n' src/optimizer/*.java > build/sources.txt
"$task_javac" -encoding UTF-8 --release 17 -d build @build/sources.txt
"$task_jar" --create --file ServerPlacementLab.jar --main-class optimizer.Main -C build optimizer
printf '%s\n' 'Built ServerPlacementLab.jar (Java 17+)'
