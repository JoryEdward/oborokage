"""Read-only target-instance inspection; all launch writes go to the project build sandbox."""
from pathlib import Path
import json,zipfile,subprocess,shutil,hashlib
R=Path(__file__).resolve().parents[1];MC=Path.home()/'Library/Application Support/minecraft';I=MC/'versions/26.1.2-Fabric-DH';J=Path.home()/'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home/bin'
B=R/'build/verification-compatibility-client-26.1.2-gemini';B.mkdir(exist_ok=True)
mods=B/'mods';mods.mkdir(exist_ok=True);deps=B/'deps';deps.mkdir(exist_ok=True)
meta=json.loads((I/'26.1.2-Fabric-DH.json').read_text());cp=[str(I/'26.1.2-Fabric-DH.jar')]
for l in meta['libraries']:
 a=l.get('downloads',{}).get('artifact',{}).get('path')
 if a and (MC/'libraries'/a).exists():cp.append(str(MC/'libraries'/a))
cp.append(str(MC/'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar'))
for n in ['asm','asm-analysis','asm-commons','asm-tree','asm-util']:cp.append(str(MC/f'libraries/org/ow2/asm/{n}/9.10.1/{n}-9.10.1.jar'))
cp.append(str(MC/'libraries/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar'))
api=I/'mods/fabric-api-0.155.3+26.1.2.jar'
with zipfile.ZipFile(api) as z:
 for n in z.namelist():
  if n.endswith('.jar'):
   p=deps/Path(n).name;p.write_bytes(z.read(n));cp.append(str(p))
cp=':'.join(dict.fromkeys(cp));classes=B/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(J/'javac'),'-proc:none','--release','25','-cp',cp,'-d',str(classes),str(R/'verification/src/oborokage/check/CompatClientProbe.java')],check=True)
with zipfile.ZipFile(mods/'compat-client-probe.jar','w') as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'oborokage_compat_probe','version':'1','environment':'client','entrypoints':{'client':['oborokage.check.CompatClientProbe']}}))
jar=R/'backups/gemini-baseline/oborokage-tachi-0.3.0.jar';shutil.copy2(jar,mods/jar.name);shutil.copy2(api,mods/api.name)
world=B/'saves/generated-compatibility-probe';world.parent.mkdir(exist_ok=True)
if not world.exists():shutil.copytree(R/'build/verification-compatibility-26.1.2-gemini/generated-compatibility-probe',world)
(B/'options.txt').write_text('lang:zh_cn\nguiScale:2\nrenderDistance:4\nsimulationDistance:3\nmaxFps:60\npauseOnLostFocus:false\n')
cmd=[str(J/'java'),'-XstartOnFirstThread','-Xmx1600M','--enable-native-access=ALL-UNNAMED','-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','CompatProbe','--version','26.1.2','--gameDir',str(B),'--assetsDir',str(MC/'assets'),'--assetIndex',str(meta['assetIndex']['id']),'--uuid','12ee712f-dbea-4b30-a690-a4aadf3e42a4','--accessToken','0','--userType','legacy','--width','1280','--height','800','--quickPlaySingleplayer','generated-compatibility-probe']
with (B/'launch.log').open('w') as log:
 try:run=subprocess.run(cmd,cwd=B,stdout=log,stderr=subprocess.STDOUT,timeout=100);code=run.returncode
 except subprocess.TimeoutExpired:code='timeout'
text=(B/'launch.log').read_text();result={'game':'26.1.2','artifact_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'exit_code':code,'client_world_loaded':(B/'client-probe-result.txt').exists(),'scope':'Minecraft26.1.2 + FabricAPI0.155.3, isolated temporary world, no DH/fullpack or combat assertion'}
(B/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
print('\n'.join(l for l in text.splitlines() if any(s in l for s in ['Caused by:','NoSuch','NoClassDef','Mixin apply','ERROR'])))
