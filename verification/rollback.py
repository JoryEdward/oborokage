"""Test only a generated sandbox. Never downgrade a personal world."""
from pathlib import Path
import os,json,zipfile,subprocess,shutil
R=Path(__file__).resolve().parents[1];B=R/'build';cp=(B/'verification-classpath.txt').read_text();mc=Path(os.environ.get('MINECRAFT_HOME',str(Path.home()/'Library/Application Support/minecraft')));j=Path(os.environ.get('JAVA_HOME',str(Path.home()/'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home')))/'bin'
legacy=Path(os.environ.get('OBOROKAGE_LEGACY_JAR',str(R.parent/'Oborokage-0.2.0/backups/release-20261016/oborokage-tachi-0.1.0.jar')))
run=B/'verification-rollback';mods=run/'mods';mods.mkdir(parents=True,exist_ok=True);classes=B/'rollback-checks';classes.mkdir(exist_ok=True)
subprocess.run([str(j/'javac'),'-proc:none','--release','25','-cp',cp,'-d',str(classes),str(R/'verification/src/oborokage/check/RollbackChecks.java')],check=True)
with zipfile.ZipFile(mods/'rollback-checks.jar','w') as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'oborokage_rollback_checks','version':'1','environment':'server','entrypoints':{'main':['oborokage.check.RollbackChecks']}}))
shutil.copy2(mc/'versions/26.3-Fabric/mods/fabric-api-0.162.0+26.3.jar',mods/'fabric-api.jar')
(run/'eula.txt').write_text('eula=true\n');(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=rollback-sandbox\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\n')
for phase,jar in [('seed',B/'libs/oborokage-tachi-0.3.1.jar'),('old',legacy),('new',B/'libs/oborokage-tachi-0.3.1.jar')]:
 shutil.copy2(jar,mods/'oborokage.jar')
 with (run/(phase+'.log')).open('w') as log:subprocess.run([str(j/'java'),'-Xmx1200M','-Doborokage.qa.rollback='+phase,'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=run,stdout=log,stderr=subprocess.STDOUT,timeout=90,check=True)
 print(phase,'done',flush=True)
result=(run/'rollback-results.txt').read_text();print(result);assert 'FAIL' not in result
