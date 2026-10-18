#!/bin/sh
# Dev-only: build the 26.3 jar, run the visual rig, and tile the shots into build/<name>.png
set -e
cd "$(dirname "$0")/../.."
export JAVA_HOME="$HOME/Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home"
./gradlew build -Pmc_target=26.3 --offline -q 2>&1 | grep -v '^Note' || true
MODE=${MODE:-showcase}; rm -rf build/verification-26.3-client-$MODE
python3 verification/run.py client --$MODE > build/showcase.log 2>&1 || { grep -A3 'error:' build/showcase.log | cut -c1-200 | head -20; exit 1; }
grep -iE "Unable to bake|Missing textures in model oborokage" build/verification-26.3-client-$MODE/logs/latest.log || true
bash tools/dev/sheet.sh build/verification-26.3-client-$MODE "$PWD/build/${1:-sheet}.png" ${2:-3} ${3:-700}
