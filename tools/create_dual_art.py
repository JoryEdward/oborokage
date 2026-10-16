"""Original short-blade variants derived from this project's own parametric sculpture."""
from pathlib import Path
import json,copy,math,struct,zlib,shutil
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
assets=ROOT/'assets/oborokage'
def save(path,data):
 p=assets/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def short_model(source):
 j=json.loads((assets/'models/item'/f'{source}.json').read_text())
 for e in j['elements']:
  for k in ['from','to']:
   e[k][1]*=.62 if e[k][1]>0 else .73
   e[k][0]=8+(e[k][0]-8)*.82
  if 'rotation' in e:
   o=e['rotation']['origin'];o[1]*=.62 if o[1]>0 else .73;o[0]=8+(o[0]-8)*.82
 for hand in ['righthand','lefthand']:
  j['display']['firstperson_'+hand]['translation']=[-.3,4.8,-2]
  j['display']['thirdperson_'+hand]['translation']=[3.1 if hand=='righthand' else -3.1,6.55,0]
 return j
for src,dst in [('tachi','wakizashi'),('sheathed','waki_sheathed')]:save(Path('models/item')/(dst+'.json'),short_model(src))
for src,dst in [('tachi','tachi_scabbard'),('sheathed','long_scabbard'),('sheathed','short_scabbard')]:
 if dst=='tachi_scabbard':continue
 j=short_model(src) if dst=='short_scabbard' else json.loads((assets/'models/item/sheathed.json').read_text())
 j['elements']=[e for e in j['elements'] if e['from'][1]>=1]
 j['display']['ground']={'scale':[.78]*3,'translation':[0,0,0]}
 save(Path('models/item')/(dst+'.json'),j)
 save(Path('items')/(dst+'.json'),{'model':{'type':'minecraft:model','model':'oborokage:item/'+dst}})
