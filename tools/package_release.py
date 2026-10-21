"""Create a clean source archive with explicit AppleDouble/cache/internal-log exclusions."""
from pathlib import Path
import os, subprocess, tarfile, hashlib, json, zipfile
ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / 'release'
RELEASE.mkdir(exist_ok=True)
version = next(line.split('=', 1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
archive = RELEASE / f'oborokage-{version}-source.tar.gz'
include = ['src', 'versions', 'tools', 'gradle', 'gradlew', 'gradlew.bat', 'build.gradle', 'settings.gradle', 'gradle.properties', 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'README.md', 'VERIFICATION.md', 'CHANGELOG.md', 'docs', 'config-examples', 'verification']
include = [name for name in include if (ROOT / name).exists()]
evidence = RELEASE / f'oborokage-{version}-evidence.zip'
with zipfile.ZipFile(evidence, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for p in sorted((ROOT/f'verification/release-{version}').rglob('*')):
        if p.is_file() and not any(part.startswith('._') or part in ['__pycache__', 'WORK_LOG.md', '.DS_Store'] for part in p.parts):
            z.write(p, p.relative_to(ROOT))
exclude = [f'*/verification/release-{version}/screenshots', f'*/verification/release-{version}/screenshots/*', f'*/verification/release-{version}/sheets', f'*/verification/release-{version}/sheets/*', '*/verification/release-0.*', '._*', '*/._*', '__pycache__', '*/__pycache__', '*/__pycache__/*', '__MACOSX', '*/__MACOSX/*', 'WORK_LOG.md', '*/WORK_LOG.md', '.DS_Store', '*/.DS_Store', '*/.pytest_cache/*', '*/.git/*', '*/.gradle/*', '*/build/*', '*/backups/*', '*/release/*']
# The archive's top folder is oborokage-<version>, whatever the project folder is called.
PREFIX = f'oborokage-{version}'
cmd = ['tar'] + [f'--exclude={pattern}' for pattern in exclude] + ['-s', f',^{ROOT.name}/,{PREFIX}/,', '-czf', str(archive), '-C', str(ROOT.parent)] + [ROOT.name + '/' + name for name in include]
env = os.environ.copy()
env['COPYFILE_DISABLE'] = '1'
subprocess.run(cmd, env=env, check=True)
with tarfile.open(archive) as tar:
    members = tar.getmembers()
    bad = [m.name for m in members if any(p.startswith('._') or p in ['__pycache__','__MACOSX','WORK_LOG.md','.DS_Store'] for p in Path(m.name).parts)]
    assert not bad, bad
    production_files = [m for m in members if m.isfile() and m.name.startswith(PREFIX + '/src/')]
    for m in production_files:
        source = ROOT / Path(m.name).relative_to(PREFIX)
        assert tar.extractfile(m).read() == source.read_bytes(), m.name
# One JAR (and sources JAR) per Minecraft line, as produced by verification/matrix.py into dist/.
jars = sorted(p for p in (ROOT / 'dist').glob(f'oborokage-tachi-{version}+*.jar'))
assert len([p for p in jars if not p.name.endswith('-sources.jar')]) == 3, jars
for jar in jars:
    (RELEASE / jar.name).write_bytes(jar.read_bytes())
names = [p.name for p in jars] + [archive.name, evidence.name]
checks = {name: hashlib.sha256((RELEASE/name).read_bytes()).hexdigest() for name in names}
(RELEASE/'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name, digest in checks.items()))
report = {'archive_members':len(members), 'verified_production_files':len(production_files), 'appledouble':0, 'python_cache':0, 'work_log':0, 'screenshots_separate_evidence_zip':True, 'copyfile_disable':'1', 'artifacts':checks}
(RELEASE/'packaging-check.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report, indent=2))
