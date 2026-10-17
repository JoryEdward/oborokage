"""Original parametric voxel sculpture and procedural optical textures. No downloaded art."""
import math,json,struct,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/oborokage'
def png(path,w,h,pixels):
 path=ROOT/path;path.parent.mkdir(parents=True,exist_ok=True)
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 raw=b''.join(b'\0'+bytes(pixels[y*w*4:(y+1)*w*4]) for y in range(h))
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def js(path,data):
 path=ROOT/path;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
colors={'ink':(20,18,31),'black':(10,10,18),'wrap':(66,47,89),'silk':(139,103,176),'gold':(190,163,101),'silver':(168,182,207),'edge':(233,244,255),'spine':(65,78,106),'light':(186,153,255)}
for name,col in colors.items():
 pix=[]
 for y in range(16):
  for x in range(16):
   d=((x*3+y*7)%5)-2;pix.extend([max(0,min(255,c+d)) for c in col]+[255])
 png(Path('textures/item')/(name+'.png'),16,16,pix)
textures={name:'oborokage:item/'+name for name in colors};textures['particle']='oborokage:item/silver'
def box(x1,y1,z1,x2,y2,z2,tex,rot=None,light=0):
 d={'from':[round(x1,3),round(y1,3),round(z1,3)],'to':[round(x2,3),round(y2,3),round(z2,3)],'faces':{face:{'texture':'#'+tex,'uv':[0,0,16,16]} for face in ['north','south','east','west','up','down']}}
 if rot:d['rotation']={'origin':rot[0],'axis':rot[1],'angle':rot[2]}
 if light:d['light_emission']=light
 return d
# Silvery blue folded steel; UVs flow continuously from habaki to kissaki.
pix=[]
for y in range(256):
 for x in range(64):
  u=x/63;wave=.22+.025*math.sin(y*.16)+.012*math.sin(y*.47)
  shine=math.exp(-((u-.56)/.14)**2);hamon=math.exp(-((u-wave)/.025)**2)
  if u<.16:c=(226,239,250)
  else:c=(int(109+75*shine+48*hamon),int(124+74*shine+42*hamon),int(149+74*shine+33*hamon))
  grain=((x*5+y*13)%7-3)*.5;pix.extend([min(255,max(0,int(v+grain))) for v in c]+[255])
png(Path('textures/item/steel.png'),64,256,pix);textures['steel']='oborokage:item/steel'
# Long tsuka with diagonal silk crossings, pale samegawa diamonds, brass menuki.
hilt=[box(7.42,-10.4,7.5,8.58,.6,8.5,'ink'),box(7.3,-10.5,7.35,8.7,-10.0,8.65,'gold'),box(7.45,-10.65,7.45,8.55,-10.45,8.55,'black')]
for i in range(9):
 y=-9.5+i*1.06
 for z in (7.39,8.51):
  hilt.append(box(7.56,y-.23,z,8.44,y+.23,z+.1,'silk',([8,y,z],'z',45)))
  for a in [-37,37]:hilt.append(box(7.2,y-.09,z-.025,8.8,y+.09,z+.13,'wrap',([8,y,z],'z',a)))
for y in [-7.1,-3.9]:
 for z in [7.28,8.63]:hilt.append(box(7.87,y,z,8.13,y+.24,z+.08,'gold'))
# Octagonal open guard: dark interior, thin warm-gold outer rim.
for i in range(8):
 a=i*math.pi/4;cx=8+math.cos(a)*1.78;cz=8+math.sin(a)*1.45
 hilt.append(box(cx-.76,.1,cz-.2,cx+.76,.43,cz+.2,'gold',([cx,.1,cz],'y',-i*45-90)))
 hilt.append(box(cx-.62,.44,cz-.14,cx+.62,.5,cz+.14,'ink',([cx,.1,cz],'y',-i*45-90)))
hilt += [box(7.26,.45,7.65,8.74,1.65,8.35,'gold'),box(7.3,1.45,7.62,8.7,1.65,8.38,'edge')]
blade=[];sheath=[];glow=[]
for i in range(60):
 y=1.65+i*.5;x=8+.004*(y-1.65)**2;angle=-math.degrees(math.atan(.008*(y-1.65)))
 width=1.35*min(1,(60-i)/10);rot=([x,y,8],'z',angle)
 e=box(x-width/2,y-.015,7.88,x+width/2,y+.515,8.12,'steel',rot)
 for face in ('north','south'):e['faces'][face]['uv']=[0,i*16/60,16,(i+1)*16/60]
 e['faces']['east']['texture']='#spine';e['faces']['west']['texture']='#edge';blade.append(e)
 # A silver-blue edge follows the curve; no protruding cuboid ribs.
 blade.append(box(x-width/2,y-.015,7.875,x-width/2+min(.07,width*.2),y+.515,8.125,'edge',rot,2))
 sheath.append(box(x-width/2-.16,y-.015,7.58,x+width/2+.16,y+.515,8.42,'black',rot))
 glow.append(box(x-width/2-.18,y-.015,7.56,x-width/2-.10,y+.515,8.44,'light',rot,15))
 if i in [0,1,55,56]:sheath.append(box(x-width/2-.20,y,7.56,x+width/2+.20,y+.32,8.44,'gold',rot))