for dst in ['waki_sheathed']:save(Path('items')/(dst+'.json'),{'model':{'type':'minecraft:model','model':'oborokage:item/'+dst}})
save(Path('items/wakizashi.json'),{'model':{'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':'gui','model':{'type':'minecraft:model','model':'oborokage:item/waki_icon'}}],'fallback':{'type':'minecraft:model','model':'oborokage:item/wakizashi'}}})
save(Path('models/item/waki_icon.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'oborokage:item/waki_icon'}})
# Hand-authored small pixel blade: dark outline, pearl edge, lavender grip and warm guard.
w=h=32;pix=bytearray(w*h*4)
def dot(x,y,c):
 if 0<=x<w and 0<=y<h:pix[(y*w+x)*4:(y*w+x+1)*4]=bytes(c)
for i in range(5,24):
 x=i;y=29-i
 for d,c in [(-1,(38,34,58,255)),(0,(244,247,255,255)),(1,(153,180,213,255)),(2,(66,74,107,255))]:dot(x,y+d,c)
for i in range(3,9):
 for d in range(-1,2):dot(i,29-i+d,(117 if i%2 else 60,80 if i%2 else 43,154 if i%2 else 82,255))
for d in range(-2,4):dot(9+d,21+d,(213,181,112,255))
def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
raw=b''.join(b'\0'+pix[y*w*4:(y+1)*w*4] for y in range(h))
(assets/'textures/item/waki_icon.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
(assets/'textures/fx').mkdir(exist_ok=True)
(assets/'textures/fx/white.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0\xff\xff\xff\xff'))+chunk(b'IEND',b''))
# Copy data drafts only after the new item is registered in code.
base=ROOT.parents[2]/'planning/expansion-2026-10-15/draft-data/data'
shutil.copytree(base,ROOT/'data',dirs_exist_ok=True)
rows=[
('first_moon',None,'初见胧月','First Moonrise','获得胧影太刀或胧月脇差。','Obtain either Oborokage blade.','task','tachi'),
('twin_moons','first_moon','双月同鞘','Two Moons, One Path','主手太刀、副手脇差，保持双刀姿态一秒。','Hold the tachi and wakizashi together for a second.','task','wakizashi'),
('alternating_finish','twin_moons','双月同辉','Twin Moons Aligned','主副手有效命中后，以三连末段击败敌人。','Land both hands of a combo and finish a hostile with its cross-cut.','goal','wakizashi'),
('borrowed_blade','twin_moons','借你一刀，还你一月','A Blade Borrowed, a Moon Returned','精准架刀后，反切击败本次攻击者。','Perfect-guard and defeat that attacker with the counter.','challenge','tachi'),
('decisive_step','twin_moons','一步千钧','A Step That Decides','完整影步后，迅速用副手招式命中敌人。','Complete a shadow step and promptly land an offhand strike.','goal','wakizashi'),
('three_breaths','borrowed_blade','三息不乱','Three Breaths, Unbroken','二十秒内无伤完成三次独立精准架刀。','Perfect-guard three distinct attacks within twenty seconds, unharmed.','challenge','wakizashi'),
('break_and_cut','alternating_finish','破势见锋','Beyond the Guard','使敌人破势，在窗口内以交叉斩命中。','Break posture and cross-cut within the opening.','goal','tachi'),
('twin_moon_ultimate','twin_moons','双月归寂','Silence of Twin Moons','以双刀奥义终斩击败敌人。','Defeat a hostile with the dual ultimate finishing cut.','goal','wakizashi'),
('flawless_night','twin_moon_ultimate','长夜无伤','Unscarred Through the Night','六十秒内无伤以至少三次双刀动作击败五个敌人，双手均有效命中。','Defeat five hostiles with at least three dual actions in sixty seconds; land both hands and take no damage.','challenge','tachi')]
for locale in ['zh_cn','en_us']:
 p=assets/'lang'/f'{locale}.json';lang=json.loads(p.read_text())
 lang.update({'item.oborokage.wakizashi':'胧月脇差' if locale=='zh_cn' else 'Oborotsuki Wakizashi','itemGroup.oborokage.equipment':'胧影' if locale=='zh_cn' else 'Oborokage','key.category.oborokage.combat':'胧影 · 双刀' if locale=='zh_cn' else 'Oborokage: Dual Blades','key.oborokage.guard':'交叉架刀' if locale=='zh_cn' else 'Cross Guard','key.oborokage.step':'影步' if locale=='zh_cn' else 'Shadow Step','key.oborokage.ultimate':'双刀奥义' if locale=='zh_cn' else 'Dual Ultimate','ui.oborokage.dual_ultimate':'奥义 · 双月归寂' if locale=='zh_cn' else 'Twin Moons: Silence','ui.oborokage.intent':'剑意' if locale=='zh_cn' else 'Blade Intent','ui.oborokage.stamina':'架势' if locale=='zh_cn' else 'Guard','tooltip.oborokage.wakizashi':'置于副手，与主手胧影太刀组成双刀流' if locale=='zh_cn' else 'Equip offhand with a mainhand Oborokage Tachi.','tooltip.oborokage.controls':'左键交替连斩 · 控制设置可重绑定架刀、影步和奥义' if locale=='zh_cn' else 'Attack to alternate; rebind guard, step and ultimate in Controls.'})
 for id,parent,zh,en,desc,edesc,frame,item in rows:lang[f'advancement.oborokage.{id}.title']=zh if locale=='zh_cn' else en;lang[f'advancement.oborokage.{id}.description']=desc if locale=='zh_cn' else edesc
 if locale=='zh_cn':lang['advancement.oborokage.first_moon.title']='胧影剑录 · 初见胧月'
 else:lang['advancement.oborokage.first_moon.title']='Oborokage Chronicle: First Moonrise'
 p.write_text(json.dumps(lang,ensure_ascii=False,indent=2)+'\n')
for id,parent,zh,en,desc,edesc,frame,item in rows:
 display={'icon':{'id':'oborokage:'+item},'title':{'translate':f'advancement.oborokage.{id}.title'},'description':{'translate':f'advancement.oborokage.{id}.description'},'frame':frame,'show_toast':True,'announce_to_chat':frame=='challenge','hidden':False}
 if parent is None:display['background']='oborokage:gui/advancements/background'
 j={'display':display,'criteria':{'complete':{'trigger':'minecraft:impossible'}},'requirements':[['complete']]}
 if parent:j['parent']='oborokage:'+parent
 p=ROOT/'data/oborokage/advancement'/f'{id}.json';p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
p=ROOT/'data/oborokage/advancement/moon_still_hangs.json';j=json.loads(p.read_text());j['parent']='oborokage:first_moon';p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
print('Created original short blade, sheath variants, two recipes, recipe-book entries, nine advancements and translations.')

# A dark woven moon-grid for the advancement GUI atlas.
bg=bytearray()
for y in range(32):
 for x in range(32):
  line=(x+y)%16==0 or (x-y)%16==0
  r=math.hypot(x-16,y-16);moon=abs(r-10)<.6 and x>13
  bg.extend((35 if moon else 24 if line else 15,30 if moon else 21 if line else 13,48 if moon else 33 if line else 23,255))
raw=b''.join(b'\0'+bg[y*32*4:(y+1)*32*4] for y in range(32))
p=assets/'textures/gui/advancements/background.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
