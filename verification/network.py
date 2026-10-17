from pathlib import Path
import subprocess,json,zipfile,shutil,socket,time,uuid,os,sys
R=Path(__file__).resolve().parents[1]; B=R/'build'; MC=Path.home()/'Library/Application Support/minecraft'; J=Path.home()/'Library/Application Support/hmcl/java/macos-arm64/mojang-java-runtime-epsilon/jre.bundle/Contents/Home/bin'
MC=Path(os.environ.get('MINECRAFT_HOME',str(MC)));J=Path(os.environ.get('JAVA_HOME',str(J.parent)))/'bin'
CP=(B/'verification-classpath.txt').read_text(); MOD=B/'libs/oborokage-tachi-0.3.0.jar'; API=MC/'versions/26.3-Fabric/mods/fabric-api-0.162.0+26.3.jar'
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
delayed='--latency' in sys.argv
BASE=B/('verification-network-latency' if delayed else 'verification-network');BASE.mkdir(exist_ok=True)
procs=[];outputs=[]
def prepare(role,source,env):
 run=BASE/role
 if run.exists():shutil.rmtree(run)
 mods=run/'mods';mods.mkdir(parents=True)
 classes=B/('net-checks-'+role);classes.mkdir(exist_ok=True)
 subprocess.run([str(J/'javac'),'-proc:none','--release','25','-cp',CP+':'+str(MOD),'-d',str(classes),str(R/'verification/src/oborokage/check'/source)]+([str(R/'verification/src/oborokage/check/RenderObservations.java')]+[str(p) for p in (R/'verification/src/oborokage/check/mixin').glob('*.java')] if env=='client' else []),check=True)
 with zipfile.ZipFile(mods/'network-checks.jar','w') as z:
  for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
  cls=source.removesuffix('.java');z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'oborokage_net_checks','version':'1','environment':env,'mixins':(['observations.mixins.json'] if env=='client' else []),'entrypoints':{'main' if env=='server' else 'client':['oborokage.check.'+cls]}}))
  if env=='client':z.writestr('observations.mixins.json',json.dumps({'required':True,'package':'oborokage.check.mixin','compatibilityLevel':'JAVA_25','client':['ObservedPose','ObservedEvent']}))
 shutil.copy2(MOD,mods/MOD.name);shutil.copy2(API,mods/API.name)
 return run
try:
 run=prepare('server','NetServerChecks.java','server')
 (run/'eula.txt').write_text('eula=true\n')
 (run/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nwhite-list=false\nenforce-whitelist=false\nenforce-secure-profile=false\nlevel-name=network-sandbox\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-players=3\npause-when-empty-seconds=-1\n')
 log=(run/'launch.log').open('w');outputs.append(log)
 server=subprocess.Popen([str(J/'java'),'-Xmx1100M','-cp',CP,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=run,stdout=log,stderr=subprocess.STDOUT);procs.append(server)
 for i in range(90):
  time.sleep(1)
  if server.poll() is not None:raise RuntimeError('Network server exited')
  if 'Done (' in (run/'launch.log').read_text():break
 else:raise RuntimeError('Network server startup timed out')
 print('Dedicated local server ready; launching two independent clients.',flush=True)
 proxy=None
 if delayed:
  from latency_proxy import DelayProxy
  proxy=DelayProxy(port,.1);connect_port=proxy.start();print('Injected100ms each way',flush=True)
 else:connect_port=port
 clients=[]
 for role,username in [('actor','BladeActor'),('observer','BladeObserver')]:
  run=prepare(role,'NetClientChecks.java','client');(run/'options.txt').write_text('lang:zh_cn\nrenderDistance:4\nsimulationDistance:3\nmaxFps:30\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\n')
  log=(run/'launch.log').open('w');outputs.append(log)
  cmd=[str(J/'java'),'-XstartOnFirstThread','-Xmx1500M','--enable-native-access=ALL-UNNAMED','-Doborokage.qa.actor='+str(role=='actor').lower(),'-cp',CP,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username',username,'--version','26.3','--gameDir',str(run),'--assetsDir',str(MC/'assets'),'--assetIndex','34','--uuid',str(uuid.uuid5(uuid.NAMESPACE_DNS,username)),'--accessToken','0','--userType','legacy','--width','960','--height','640','--quickPlayMultiplayer',f'127.0.0.1:{connect_port}']
  p=subprocess.Popen(cmd,cwd=run,stdout=log,stderr=subprocess.STDOUT);procs.append(p);clients.append(p)
 deadline=time.monotonic()+120
 while any(p.poll() is None for p in clients) and time.monotonic()<deadline:time.sleep(1)
 if any(p.poll() is None for p in clients):raise RuntimeError('Network clients timed out')
 for role in ['server','actor','observer']:
  result=BASE/role/('network-server-results.txt' if role=='server' else 'network-client-results.txt')
  print(role,result.read_text() if result.exists() else 'RESULT MISSING',flush=True)
finally:
 for p in reversed(procs):
  if p.poll() is None:p.terminate()
 for p in procs:
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:p.kill()
 for f in outputs:f.close()
 if 'proxy' in locals() and proxy:proxy.stop()
