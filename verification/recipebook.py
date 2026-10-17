"""Focused real-client check of native recipe-book unlocks; no personal worlds used."""
from pathlib import Path
import json, shutil, subprocess, zipfile, uuid, os

ROOT = Path(__file__).resolve().parents[1]
MC = Path.home() / 'Library/Application Support/minecraft'
INSTANCE = MC / 'versions/26.3-Fabric'
JAVA = Path.home() / 'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home/bin'
MC=Path(os.environ.get('MINECRAFT_HOME',str(MC)))
INSTANCE=Path(os.environ.get('MINECRAFT_INSTANCE',str(INSTANCE)))
JAVA=Path(os.environ.get('JAVA_HOME',str(JAVA.parent)))/'bin'
BUILD = ROOT / 'build'
RUN = BUILD / 'verification-recipebook'
MODS = RUN / 'mods'
CLASSES = BUILD / 'checks-recipebook'
MODS.mkdir(parents=True, exist_ok=True)
CLASSES.mkdir(exist_ok=True)
cp = (BUILD / 'verification-classpath.txt').read_text()
jar = BUILD / 'libs/oborokage-tachi-0.3.0.jar'
source = ROOT / 'verification/src/oborokage/check/RecipeBookChecks.java'
subprocess.run([str(JAVA / 'javac'), '-proc:none', '--release', '25', '-cp', cp + ':' + str(jar), '-d', str(CLASSES), str(source)], check=True)
for old in MODS.glob('*.jar'):
    old.unlink()
for item in (INSTANCE / 'mods').glob('*.jar'):
    with zipfile.ZipFile(item) as z:
        metadata = json.loads(z.read('fabric.mod.json')) if 'fabric.mod.json' in z.namelist() else {}
    if metadata.get('id') != 'oborokage':
        shutil.copy2(item, MODS / item.name)
shutil.copy2(jar, MODS / jar.name)
with zipfile.ZipFile(MODS / 'oborokage-recipebook-checks.jar', 'w') as z:
    for p in CLASSES.rglob('*.class'):
        z.write(p, p.relative_to(CLASSES))
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'oborokage_recipebook_checks', 'version': '1', 'environment': 'client', 'entrypoints': {'client': ['oborokage.check.RecipeBookChecks']}}))
world = RUN / 'saves/temporary-recipebook-world'
# Singleplayer level.dat can restore the previous local player's inventory even
# with a new UUID. Recreate only this generated test world for a clean inventory.
if world.exists():
    shutil.rmtree(world)
shutil.copytree(BUILD / 'verification-expansion-server/temporary-tachi-world', world)
(RUN / 'options.txt').write_text('lang:zh_cn\nguiScale:2\nrenderDistance:4\nsimulationDistance:4\nmaxFps:60\nenableVsync:true\npauseOnLostFocus:false\n')
cmd = [str(JAVA / 'java'), '-XstartOnFirstThread', '-Xmx2200M', '--enable-native-access=ALL-UNNAMED', '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED', '-cp', cp, 'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', 'RecipeBookTest', '--version', '26.3', '--gameDir', str(RUN), '--assetsDir', str(MC / 'assets'), '--assetIndex', '34', '--uuid', str(uuid.uuid4()), '--accessToken', '0', '--userType', 'legacy', '--width', '1280', '--height', '800', '--quickPlaySingleplayer', 'temporary-recipebook-world']
print('Isolated recipe-book check:', RUN, flush=True)
with (BUILD / 'recipebook-launch.log').open('w') as log:
    result = subprocess.run(cmd, cwd=RUN, stdout=log, stderr=subprocess.STDOUT, timeout=180)
print((RUN / 'recipebook-results.txt').read_text())
assert result.returncode == 0 and (RUN / 'recipebook-results.txt').read_text().count('PASS ') == 6
