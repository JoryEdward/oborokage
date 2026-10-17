"""Original parametric blade family; all source geometry belongs to this project."""
from pathlib import Path
import json,struct,zlib,copy
root=Path(__file__).resolve().parents[1]/'src/main/resources';a=root/'assets/oborokage'
def save(p,j):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
def png(p,w,h,pix):
 def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 raw=b''.join(b'\0'+bytes(pix[y*w*4:(y+1)*w*4]) for y in range(h))
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
styles=[('raikiri','赤霆刀','Raikiri',.82,.82,(245,104,61),'minecraft:copper_ingot','minecraft:redstone','20 tick 满蓄力 · 短距突击，快速追击','20 ticks to full charge: a swift close assault.'),('frostmoon','霜轮太刀','Frostmoon',1.0,1.25,(108,214,239),'minecraft:packed_ice','minecraft:lapis_lazuli','40 tick 满蓄力 · 环形霜斩，减速周围敌人','40 ticks to full charge: a radial cut that slows nearby foes.'),('voidstring','空弦太刀','Voidstring',1.0,.70,(195,131,246),'minecraft:ender_pearl','minecraft:amethyst_shard','40 tick 满蓄力 · 直线剑气，最远 28 格，受墙体阻挡','40 ticks to full charge: a wall-blocked energy line up to 28 blocks.')]
for name,zh,en,length,width,color,material,gem,tip,etip in styles:
 for kind,c in [('silk',color),('light',tuple(min(255,v+35) for v in color)),('gold',color),('wrap',tuple(v//3 for v in color))]:
  pix=[]
  for y in range(16):
   for x in range(16):pix.extend([max(0,min(255,v+(8 if (x+y)%4==0 else 0))) for v in c]+[255])
  png(a/f'textures/item/{name}_{kind}.png',16,16,pix)
 for source in ['tachi','sheathed','charged4','charged8','charged12','charged15']:
  j=json.loads((a/f'models/item/{source}.json').read_text())
  for key in ['silk','light','gold','wrap']:j['textures'][key]=f'oborokage:item/{name}_{key}'
  for e in j['elements']:
   for k in ['from','to']:
    e[k][0]=8+(e[k][0]-8)*width
    if e[k][1]>0:e[k][1]*=length
   if 'rotation' in e:
    o=e['rotation']['origin'];o[0]=8+(o[0]-8)*width
    if o[1]>0:o[1]*=length
  save(a/f'models/item/{name}_{source}.json',j)
 j=json.loads((a/'items/tachi.json').read_text())
 def remap(v):
  if isinstance(v,dict):
   for k,value in list(v.items()):
    if k=='model' and isinstance(value,str) and value.startswith('oborokage:item/'):
     s=value.split('/')[-1];v[k]=f'oborokage:item/{name}_'+s
    else:remap(value)
  elif isinstance(v,list):
   for value in v:remap(value)
 remap(j);save(a/f'items/{name}.json',j)
 save(a/f'models/item/{name}_icon.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'oborokage:item/{name}_icon'}})
 pix=[0]*(32*32*4)
 def dot(x,y,c):
  if 0<=x<32 and 0<=y<32:pix[(y*32+x)*4:(y*32+x+1)*4]=[*c,255]
 for i in range(4,29 if name=='voidstring' else 26):
  y=30-i+(1 if name=='frostmoon' and i>20 else 0)
  for d,c in [(-1,(25,27,40)),(0,(244,246,255)),(1,color),(2,(38,44,65))]:dot(i,y+d,c if i>9 else (color if i%2 else (33,30,46)))
 for d in range(-2,4):dot(9+d,22+d,color)
 png(a/f'textures/item/{name}_icon.png',32,32,pix)
 save(root/f'data/oborokage/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'equipment','pattern':['  D',' MD','SG '],'key':{'D':'minecraft:diamond','M':material,'S':'minecraft:stick','G':gem},'result':{'id':f'oborokage:{name}'}})
 save(root/f'data/oborokage/advancement/recipes/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':material}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipes':f'oborokage:{name}'}}},'requirements':[['has_material','has_the_recipe']],'rewards':{'recipes':[f'oborokage:{name}']}})
 for locale in ['zh_cn','en_us']:
  p=a/f'lang/{locale}.json';lang=json.loads(p.read_text());ch=locale=='zh_cn'
  lang[f'item.oborokage.{name}']=zh if ch else en
  lang['tooltip.oborokage.'+{'raikiri':'storm','frostmoon':'frost','voidstring':'void'}[name]]=tip if ch else etip
  lang['tooltip.oborokage.terrain']='下蹲蓄力：斩物 / 斩木；蓄力越高，破坏数量越多' if ch else 'Sneak and charge: cut blocks or connected logs. More charge cuts more.'
  lang['tooltip.oborokage.wakizashi']='置于副手，与任意胧影系列主刀组成双刀流' if ch else 'Equip offhand with any Oborokage main blade.'
  save(p,lang)
for p in (root/'data/minecraft/tags/item').rglob('*.json'):
 j=json.loads(p.read_text())
 if 'oborokage:tachi' in j.get('values',[]):
  for name,*_ in styles:
   if f'oborokage:{name}' not in j['values']:j['values'].append(f'oborokage:{name}')
  save(p,j)
