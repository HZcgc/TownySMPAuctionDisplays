#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_CLASSES="$PROJECT_DIR/build/test-classes"

bash "$PROJECT_DIR/build.sh" >/dev/null
mkdir -p "$TEST_CLASSES"
mapfile -t TEST_SOURCES < <(find "$PROJECT_DIR/tests" -name '*.java' -print | sort)

java -m jdk.compiler/com.sun.tools.javac.Main \
  --release 17 \
  -cp "$PROJECT_DIR/build/stub-classes:$PROJECT_DIR/build/classes" \
  -d "$TEST_CLASSES" \
  "${TEST_SOURCES[@]}"

java -ea -cp "$PROJECT_DIR/build/stub-classes:$PROJECT_DIR/build/classes:$TEST_CLASSES" \
  de.townysmp.auctiondisplay.AuctionBridgeTest
