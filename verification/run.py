from pathlib import Path
import json,zipfile,subprocess,shutil,sys,os
ROOT=Path(__file__).resolve().parents[1];MC=Path.home()/'Library/Application Support/minecraft';INSTANCE=MC/'versions/26.3-Fabric';JAVA=Path.home()/'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home/bin'
MC=Path(os.environ.get('MINECRAFT_HOME',str(MC)));INSTANCE=Path(os.environ.get('MINECRAFT_INSTANCE',str(INSTANCE)));JAVA=Path(os.environ.get('JAVA_HOME',str(JAVA.parent)))/'bin'
mode=sys.argv[1] if len(sys.argv)>1 else 'server'
shader='--shader' in sys.argv
lowfx='--lowfx' in sys.argv
fullpack='--fullpack' in sys.argv or shader
baseline='--baseline' in sys.argv
maintenance='--maintenance' in sys.argv
client_class='BaselineAuditChecks' if baseline else 'MaintenanceClientChecks' if maintenance else 'FocusedClientChecks' if '--focused' in sys.argv else 'ClientChecks'
b=ROOT/'build';deps=b/'verification-deps';deps.mkdir(exist_ok=True)
cp=[str(INSTANCE/'26.3-Fabric.jar')]
meta=json.loads((INSTANCE/'26.3-Fabric.json').read_text())
for lib in meta['libraries']:
 a=lib.get('downloads',{}).get('artifact',{}).get('path');p=MC/'libraries'/a if a else None
 if p and p.exists():cp.append(str(p))
cp.append(str(MC/'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar'))
for name in ['asm','asm-analysis','asm-commons','asm-tree','asm-util']:cp.append(str(MC/f'libraries/org/ow2/asm/{name}/9.10.1/{name}-9.10.1.jar'))
cp.append(str(MC/'libraries/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar'))
api=INSTANCE/'mods/fabric-api-0.162.0+26.3.jar'
with zipfile.ZipFile(api) as z:
 for name in z.namelist():
  if name.endswith('.jar'):
   p=deps/Path(name).name
   if not p.exists():p.write_bytes(z.read(name))
   cp.append(str(p))
cp=':'.join(dict.fromkeys(cp));(b/'verification-classpath.txt').write_text(cp)
jar=ROOT/('backups/gemini-baseline/oborokage-tachi-0.3.0.jar' if baseline else 'build/libs/oborokage-tachi-0.3.1.jar');run=b/('verification-expansion-'+mode+('-fullpack' if fullpack else '')+('-shader' if shader else '')+('-lowfx' if lowfx else '')+('-baseline' if baseline else '')+('-maintenance' if maintenance else ''));run.mkdir(exist_ok=True);mods=run/'mods';mods.mkdir(exist_ok=True)
(run/(mode+'-results.txt')).unlink(missing_ok=True)
classes=b/('checks-'+mode);shutil.rmtree(classes,ignore_errors=True);classes.mkdir(exist_ok=True)
source=ROOT/'verification/src/oborokage/check'/('ServerChecks.java' if mode=='server' else client_class+'.java')
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','25','-cp',cp+':'+str(jar),'-d',str(classes),str(source)]+([str(source.parent/'ReviewChecks.java')] if mode=='server' else [])+([str(source.parent/'ClientChecks.java'),str(source.parent/'FocusedChecks.java'),str(source.parent/'RenderObservations.java')]+[str(p) for p in (source.parent/'mixin').glob('*.java') if maintenance or p.stem in ['ObservedPose','ObservedEvent','ObservedCut','ObservedTerrain']] + ([str(source.parent/'MaintenanceObservations.java')] if maintenance else []) if mode=='client' else []),check=True)
with zipfile.ZipFile(mods/'oborokage-checks.jar','w') as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'oborokage_checks','version':'1','environment':mode,'mixins':(['observations.mixins.json'] if mode=='client' else []),'entrypoints':{'main' if mode=='server' else 'client':['oborokage.check.'+('ServerChecks' if mode=='server' else client_class)]}}))
 if mode=='server':z.writestr('data/oborokage/tags/entity_type/finisher_exempt.json',json.dumps({'replace':False,'values':['minecraft:armor_stand']}))
 if mode=='client':z.writestr('observations.mixins.json',json.dumps({'required':True,'package':'oborokage.check.mixin','compatibilityLevel':'JAVA_25','client':['ObservedPose','ObservedEvent']+(['ObservedMaintenance','ObservedCharge','ObservedHud','ObservedChat','ObservedSinglePose'] if maintenance else []),'mixins':['ObservedCut','ObservedTerrain']+(['ObservedSound','ObservedBroadcast'] if maintenance else [])}))
