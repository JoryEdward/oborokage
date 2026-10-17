"""Probe the preserved Gemini artifact on the installed 26.1.2 runtime, without user worlds."""
from pathlib import Path
import json,zipfile,subprocess,shutil,hashlib
R=Path(__file__).resolve().parents[1]
MC=Path.home()/'Library/Application Support/minecraft'
I=MC/'versions/26.1.2-Fabric-DH'
J=Path.home()/'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home/bin/java'
B=R/'build/verification-compatibility-26.1.2-gemini'
B.mkdir(parents=True,exist_ok=True)
mods=B/'mods';mods.mkdir(exist_ok=True)
jar=R/'backups/gemini-baseline/oborokage-tachi-0.3.0.jar'
api=I/'mods/fabric-api-0.155.3+26.1.2.jar'
meta=json.loads((I/'26.1.2-Fabric-DH.json').read_text())
cp=[str(I/'26.1.2-Fabric-DH.jar')]
for lib in meta['libraries']:
 a=lib.get('downloads',{}).get('artifact',{}).get('path')
 if a and (MC/'libraries'/a).exists():cp.append(str(MC/'libraries'/a))
# Launcher-supplied loader libraries are required outside the launcher as well.
loader=MC/'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar'
cp.append(str(loader))
for name in ['asm','asm-analysis','asm-commons','asm-tree','asm-util']:
 cp.append(str(MC/f'libraries/org/ow2/asm/{name}/9.10.1/{name}-9.10.1.jar'))
cp.append(str(MC/'libraries/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar'))
shutil.copy2(jar,mods/jar.name);shutil.copy2(api,mods/api.name)
(B/'eula.txt').write_text('eula=true\n')
(B/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=generated-compatibility-probe\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\npause-when-empty-seconds=-1\n')
logfile=B/'launch.log'
with logfile.open('w') as log:
 p=subprocess.Popen([str(J),'-Xmx1200M','-cp',':'.join(dict.fromkeys(cp)),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=B,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
 try:p.wait(timeout=60)
 except subprocess.TimeoutExpired:
  p.stdin.write('stop\n');p.stdin.flush()
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=10)
text=logfile.read_text()
result={'requested_name':'1.26.2','actual_local_game':'26.1.2','artifact':str(jar),'artifact_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'exit_code':p.returncode,'server_started':'Done (' in text,'scope':'Isolated bootstrap only; no personal world or target-instance file modified'}
(B/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
print('\n'.join(line for line in text.splitlines() if any(s in line for s in ['Caused by:','Exception','NoClassDefFoundError','Mixin apply','ERROR','Incompatible'])))
