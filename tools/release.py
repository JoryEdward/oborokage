#!/usr/bin/env python3
"""Oborokage release workflow, one step per subcommand (run from anywhere):

  begin <version>  bump mod_version; move the previous release's files to backups/release-<old>/;
                   start verification/release-<version>/
  build            build the three Minecraft lines into dist/ (never `gradlew clean`: it would
                   delete build/mc-home, the downloaded test instances)
  verify [options] run the checks on the JARs in dist/ and summarise them in build/verify.json
                   (and, with --doc, in VERIFICATION.md between <!-- verify:start/end --> markers)
  package          tools/package_release.py, then refresh the standalone 26.2 package beside the
                   project
  install          put the release JARs into the two launcher instances (the old ones go to
                   backups/install-<time>/)
  finish           tidy build outputs (keeping build/mc-home) and rename the project folder and
                   the 26.2 package to the version

Subprocesses always get argument lists, never a shell, so paths with spaces stay whole.

verify options:
  --games=26.1,26.3      only these Minecraft versions (default: all five)
  --no-matrix            skip the full server suite and the client smoke run
  --no-focused           skip the focused server checks
  --no-client            skip the special client checks (bug check, language check)
  --focused=a,b          focused checks to run (default: FOCUSED below)
  --doc                  also write the summary table into VERIFICATION.md
"""
from pathlib import Path
import datetime, hashlib, json, os, re, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
JAVA_HOME = (Path.home() / 'Library/Application Support/hmcl/java/macos-arm64'
             / 'mojang-java-runtime-epsilon/jre.bundle/Contents/Home')
MC = Path.home() / 'Library/Application Support/minecraft'
LINES = ['26.1', '26.2', '26.3']
# game version -> compile line
GAMES = {'26.1': '26.1', '26.1.1': '26.1', '26.1.2': '26.1', '26.2': '26.2', '26.3': '26.3'}
# Fabric API for the isolated instances under build/mc-home (as verification/matrix.py uses)
API = {'26.1': '0.145.1+26.1', '26.1.1': '0.145.4+26.1.1', '26.2': '0.161.0+26.2'}
REAL = {'26.1.2': MC / 'versions/26.1.2-Fabric-DH', '26.3': MC / 'versions/26.3-Fabric'}
INSTALL = {'26.3-Fabric': '26.3', '26.1.2-Fabric-DH': '26.1'}
# Every focused check that runs on the flat test world. Not perf (informational, slow) and not
# survey (needs a normal world).
FOCUSED = 'tomb,lord,candles,ai,lang,bugs,density,withered,structures,parade,grind,commands,lord2'
# Special client checks and where they run; "+fullpack" adds the player's own mods.
CLIENT = {'bugcheck': ['26.2', '26.1.2', '26.3'], 'langcheck': ['26.3+fullpack', '26.1.2'],
          'settingscheck': ['26.3', '26.1.2'], 'compatcheck': ['26.3+fullpack']}


def version():
    return re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M)[1]


def say(*parts):
    print(*parts, flush=True)


def game_running():
    return subprocess.run(['pgrep', '-f', 'net.minecraft.client.main.Main|KnotClient'],
                          capture_output=True).returncode == 0


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def gradle_env():
    env = dict(os.environ)
    env['JAVA_HOME'] = str(JAVA_HOME)
    return env


# ------------------------------------------------------------------------------------- begin

def begin(new):
    old = version()
    if new == old:
        sys.exit(f'already at {new}')
    props = ROOT / 'gradle.properties'
    props.write_text(re.sub(r'^mod_version=.+$', f'mod_version={new}', props.read_text(), flags=re.M))
    release = ROOT / 'release'
    moved = 0
    if release.exists() and any(release.iterdir()):
        dest = ROOT / 'backups' / f'release-{old}'
        dest.mkdir(parents=True, exist_ok=True)
        for f in release.iterdir():
            shutil.move(str(f), dest / f.name)
            moved += 1
    (ROOT / 'verification' / f'release-{new}').mkdir(parents=True, exist_ok=True)
    say(f'{old} -> {new}; moved {moved} release files to backups/release-{old}/;',
        f'started verification/release-{new}/')


