#!/bin/sh
set -e

PLUGIN_SOURCE="src/vyntri-wither-storm/com/vyntriloop/minecraft/witherstorm/VyntriWitherStormPlugin.java"
PLUGIN_DESCRIPTOR="src/vyntri-wither-storm/plugin.yml"
PLUGIN_BUILD_DIR=".vyntri-build/wither-storm"
PLUGIN_JAR="plugins/VyntriWitherStorm.jar"

if command -v javac >/dev/null 2>&1 && command -v jar >/dev/null 2>&1; then
    rm -rf "$PLUGIN_BUILD_DIR"
    mkdir -p "$PLUGIN_BUILD_DIR/classes" plugins

    javac \
        -encoding UTF-8 \
        -source 8 \
        -target 8 \
        -cp paper-1.12.2.jar \
        -d "$PLUGIN_BUILD_DIR/classes" \
        "$PLUGIN_SOURCE"

    cp "$PLUGIN_DESCRIPTOR" "$PLUGIN_BUILD_DIR/classes/plugin.yml"
    jar cf "$PLUGIN_JAR" -C "$PLUGIN_BUILD_DIR/classes" .
    echo "Built $PLUGIN_JAR"
else
    if [ ! -f "$PLUGIN_JAR" ]; then
        echo "VyntriWitherStorm could not be built because javac/jar are unavailable."
        exit 1
    fi
fi

exec java -Xmx2G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dcom.mojang.eula.agree=true -jar paper-1.12.2.jar