if fullpack:
 for p in (INSTANCE/'mods').glob('*.jar'):
  if not p.name.startswith('oborokage-tachi-'):shutil.copy2(p,mods/p.name)
shutil.copy2(jar,mods/jar.name);shutil.copy2(api,mods/api.name)
if mode=='server':
 (run/'eula.txt').write_text('eula=true\n');(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=temporary-tachi-world\ngamemode=creative\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=1\nlevel-type=minecraft:flat\ngenerate-structures=false\npause-when-empty-seconds=-1\n')
 cmd=[str(JAVA/'java'),'-Xmx1536M','-Djava.awt.headless=true','-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
else:
 world=run/'saves/temporary-tachi-world'
 if not world.exists():shutil.copytree(b/'verification-expansion-server/temporary-tachi-world',world)
 old_progress=ROOT/'build/verification-client-fullpack/saves/temporary-tachi-world/players/advancements/10eae1ce-0d44-443e-9842-ea6420126711.json'
 if old_progress.exists():
  old=json.loads(old_progress.read_text());assert old.get('oborokage:moon_still_hangs',{}).get('done')
  dest=world/'players/advancements'/old_progress.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(old_progress,dest)
 if shader:
  packs=run/'shaderpacks';packs.mkdir(exist_ok=True);shutil.copy2(INSTANCE/'shaderpacks/ComplementaryReimagined_r5.9.3.zip',packs/'ComplementaryReimagined_r5.9.3.zip');config=run/'config';config.mkdir(exist_ok=True);(config/'iris.properties').write_text('enableShaders=true\nshaderPack=ComplementaryReimagined_r5.9.3.zip\nmaxShadowRenderDistance=8\n')
 if lowfx:
  config=run/'config';config.mkdir(exist_ok=True);(config/'oborokage-client.properties').write_text('trailSegments=64\ntrailVertexBudget=512\ntrailMaxPlayers=1\ntrailDistance=16\n')
 (run/'options.txt').write_text('lang:zh_cn\nguiScale:2\nrenderDistance:6\nsimulationDistance:5\nmaxFps:60\nenableVsync:true\npauseOnLostFocus:false\nshowAutosaveIndicator:false\n')
 cmd=[str(JAVA/'java'),'-Doborokage.qa.start='+next((a.split('=',1)[1] for a in sys.argv if a.startswith('--from=')),'0'),'-XstartOnFirstThread','-Xmx2200M','--enable-native-access=ALL-UNNAMED','--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED','-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','TachiArtist','--version','26.3','--gameDir',str(run),'--assetsDir',str(MC/'assets'),'--assetIndex','34','--uuid','10eae1ce-0d44-443e-9842-ea6420126711','--accessToken','0','--userType','legacy','--width','1280','--height','800','--quickPlaySingleplayer','temporary-tachi-world']
print('Isolated run:',run,flush=True)
result=subprocess.run(cmd,cwd=run)
result_file=run/(mode+'-results.txt')
assert result.returncode == 0, f'Game process exited {result.returncode}'
assert result_file.exists(), f'Results missing: {result_file}'
assert 'FAIL ' not in result_file.read_text(), result_file.read_text()
print('Verified result records:', result_file.read_text().count('PASS '), flush=True)