# ------------------------------------------------------------------------------------- build

def build():
    ver = version()
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    for f in dist.glob('oborokage-tachi-*.jar'):
        if not f.name.startswith(f'oborokage-tachi-{ver}+'):
            f.unlink()
    for line in LINES:
        say(f'build {line}')
        subprocess.run(['./gradlew', 'build', f'-Pmc_target={line}', '-q'], cwd=ROOT,
                       env=gradle_env(), check=True)
        for suffix in ['', '-sources']:
            jar = ROOT / 'build/libs' / f'oborokage-tachi-{ver}+{line}{suffix}.jar'
            shutil.copy2(jar, dist / jar.name)
    for line in LINES:
        jar = dist / f'oborokage-tachi-{ver}+{line}.jar'
        say(f'  {jar.name}  {sha256(jar)[:16]}')


# ------------------------------------------------------------------------------------ verify

def run_env(game):
    env = gradle_env()
    env['MINECRAFT_VERSION'] = game
    env['OBOROKAGE_JAR'] = str(ROOT / 'dist' / f'oborokage-tachi-{version()}+{GAMES[game]}.jar')
    if game in REAL:
        env.pop('MINECRAFT_HOME', None)
        env['MINECRAFT_INSTANCE'] = str(REAL[game])
    else:
        home = ROOT / 'build/mc-home'
        instance = home / 'versions' / f'{game}-Fabric'
        if not instance.exists():
            subprocess.run([sys.executable, str(ROOT / 'verification/make_instance.py'), game,
                            API[game]], cwd=ROOT, check=True)
        env['MINECRAFT_HOME'] = str(home)
        env['MINECRAFT_INSTANCE'] = str(instance)
    return env


def run(game, label, args):
    """run.py with these arguments; its output goes to build/verify-logs/<game>-<label>.log."""
    logs = ROOT / 'build/verify-logs'
    logs.mkdir(parents=True, exist_ok=True)
    log = logs / f'{game}-{label}.log'
    with log.open('w') as out:
        code = subprocess.run([sys.executable, str(ROOT / 'verification/run.py')] + args,
                              cwd=ROOT, env=run_env(game), stdout=out,
                              stderr=subprocess.STDOUT).returncode
    return code, log.read_text(errors='replace')


def server_counts(game):
    log = ROOT / 'build' / f'verification-{game}-server/logs/latest.log'
    text = log.read_text(errors='replace') if log.exists() else ''
    fails = [l.split('AssertionError:', 1)[1].strip()[:200] for l in text.splitlines()
             if 'AssertionError:' in l]
    return text.count('CHECK PASS'), fails


def client_counts(game, fullpack):
    results = (ROOT / 'build' / f"verification-{game}-client{'-fullpack' if fullpack else ''}-smoke"
               / 'client-results.txt')
    text = results.read_text(errors='replace') if results.exists() else ''
    lines = text.splitlines()
    return (sum(l.startswith('PASS') for l in lines),
            [l[5:200] for l in lines if l.startswith('FAIL')])


