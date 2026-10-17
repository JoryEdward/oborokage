from pathlib import Path
import os,json,subprocess,zipfile,shutil
R=Path(__file__).resolve().parents[1];B=R/'build';MC=Path(os.environ.get('MINECRAFT_HOME',str(Path.home()/'Library/Application Support/minecraft')));J=Path(os.environ.get('JAVA_HOME',str(Path.home()/'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home')))/'bin'
module=next((Path.home()/'.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-gametest-api-v1').rglob('*.jar'))
cp=(B/'verification-classpath.txt').read_text()+':'+str(module);jar=B/'libs/oborokage-tachi-0.3.1.jar';run=B/'verification-gametest';mods=run/'mods';mods.mkdir(parents=True,exist_ok=True);classes=B/'gametest-classes';classes.mkdir(exist_ok=True)
subprocess.run([str(J/'javac'),'-proc:none','--release','25','-cp',cp+':'+str(jar),'-d',str(classes),str(R/'verification/src/oborokage/check/NativeGameTests.java')],check=True)
with zipfile.ZipFile(mods/'oborokage-native-tests.jar','w') as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'oborokage_native_tests','version':'1','environment':'server','entrypoints':{'fabric-gametest':['oborokage.check.NativeGameTests']}}))
for src,name in [(jar,'oborokage.jar'),(module,'fabric-gametest-api.jar'),(MC/'versions/26.3-Fabric/mods/fabric-api-0.162.0+26.3.jar','fabric-api.jar')]:shutil.copy2(src,mods/name)
(run/'eula.txt').write_text('eula=true\n')
with (run/'launch.log').open('w') as log:result=subprocess.run([str(J/'java'),'-Xmx1300M','-Dfabric-api.gametest=true','-Dfabric-api.gametest.report-file=reports/results.xml','-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=run,stdout=log,stderr=subprocess.STDOUT,timeout=120)
print('GameTest exit',result.returncode);print((run/'reports/results.xml').read_text() if (run/'reports/results.xml').exists() else 'RESULT MISSING');assert result.returncode==0 and (run/'reports/results.xml').exists()
