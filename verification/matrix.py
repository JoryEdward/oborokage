"""Build every Minecraft line and run the server suite plus the client smoke run on each 26.x release.

Usage: python3 verification/matrix.py [--skip-build] [--server-only]
Real launcher instances are used where they exist (26.1.2, 26.3); the others are isolated
instances from verification/make_instance.py under build/mc-home. Summary: build/matrix.json.
"""
from pathlib import Path
import hashlib, json, os, re, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
MC = Path.home() / 'Library/Application Support/minecraft'
HOME = ROOT / 'build/mc-home'
VERSION = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M)[1]
# game version -> (compile line, Fabric API used for an isolated instance)
MATRIX = {
    '26.1': ('26.1', '0.145.1+26.1'),
    '26.1.1': ('26.1', '0.145.4+26.1.1'),
    '26.1.2': ('26.1', '0.155.3+26.1.2'),
    '26.2': ('26.2', '0.161.0+26.2'),
    '26.3': ('26.3', '0.162.0+26.3'),
}
REAL = {'26.1.2': MC / 'versions/26.1.2-Fabric-DH', '26.3': MC / 'versions/26.3-Fabric'}

dist = ROOT / 'dist'
dist.mkdir(exist_ok=True)
if '--skip-build' not in sys.argv:
    for line in sorted({v[0] for v in MATRIX.values()}):
        subprocess.run(['./gradlew', 'build', f'-Pmc_target={line}', '-q'], cwd=ROOT, check=True)
        for jar in (ROOT / 'build/libs').glob(f'*+{line}*.jar'):
            shutil.copy2(jar, dist / jar.name)

summary = {}
modes = [['server']] + ([] if '--server-only' in sys.argv else [['client', '--smoke']])
for game, (line, api) in MATRIX.items():
    env = dict(os.environ)
    if game in REAL and REAL[game].exists():
        env['MINECRAFT_INSTANCE'] = str(REAL[game])
    else:
        subprocess.run([sys.executable, str(ROOT / 'verification/make_instance.py'), game, api], check=True)
        env['MINECRAFT_HOME'] = str(HOME)
        env['MINECRAFT_INSTANCE'] = str(HOME / 'versions' / f'{game}-Fabric')
    jar = dist / f'oborokage-tachi-{VERSION}+{line}.jar'
    env['OBOROKAGE_JAR'] = str(jar)
    summary[game] = {'jar': jar.name, 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
    for mode in modes:
        log = ROOT / 'build' / f'matrix-{game}-{mode[0]}.log'
        with log.open('w') as out:
            code = subprocess.run([sys.executable, str(ROOT / 'verification/run.py')] + mode,
                                  cwd=ROOT, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
        text = log.read_text()
        passed = int(m[1]) if (m := re.search(r'Verified result records: (\d+)', text)) else 0
        summary[game][mode[0]] = {'exit': code, 'pass': passed, 'log': log.name}
        print(game, mode[0], 'exit', code, 'pass', passed, flush=True)
(ROOT / 'build/matrix.json').write_text(json.dumps(summary, indent=2) + '\n')
failed = [g for g, r in summary.items() for k, v in r.items() if isinstance(v, dict) and v['exit']]
sys.exit(1 if failed else 0)