for i in range(4):sheath.append(box(7.06,3+i*.38,7.4,8.94,3.2+i*.38,8.6,'wrap'))
# A tiny gold crescent inlay on the saya, front and back.
for i in range(10):
 a=-1.2+i*.27;x=8+.35*math.cos(a);y=8+.58*math.sin(a)
 for z in [7.54,8.43]:sheath.append(box(x-.06,y-.07,z,x+.06,y+.07,z+.03,'gold'))
display={'gui':{'rotation':[0,0,-38],'translation':[0,-5,0],'scale':[.35]*3},'ground':{'rotation':[0,0,90],'translation':[0,2,0],'scale':[.4]*3},'fixed':{'rotation':[0,180,-35],'translation':[0,-5,0],'scale':[.5]*3},'firstperson_righthand':{'rotation':[0,-12,-18],'translation':[-.8,6.5,-3],'scale':[.48]*3},'firstperson_lefthand':{'rotation':[0,12,18],'translation':[-.8,6.5,-3],'scale':[.48]*3},'thirdperson_righthand':{'rotation':[0,0,-25],'translation':[4.18,8.97,0],'scale':[.9]*3},'thirdperson_lefthand':{'rotation':[0,0,25],'translation':[-4.18,8.97,0],'scale':[.9]*3}}
variants=[('tachi',hilt+blade),('sheathed',hilt+sheath)]
for light in [4,8,12,15]:
 g=json.loads(json.dumps(glow))
 for e in g:e['light_emission']=light
 variants.append(('charged'+str(light),hilt+sheath+g))
for name,elements in variants:
 d=json.loads(json.dumps(display))
 if name!='tachi':
  for hand,sign in [('righthand',1),('lefthand',-1)]:
   d['firstperson_'+hand]={'rotation':[0,sign*-10,sign*72],'translation':[-2,6,-3],'scale':[.5]*3}
   d['thirdperson_'+hand]={'rotation':[0,0,sign*68],'translation':[-sign*9.18,3.71,0],'scale':[.9]*3}
 js(Path('models/item')/(name+'.json'),{'textures':textures,'elements':elements,'display':d,'gui_light':'front'})
def model(n):return {'type':'minecraft:model','model':'oborokage:item/'+n}
js(Path('items/tachi.json'),{'model':{'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':'gui','model':model('icon')}],'fallback':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':model('tachi'),'on_true':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','scale':1,'entries':[{'threshold':t,'model':model('charged'+str(v))} for t,v in [(10,4),(20,8),(30,12),(40,15)]],'fallback':model('sheathed')}}}})
for n in ['sheathed','charged15']:js(Path('items')/(n+'.json'),{'model':model(n)})
# Open sweeping crescent, a sharp leading edge and tapered trailing violet ribbon.
n=512;pix=[]
for y in range(n):
 for x in range(n):
  u=(x+.5-n/2)/(n/2);v=(y+.5-n/2)/(n/2);r=math.hypot(u,v);a=math.atan2(v,u)
  taper=max(0,math.cos(a/1.12))**1.8 if abs(a)<math.pi*.56 else 0
  center=.76;d=r-center;width=.008+.013*taper
  core=math.exp(-(d/width)**2);trail=.42*math.exp(-((d+.06*taper)/(.055*taper+.005))**2)
  alpha=min(1,core+trail)*taper
  pix.extend([int(139+115*core),int(110+144*core),255,int(255*alpha)])
png(Path('textures/particle/crescent.png'),n,n,pix)
# Blade afterimage: an elongated tapered highlight, not another ring.
w=256;h=32;pix=[]
for y in range(h):
 for x in range(w):
  t=x/(w-1);v=(y-h/2)/(h/2);taper=math.sin(math.pi*t)**.6;core=math.exp(-(v/(.025+.09*taper))**2);halo=.32*math.exp(-(v/(.05+.4*taper))**2)
  pix.extend([int(133+122*core),int(104+151*core),255,int(255*min(1,core+halo)*taper)])
png(Path('textures/particle/streak.png'),w,h,pix)
js(Path('particles/streak.json'),{'textures':['oborokage:streak']})
print('Refined tangent-aligned blade, grip pivots, braided tsuka, crescent tsuba and directional ribbons')

js(Path('models/item/icon.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'oborokage:item/icon'}})
# Pixel icon: a long tapered curved blade, charcoal wrapped hilt and gold guard.
w=64;pixels=[0]*(w*w*4)
def dot(x,y,c,r=0):
 for dy in range(-r,r+1):
  for dx in range(-r,r+1):
   if 0<=x+dx<w and 0<=y+dy<w:
    k=((y+dy)*w+x+dx)*4;pixels[k:k+4]=[*c,255]
for i in range(42):
 t=i/41;x=15+int(40*t+4*t*t);y=49-int(42*t)
 dot(x,y,colors['spine'],1)
 if i<39:dot(x-1,y,colors['edge'],0);dot(x,y,colors['silver'],0)
for i in range(12):
 x=15-i;y=49+i;dot(x,y,colors['ink'],1)
 if i%3==0:dot(x,y,colors['silk'],1)
for i in range(-4,5):dot(15+i,49+i,colors['gold'])
dot(3,61,colors['gold'],1)
png(Path('textures/item/icon.png'),64,64,pixels);png(Path('icon.png'),64,64,pixels)
