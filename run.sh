#!/bin/sh
set -e

WITHER_SOURCE="src/vyntri-wither-storm/com/vyntriloop/minecraft/witherstorm/VyntriWitherStormPlugin.java"
WITHER_DESCRIPTOR="src/vyntri-wither-storm/plugin.yml"
WITHER_BUILD_DIR=".vyntri-build/wither-storm"
WITHER_JAR="plugins/VyntriWitherStorm.jar"

AUTH_SOURCE="src/eagler-password-auth/com/vyntriloop/minecraft/passwordauth/EaglerPasswordAuthPlugin.java"
AUTH_DESCRIPTOR="src/eagler-password-auth/plugin.yml"
AUTH_BUILD_DIR=".vyntri-build/eagler-password-auth"
AUTH_JAR="plugins/EaglerPasswordAuth.jar"
EAGLER_API_JAR="plugins/EaglerXServer.jar"

build_plugin() {
    source_file="$1"
    descriptor_file="$2"
    build_dir="$3"
    output_jar="$4"
    classpath="$5"

    rm -rf "$build_dir"
    mkdir -p "$build_dir/classes" plugins

    javac \
        -encoding UTF-8 \
        -source 8 \
        -target 8 \
        -cp "$classpath" \
        -d "$build_dir/classes" \
        "$source_file"

    cp "$descriptor_file" "$build_dir/classes/plugin.yml"
    jar cf "$output_jar" -C "$build_dir/classes" .
    echo "Built $output_jar"
}

if command -v javac >/dev/null 2>&1 && command -v jar >/dev/null 2>&1; then
    build_plugin \
        "$WITHER_SOURCE" \
        "$WITHER_DESCRIPTOR" \
        "$WITHER_BUILD_DIR" \
        "$WITHER_JAR" \
        "paper-1.12.2.jar"

    if [ ! -f "$EAGLER_API_JAR" ]; then
        echo "Eagler password authentication could not be built because $EAGLER_API_JAR is missing."
        exit 1
    fi

    build_plugin \
        "$AUTH_SOURCE" \
        "$AUTH_DESCRIPTOR" \
        "$AUTH_BUILD_DIR" \
        "$AUTH_JAR" \
        "paper-1.12.2.jar:$EAGLER_API_JAR"
else
    if [ ! -f "$WITHER_JAR" ]; then
        echo "VyntriWitherStorm could not be built because javac/jar are unavailable."
        exit 1
    fi

    if [ ! -f "$AUTH_JAR" ]; then
        echo "EaglerPasswordAuth could not be built because javac/jar are unavailable."
        exit 1
    fi
fi

exec java -Xmx2G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dcom.mojang.eula.agree=true -jar paper-1.12.2.jar