def verify(argv):
    opts = {a.split('=', 1)[0]: (a.split('=', 1)[1] if '=' in a else True) for a in argv}
    games = opts.get('--games', ','.join(GAMES)).split(',')
    focused = opts.get('--focused', FOCUSED)
    if game_running():
        sys.exit('Minecraft is running: close it first')
    ver = version()
    summary = {'version': ver,
               'jars': {line: sha256(ROOT / 'dist' / f'oborokage-tachi-{ver}+{line}.jar')
                        for line in LINES},
               'results': {}}
    for game in games:
        r = summary['results'].setdefault(game, {})
        if '--no-matrix' not in opts:
            code, out = run(game, 'server', ['server'])
            m = re.search(r'Verified result records: (\d+)', out)
            r['server'] = {'exit': code, 'pass': int(m[1]) if m else 0}
            say(game, 'server suite', r['server'])
            code, out = run(game, 'smoke', ['client', '--smoke'])
            m = re.search(r'Verified result records: (\d+)', out)
            r['smoke'] = {'exit': code, 'pass': int(m[1]) if m else 0}
            say(game, 'client smoke', r['smoke'])
        if '--no-focused' not in opts and focused:
            code, _ = run(game, 'focused', ['server', f'--only={focused}'])
            passed, fails = server_counts(game)
            r['focused'] = {'exit': code, 'pass': passed, 'fail': fails, 'checks': focused}
            say(game, 'focused', {k: v for k, v in r['focused'].items() if k != 'checks'})
        if '--no-client' not in opts:
            for check, where in CLIENT.items():
                for spot in where:
                    target, _, extra = spot.partition('+')
                    if target != game:
                        continue
                    args = ['client', f'--{check}'] + (['--fullpack'] if extra == 'fullpack' else [])
                    code, _ = run(game, check, args)
                    passed, fails = client_counts(game, extra == 'fullpack')
                    r[check] = {'exit': code, 'pass': passed, 'fail': fails}
                    say(game, check, r[check])
    out = ROOT / 'build/verify.json'
    out.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n')
    table = summary_table(summary)
    say(table)
    if '--doc' in opts:
        doc = ROOT / 'VERIFICATION.md'
        text = doc.read_text()
        start, end = '<!-- verify:start -->', '<!-- verify:end -->'
        if start in text and end in text:
            head, rest = text.split(start, 1)
            _, tail = rest.split(end, 1)
            doc.write_text(head + start + '\n' + table + '\n' + end + tail)
            say('VERIFICATION.md updated')
        else:
            say('VERIFICATION.md has no verify markers; table not written')
    bad = [(g, k) for g, r in summary['results'].items() for k, v in r.items()
           if v.get('exit') or v.get('fail')]
    if bad:
        say('FAILED:', bad)
        sys.exit(1)


def summary_table(summary):
    kinds = [('server', '服务端回归'), ('smoke', '客户端冒烟'), ('focused', '专项检查'),
             ('bugcheck', '客户端问题检查'), ('langcheck', '客户端语言检查'),
             ('settingscheck', '客户端设置检查'), ('compatcheck', 'JEI 与 Jade')]
    present = [k for k in kinds if any(k[0] in r for r in summary['results'].values())]
    rows = ['| Minecraft | ' + ' | '.join(name for _, name in present) + ' |',
            '|---|' + '---:|' * len(present)]
    for game, r in summary['results'].items():
        cells = []
        for key, _ in present:
            v = r.get(key)
            if not v:
                cells.append('—')
                continue
            failed = len(v.get('fail', [])) if 'fail' in v else (1 if v['exit'] else 0)
            ok = v['exit'] == 0 and failed == 0
            cells.append(f"{v['pass']} / {v['pass'] + failed}" + ('' if ok else ' ✗'))
        rows.append(f'| {game} | ' + ' | '.join(cells) + ' |')
    return '\n'.join(rows)


# ----------------------------------------------------------------------------------- package

