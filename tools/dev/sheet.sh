#!/bin/sh
# Dev-only: sheet.sh <run-dir> <out.png> [cols] [width] — pairs showcase-labels.txt with screenshots.
D=$(cd "$1" && pwd); OUT=$2; COLS=${3:-4}; W=${4:-640}
J="$HOME/Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home/bin"
N=$(ls "$D"/screenshots/*.png | wc -l | tr -d ' ')
ARGS=$(paste -d= <(grep -v '^PASS\|^FAIL' "$D/showcase-labels.txt" | head -n "$N" | tr ' ' '_') <(ls "$D"/screenshots/*.png | sort) | tr '\n' ' ')
"$J/java" -Djava.awt.headless=true "$(dirname "$0")/ContactSheet.java" "$OUT" "$COLS" "$W" $ARGS
