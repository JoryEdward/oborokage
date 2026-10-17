"""Build an isolated, server-test-only Minecraft instance under build/mc-home.

Usage: python3 verification/make_instance.py <minecraft-version> <fabric-api-version>
Then:  MINECRAFT_HOME=build/mc-home MINECRAFT_INSTANCE=build/mc-home/versions/<v>-Fabric \
       python3 verification/run.py server

Only official sources are used: Mojang's piston-meta for the game and its libraries,
maven.fabricmc.net for Fabric API. Loader, ASM and Mixin are copied from the local launcher
library cache that verification/run.py already relies on. Nothing outside build/ is written.
"""
from pathlib import Path
import hashlib, json, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / 'build/mc-home'
LAUNCHER = Path.home() / 'Library/Application Support/minecraft'
version, api = sys.argv[1], sys.argv[2]


def get(url):
    # System curl uses the OS trust store; certificate verification stays on.
    return subprocess.run(['curl', '-fsSL', '--max-time', '300', url],
                          check=True, capture_output=True).stdout


def fetch(url, dest, sha1=None):
    dest = Path(dest)
    if dest.exists() and (sha1 is None or hashlib.sha1(dest.read_bytes()).hexdigest() == sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    data = get(url)
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        raise SystemExit(f'SHA-1 mismatch for {url}')
    dest.write_bytes(data)
    return dest


manifest = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
entry = next(v for v in manifest['versions'] if v['id'] == version)
meta = json.loads(get(entry['url']))
instance = HOME / 'versions' / f'{version}-Fabric'
instance.mkdir(parents=True, exist_ok=True)
client = meta['downloads']['client']
fetch(client['url'], instance / f'{version}-Fabric.jar', client['sha1'])
for lib in meta['libraries']:
    art = lib.get('downloads', {}).get('artifact')
    if art:
        fetch(art['url'], HOME / 'libraries' / art['path'], art['sha1'])
for rel in ['net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar',
            'net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar'] + [
        f'org/ow2/asm/{n}/9.10.1/{n}-9.10.1.jar'
        for n in ['asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util']]:
    dest = HOME / 'libraries' / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    if not dest.exists():
        shutil.copy2(LAUNCHER / 'libraries' / rel, dest)
(instance / f'{version}-Fabric.json').write_text(json.dumps(meta))
# Client runs need the asset index and objects; reuse the launcher cache read-only via symlinks
# and fetch only objects it lacks into build/.
index = meta['assetIndex']
fetch(index['url'], HOME / 'assets/indexes' / f"{index['id']}.json", index['sha1'])
for obj in json.loads((HOME / 'assets/indexes' / f"{index['id']}.json").read_text())['objects'].values():
    h = obj['hash']
    dest = HOME / 'assets/objects' / h[:2] / h
    if dest.exists():
        continue
    cached = LAUNCHER / 'assets/objects' / h[:2] / h
    dest.parent.mkdir(parents=True, exist_ok=True)
    if cached.exists():
        dest.symlink_to(cached)
    else:
        fetch(f'https://resources.download.minecraft.net/{h[:2]}/{h}', dest, h)
mods = instance / 'mods'
mods.mkdir(exist_ok=True)
for old in mods.glob('fabric-api-*.jar'):
    old.unlink()
fetch(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar',
      mods / f'fabric-api-{api}.jar')
print(instance)