def package():
    ver = version()
    subprocess.run([sys.executable, str(ROOT / 'tools/package_release.py')], cwd=ROOT, check=True)
    jar = ROOT / 'release' / f'oborokage-tachi-{ver}+26.2.jar'
    packs = sorted(ROOT.parent.glob('Oborokage-*-MC26.2'))
    if len(packs) != 1:
        sys.exit(f'expected one 26.2 package beside the project, found {[p.name for p in packs]}')
    pack = packs[0]
    target = ROOT.parent / f'Oborokage-{ver}-MC26.2'
    if pack != target:
        pack.rename(target)
        pack = target
    kept = {sha256(f) for f in (ROOT / 'backups').rglob('oborokage-tachi-*+26.2.jar')}
    for old in pack.glob('oborokage-tachi-*.jar'):
        if old.name == jar.name:
            continue
        if sha256(old) not in kept:
            sys.exit(f'{old.name} is not kept anywhere in backups/: not removing it')
        old.unlink()
    shutil.copy2(jar, pack / jar.name)
    digest = sha256(pack / jar.name)
    (pack / 'SHA256SUMS.txt').write_text(f'{digest}  {jar.name}\n')
    notes = pack / '说明.md'
    text = notes.read_text()
    text = re.sub(r'^# 胧影剑录 Oborokage \S+ · Minecraft 26.2 专用包',
                  f'# 胧影剑录 Oborokage {ver} · Minecraft 26.2 专用包', text, flags=re.M)
    text = re.sub(r'oborokage-tachi-[0-9][^+`\s]*\+26\.2\.jar', jar.name, text)
    text = re.sub(r'SHA-256：`[0-9a-f]{64}`', f'SHA-256：`{digest}`', text)
    notes.write_text(text)
    # A medium update (x.Y.0) has a player's introduction: it goes with the release and the pack.
    intro = ROOT / 'docs' / f'INTRO_{ver}.md'
    for old in pack.glob('INTRO_*.md'):
        old.unlink()
    if intro.exists():
        shutil.copy2(intro, ROOT / 'release' / intro.name)
        shutil.copy2(intro, pack / intro.name)
        say(f'introduction {intro.name} copied to release/ and the 26.2 package')
    say(f'26.2 package: {pack.name} ({digest[:16]}); edit the narrative parts of 说明.md by hand')


# ----------------------------------------------------------------------------------- install

def install():
    if game_running():
        sys.exit('Minecraft is running: close it first')
    ver = version()
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = ROOT / 'backups' / f'install-{stamp}'
    for instance, line in INSTALL.items():
        mods = MC / 'versions' / instance / 'mods'
        dest = backup / instance
        dest.mkdir(parents=True, exist_ok=True)
        for old in mods.glob('oborokage-tachi-*.jar'):
            shutil.move(str(old), dest / old.name)
        new = ROOT / 'release' / f'oborokage-tachi-{ver}+{line}.jar'
        shutil.copy2(new, mods / new.name)
        present = sorted(p.name for p in mods.glob('oborokage-tachi-*.jar'))
        assert present == [new.name], present
        assert sha256(mods / new.name) == sha256(new)
        say(f'{instance}: {new.name} {sha256(new)[:16]}')
    say(f'previous JARs in {backup.relative_to(ROOT)}')


# ------------------------------------------------------------------------------------ finish

def finish():
    if game_running():
        sys.exit('Minecraft is running: close it first')
    ver = version()
    subprocess.run(['./gradlew', '--stop', '-q'], cwd=ROOT, env=gradle_env())
    # Nothing is deleted here: build/ (test worlds, run folders, build/mc-home) can run to over a
    # gigabyte, and clearing it is the owner's call (ask first).
    size = sum(f.stat().st_size for f in (ROOT / 'build').rglob('*') if f.is_file()) \
        if (ROOT / 'build').exists() else 0
    say(f'build/ kept ({size / 2**30:.1f} GB); clear it only when the owner agrees')
    target = ROOT.parent / f'Oborokage-{ver}'
    if ROOT != target:
        if target.exists():
            sys.exit(f'{target} already exists')
        ROOT.rename(target)
    say(f'project folder: {target}')
    packs = sorted(target.parent.glob('Oborokage-*-MC26.2'))
    say('26.2 package:', ', '.join(p.name for p in packs))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    step, rest = sys.argv[1], sys.argv[2:]
    if step == 'begin':
        begin(rest[0])
    elif step == 'build':
        build()
    elif step == 'verify':
        verify(rest)
    elif step == 'package':
        package()
    elif step == 'install':
        install()
    elif step == 'finish':
        finish()
    else:
        sys.exit(__doc__)
