#!/bin/sh
set -eu
cd "$(dirname "$0")"
task_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
task_jar="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
task_build=$(mktemp -d "${TMPDIR:-/tmp}/placement-build.XXXXXX")
trap 'rm -rf "$task_build"' EXIT HUP INT TERM
"$task_javac" -encoding UTF-8 --release 17 -d "$task_build" src/optimizer/Main.java src/optimizer/brain/*.java src/optimizer/ui/*.java src/optimizer/experiments/*.java tests/optimizer/brain/*.java tests/optimizer/ui/*.java
"$task_jar" --create --file ServerPlacementLab.jar --main-class optimizer.Main -C "$task_build" optimizer
printf '%s\n' 'Built ServerPlacementLab.jar (Java 17+)'
