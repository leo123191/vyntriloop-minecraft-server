#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PLUGIN_SOURCE="vyntri-wither-storm-src/com/vyntriloop/minecraft/witherstorm/VyntriWitherStormPlugin.java"
PLUGIN_DESCRIPTOR="vyntri-wither-storm-src/plugin.yml"
BUILD_DIR=".vyntri-build/wither-storm-1.21"
OUTPUT_JAR="plugins/VyntriWitherStorm.jar"

if ! command -v javac >/dev/null 2>&1; then
    echo "Java compiler (javac) was not found."
    exit 1
fi

if ! command -v jar >/dev/null 2>&1; then
    echo "Java jar tool was not found."
    exit 1
fi

if [ ! -f "$PLUGIN_SOURCE" ] || [ ! -f "$PLUGIN_DESCRIPTOR" ]; then
    echo "Wither Storm source files are missing."
    exit 1
fi

CLASSPATH="$(find libraries -type f -name '*.jar' -print | tr '\n' ':')"
if [ -z "$CLASSPATH" ]; then
    echo "Paper libraries were not found in server-1.21/libraries."
    exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" plugins

javac \
    --release 21 \
    -encoding UTF-8 \
    -cp "$CLASSPATH" \
    -d "$BUILD_DIR/classes" \
    "$PLUGIN_SOURCE"

cp "$PLUGIN_DESCRIPTOR" "$BUILD_DIR/classes/plugin.yml"
jar --create --file "$OUTPUT_JAR" -C "$BUILD_DIR/classes" .

echo "Built $OUTPUT_JAR"
echo "Restart Paper 1.21.4 to load VyntriWitherStorm."
