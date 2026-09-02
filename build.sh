#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
STUB_CLASSES="$BUILD_DIR/stub-classes"
PLUGIN_CLASSES="$BUILD_DIR/classes"
OUTPUT_DIR="$PROJECT_DIR/dist"
JAR_FILE="$OUTPUT_DIR/TownySMPAuctionDisplays-1.0.2.jar"

rm -rf "$BUILD_DIR" "$OUTPUT_DIR"
mkdir -p "$STUB_CLASSES" "$PLUGIN_CLASSES" "$OUTPUT_DIR"

mapfile -t STUB_SOURCES < <(find "$PROJECT_DIR/build-stubs" -name '*.java' -print | sort)
mapfile -t PLUGIN_SOURCES < <(find "$PROJECT_DIR/src/main/java" -name '*.java' -print | sort)

java -m jdk.compiler/com.sun.tools.javac.Main \
  --release 17 \
  -d "$STUB_CLASSES" \
  "${STUB_SOURCES[@]}"

java -m jdk.compiler/com.sun.tools.javac.Main \
  --release 17 \
  -Xlint:all \
  -cp "$STUB_CLASSES" \
  -d "$PLUGIN_CLASSES" \
  "${PLUGIN_SOURCES[@]}"

cp -R "$PROJECT_DIR/src/main/resources/." "$PLUGIN_CLASSES/"

(
  cd "$PLUGIN_CLASSES"
  java -m jdk.jartool/sun.tools.jar.Main --create --file "$JAR_FILE" .
)

echo "$JAR_FILE"
