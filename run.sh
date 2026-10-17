#!/bin/sh
set -eu
cd "$(dirname "$0")"
task_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "$task_java" >/dev/null 2>&1; then
    printf '%s\n' 'Java 17+ is required. Install a JDK/JRE or set JAVA_HOME to your Java installation.' >&2
    exit 1
fi
exec "$task_java" -Xmx512m -jar ServerPlacementLab.jar "$@"
