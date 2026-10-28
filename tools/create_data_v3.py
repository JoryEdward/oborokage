"""1.1.0 data: the 朽英剑主 (loot, entity type tags, its advancement), the ruined dojo (its NBT
template, made from the dojo's with a ruin pass; structure, pool, biome tag, chest loot; it shares
the dojos' structure set), the stone base under the dojos (the oborokage:foundation processor
lists), the structure tags of the dojos, the 百鬼夜行's advancement, and the blade tomb's four
regional variants (templates, structures, pools, biome tags, chest loot, one structure set for all
five, the tombs' structure tag and 「四方刀冢」).

Run after create_data_v1 and create_data_v2:
  python3 tools/create_data_v1.py && python3 tools/create_data_v2.py && python3 tools/create_data_v3.py
"""
import random, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from create_data_v1 import DATA, LINES, ROOT, count, item, looting, overlay, write  # noqa: E402
from create_data_v1 import nbt_compound, tag_string  # noqa: E402
from create_data_v2 import dojo  # noqa: E402

# The dojos' structure set: dojos and ruined dojos, three to one (create_data_v2 had the dojo alone).
# 8 / 4 keeps the plain dojos above 1.0.2's count with a ruin in every fourth (survey of six seeds
# over 12288 blocks square: 157 in all against 107; groves 100 blocks across with one, 71% -> 81%;
# never closer than 82 blocks; no more tombs beside them).
SPACING, SEPARATION = 8, 4


# ------------------------------------------------------------------ the 朽英剑主

def loot(line):
    new = line == '26.3'
    lt = overlay(line) / 'loot_table'
    rolls = (lambda n: n) if new else (lambda n: float(n))
    # The relic (15%, the broken form of its own blade) is dropped in code: WitheredLord.
    write(lt / 'entities/withered_lord.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': rolls(1), 'entries': [item('oborokage:intent_crystal', [count(2, 4, new), looting(new)], new=new)]},
        {'rolls': rolls(1), 'entries': [item('oborokage:blade_soul', [count(4, 8, new), looting(new)], new=new)]},
    ], 'random_sequence': 'oborokage:entities/withered_lord'})


def tags():
    mc = ROOT / 'src/main/resources/data/minecraft/tags'
    ghosts = ['oborokage:blossom_oni', 'oborokage:withered_lord']
    write(mc / 'entity_type/undead.json', {'replace': False, 'values': ghosts})
    # Only the 樱下鬼 burns: the 朽英剑主 does not fear the sun.
    write(mc / 'entity_type/burn_in_daylight.json', {'replace': False, 'values': ['oborokage:blossom_oni']})
    write(mc / 'entity_type/freeze_immune_entity_types.json', {'replace': False, 'values': ghosts})
    write(mc / 'entity_type/powder_snow_walkable_mobs.json', {'replace': False, 'values': ghosts})
    write(DATA / 'tags/entity_type/finisher_exempt.json', {
        'replace': False,
        'values': ['oborokage:training_dummy', 'oborokage:petal_lord', 'oborokage:withered_lord']})


def advancements():
    write(DATA / 'advancement/night_parade.json', {
        'parent': 'oborokage:first_moon',
        'display': {'icon': {'id': 'minecraft:soul_torch'},
                    'title': {'translate': 'advancement.oborokage.night_parade.title'},
                    'description': {'translate': 'advancement.oborokage.night_parade.description'},
                    'frame': 'challenge', 'show_toast': True, 'announce_to_chat': True, 'hidden': True},
        'criteria': {'complete': {'trigger': 'minecraft:impossible'}},
        'requirements': [['complete']]})
    write(DATA / 'advancement/withered_rest.json', {
        'parent': 'oborokage:first_moon',
        'display': {'icon': {'id': 'minecraft:soul_lantern'},
                    'title': {'translate': 'advancement.oborokage.withered_rest.title'},
                    'description': {'translate': 'advancement.oborokage.withered_rest.description'},
                    'frame': 'challenge', 'show_toast': True, 'announce_to_chat': True, 'hidden': True},
        'criteria': {'complete': {'trigger': 'minecraft:impossible'}},
        'requirements': [['complete']]})


# ------------------------------------------------------------------ the ruined dojo

def ruined_dojo():
    """朽道场: the dojo after its master died. Two great holes in the roof with the tiles and boards
    lying where they fell, the paper walls a third torn away and cobwebbed, rotten boards and moss in
    the hall's floor, the yard mossed over; the torii's right post fallen; soul fire in the lanterns;
    three small graves in the yard, an unlit candle before each, and stumps where the training posts
    stood; cherry leaves grown over the yard and the eaves, vines up the walls, the hedge gapped. The
    chest and whetstone stay at the back of the hall; the 朽英剑主 sits where the master sat."""
    r = random.Random(1985061138)
    air = 'minecraft:air'
    leaves = dict(distance='1', persistent='true', waterlogged='false')
    post = dict(east='none', north='none', south='none', west='none', up='true', waterlogged='false')
    solid = {'minecraft:white_concrete', 'minecraft:white_stained_glass', 'minecraft:dark_oak_planks',
             'minecraft:stripped_dark_oak_log'}

    def ruin(put, name_at, entity):
        holes = [(5.0, 12.5, 3.3), (11.0, 9.5, 2.7)]

        def hole(x, z, pad=0.0):
            return any((x - hx) ** 2 + (z - hz) ** 2 <= (hr + pad) ** 2 for hx, hz, hr in holes)

        # The master's seat and the way to it, the chest and the whetstone stay clear.
        keep = {(8, 11), (8, 12), (8, 13), (7, 14), (8, 14)}
        # The roof, in two great holes (about two fifths of it); the gable boards under them too.
        for z in range(7, 17):
            h = 6 + min(z - 7, 16 - z)
            for x in range(2, 15):
                if not hole(x, z):
                    continue
                put(x, h, z, air)
                if x in (3, 13) and 8 <= z <= 15:
                    for y in range(6, h):
                        put(x, y, z, air)
                if 4 <= x <= 12 and 9 <= z <= 14 and (x, z) not in keep and r.random() < .35:
                    put(x, 2, z, r.choice(['minecraft:deepslate_tile_slab', 'minecraft:dark_oak_slab']),
                        type='bottom', waterlogged='false')
        for z in range(9, 15):
            h = 6 + min(z - 7, 16 - z)
            for x in range(4, 13):
                if not hole(x, z) and hole(x, z, 1.3) and r.random() < .5:
                    put(x, h - 1, z, 'minecraft:cobweb')
        for x, z in [(4, 9), (12, 9), (4, 14), (12, 14)]:
            put(x, 5, z, 'minecraft:cobweb')
        # The paper walls: a third of the panels gone, cobwebs in some of the gaps.
        for x in range(3, 14):
            for z in range(8, 16):
                if x in (3, 13) or z in (8, 15):
                    for y in range(2, 6):
                        if name_at(x, y, z) in ('minecraft:white_concrete', 'minecraft:white_stained_glass') \
                                and r.random() < .33:
                            put(x, y, z, 'minecraft:cobweb' if r.random() < .35 else air)
        # The floor: rotten boards and moss in the hall, the yard mossed and cracked.
        for x in range(4, 13):
            for z in range(9, 15):
                k = r.random()
                if (x, z) in keep:
                    continue
                if k < .16:
                    put(x, 1, z, 'minecraft:stripped_dark_oak_wood', axis='y')
                elif k < .26:
                    put(x, 1, z, 'minecraft:mossy_cobblestone')
                elif k < .30:
                    put(x, 1, z, 'minecraft:moss_block')
        for x in range(17):
            for z in range(17):
                n, k = name_at(x, 0, z), r.random()
                if n == 'minecraft:stone_bricks' and not (3 <= x <= 13 and 8 <= z <= 15) and k < .35:
                    put(x, 0, z, r.choice(['minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks']))
                elif n in ('minecraft:andesite', 'minecraft:tuff') and k < .35:
                    put(x, 0, z, r.choice(['minecraft:mossy_cobblestone', 'minecraft:moss_block',
                                           'minecraft:coarse_dirt']))
                elif n == 'minecraft:polished_andesite' and k < .3:
                    put(x, 0, z, r.choice(['minecraft:andesite', 'minecraft:mossy_cobblestone']))
        # The torii: the right post has fallen toward the hall, and the beams on that side with it.
        for y in range(1, 5):
            put(10, y, 1, air)
        for x in range(8, 10):
            put(x, 4, 1, air)
        for x in range(8, 12):
            put(x, 5, 1, air)
        for x in range(8, 13):
            put(x, 6, 1, air)
        for z in range(2, 6):
            put(11, 1, z, 'minecraft:stripped_mangrove_log', axis='z')
        # Soul fire in the stone lanterns and the hall's lanterns.
        for x in (4, 12):
            put(x, 1, 4, 'minecraft:mossy_stone_brick_wall', **post)
            put(x, 2, 4, 'minecraft:soul_lantern', hanging='false', waterlogged='false')
            put(x, 3, 4, 'minecraft:mossy_stone_brick_slab', type='bottom', waterlogged='false')
            for z in (9, 14):
                put(x, 2, z, 'minecraft:soul_lantern', hanging='false', waterlogged='false')
        # Three small graves, an unlit candle before each; stumps where the training posts stood.
        for x, z, cx in [(2, 2, 3), (14, 2, 13), (2, 5, 3)]:
            put(x, 1, z, 'minecraft:mossy_stone_brick_wall', **post)
            put(cx, 1, z, 'minecraft:candle', candles='1', lit='false', waterlogged='false')
        for x, z in [(3, 6), (13, 6)]:
            put(x, 1, z, 'minecraft:stripped_spruce_log', axis='y')
        # Overgrowth: leaves in the yard and along the eaves, vines up the walls, the hedge gapped.
        yard = [(x, z) for x in range(1, 16) for z in range(1, 8) if not 6 <= x <= 10]
        for _ in range(8):
            x, z = r.choice(yard)
            if name_at(x, 1, z) == air:
                put(x, 1, z, 'minecraft:cherry_leaves', **leaves)
                if r.random() < .4:
                    put(x, 2, z, 'minecraft:cherry_leaves', **leaves)
        for _ in range(16):
            x, z = r.choice(yard)
            if name_at(x, 1, z) == air:
                put(x, 1, z, 'minecraft:moss_carpet')
        for z in range(7, 17):
            h = 6 + min(z - 7, 16 - z)
            for x in range(2, 15):
                eave = x in (2, 14) or z in (7, 16)
                if eave and name_at(x, h, z) != air and h + 1 < 12 and r.random() < .3:
                    put(x, h + 1, z, 'minecraft:cherry_leaves', **leaves)
        vine = dict(up='false', north='false', east='false', south='false', west='false')
        sides = [((x, 7), (x, 8), 'south') for x in (4, 5, 11, 12)] + \
                [((x, 16), (x, 15), 'north') for x in range(4, 13)] + \
                [((2, z), (3, z), 'east') for z in range(9, 15)] + \
                [((14, z), (13, z), 'west') for z in range(9, 15)]
        for (vx, vz), (wx, wz), face in sides:
            if r.random() < .3:
                for y in range(2, 2 + r.randint(1, 3)):
                    if name_at(vx, y, vz) == air and name_at(wx, y, wz) in solid:
                        put(vx, y, vz, 'minecraft:vine', **dict(vine, **{face: 'true'}))
        for x in range(17):
            for z in range(8):
                if name_at(x, 1, z) == 'minecraft:cherry_leaves' and (x in (0, 16) or z == 0) \
                        and r.random() < .25:
                    put(x, 1, z, air)
        # The chest holds the ruin's own loot; the 朽英剑主 sits where the master sat.
        put(8, 2, 14, 'minecraft:chest', {'LootTable': (8, 'oborokage:chests/ruined_dojo')},
            facing='north', type='single', waterlogged='false')
        return [entity('oborokage:withered_lord', 8.5, 2.0, 12.5, 180)]

    dojo(ruin=ruin, path='ruined_dojo/ruined_dojo')


def ruined_loot(line):
    new = line == '26.3'
    rolls = (lambda n: n) if new else (lambda n: float(n))
    uniform = (lambda a, b: {'type': 'minecraft:uniform', 'min': a, 'max': b}) if new else \
        (lambda a, b: {'type': 'minecraft:uniform', 'min': float(a), 'max': float(b)})
    entries = [item(f'oborokage:inscription_{k}', weight=2)
               for k in ['zanshin', 'focus', 'feather', 'dew', 'crimson']]
    entries += [item('minecraft:flint', [count(2, 6, new)], 8, new),
                item('minecraft:soul_lantern', [count(1, 2, new)], 5, new),
                item('minecraft:bone', [count(2, 6, new)], 6, new),
                item('minecraft:cobweb', [count(1, 3, new)], 4, new),
                item('minecraft:paper', [count(1, 4, new)], 4, new),
                item('minecraft:candle', [count(1, 3, new)], 4, new)]
    # One chest in twenty holds the broken form of one of the four long blades.
    relics = [item(f'oborokage:broken_{k}', weight=1) for k in ['tachi', 'raikiri', 'frostmoon', 'voidstring']]
    relics.append({'type': 'minecraft:empty', 'weight': 76})
    write(overlay(line) / 'loot_table/chests/ruined_dojo.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': rolls(1), 'entries': [item('oborokage:blade_soul', [count(3, 6, new)], new=new)]},
        {'rolls': rolls(1), 'entries': [item('oborokage:intent_crystal', [count(1, 3, new)], new=new)]},
        {'rolls': uniform(2, 4), 'entries': entries},
        {'rolls': rolls(1), 'entries': relics},
    ], 'random_sequence': 'oborokage:chests/ruined_dojo'})


def structure_json(line):
    new = line == '26.3'
    # At night the 樱下鬼 gather in the ruin (BlossomOni.checkSpawn lets them rise under its roof
    # too, by night only); nothing else spawns there.
    spawn = {'type': 'oborokage:blossom_oni', 'weight': 1}
    if new:
        spawn['count'] = 1
    else:
        spawn.update({'minCount': 1, 'maxCount': 1})
    write(overlay(line) / 'worldgen/structure/ruined_dojo.json', {
        'type': 'minecraft:jigsaw', 'biomes': '#oborokage:has_structure/ruined_dojo',
        'max_distance_from_center': 16, 'project_start_to_heightmap': 'WORLD_SURFACE_WG',
        'size': 1, 'spawn_overrides': {'monster': {'bounding_box': 'piece', 'spawns': [spawn]}},
        'start_height': {'absolute': 0}, 'start_pool': 'oborokage:ruined_dojo/start',
        'step': 'surface_structures', 'terrain_adaptation': 'beard_box', 'use_expansion_hack': False})


# ------------------------------------------------------------------ the blade tombs

import gzip, io  # noqa: E402

# Each variant keeps the tomb's layout (a tomb is known by it: the chiseled stele right behind the
# chest, a lightning rod three blocks in front, four candles), in the blocks of its land.
TOMB_VARIANTS = {
    # 雪原刀冢: packed ice and snow, spruce posts, white candles, snow lying about.
    'blade_tomb_snow': dict(
        floor=[('minecraft:packed_ice', 3), ('minecraft:snow_block', 4), ('minecraft:stone_bricks', 3)],
        post='minecraft:spruce_log', flank='minecraft:spruce_planks',
        mound=('minecraft:snow_block', 'minecraft:packed_ice'), candle='minecraft:white_candle',
        cover=('minecraft:snow', dict(layers='1')), relic={'frostmoon': 4}, seed=1462731011,
        base=['minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:snow_block'], adapt='beard_box',
        biomes=['minecraft:snowy_plains', 'minecraft:snowy_taiga', 'minecraft:grove',
                'minecraft:snowy_slopes']),
    # 山岳刀冢: andesite and deepslate, a second, weathered lightning rod.
    'blade_tomb_mountain': dict(
        floor=[('minecraft:andesite', 4), ('minecraft:cobbled_deepslate', 3), ('minecraft:deepslate_tiles', 3)],
        post='minecraft:cobbled_deepslate', flank='minecraft:deepslate_bricks',
        mound=('minecraft:tuff', 'minecraft:gravel'), candle='minecraft:candle',
        cover=('minecraft:moss_carpet', {}), relic={'raikiri': 4}, seed=1462731012,
        base=['minecraft:cobbled_deepslate', 'minecraft:andesite', 'minecraft:tuff'], adapt='beard_box',
        biomes=['minecraft:windswept_hills', 'minecraft:windswept_forest',
                'minecraft:windswept_gravelly_hills', 'minecraft:meadow'],
        extra=[((1, 1, 7), 'minecraft:oxidized_lightning_rod', dict(facing='up', powered='false', waterlogged='false'))]),
    # 樱林刀冢: cherry wood, pink candles, petals on the stones.
    'blade_tomb_cherry': dict(
        floor=[('minecraft:stone_bricks', 3), ('minecraft:mossy_stone_bricks', 3), ('minecraft:cherry_planks', 3)],
        post='minecraft:cherry_log', flank='minecraft:cherry_planks',
        mound=('minecraft:moss_block', 'minecraft:coarse_dirt'), candle='minecraft:pink_candle',
        cover=('minecraft:pink_petals', 'petals'), relic={'tachi': 3, 'wakizashi': 3}, seed=1462731013,
        base=['minecraft:stone_bricks', 'minecraft:mossy_cobblestone', 'minecraft:cobblestone'],
        biomes=['minecraft:cherry_grove']),
    # 荒原刀冢: sandstone and terracotta, dead bushes.
    'blade_tomb_badlands': dict(
        floor=[('minecraft:sandstone', 3), ('minecraft:cut_sandstone', 3), ('minecraft:smooth_sandstone', 2),
               ('minecraft:terracotta', 2)],
        post='minecraft:cut_sandstone', flank='minecraft:chiseled_sandstone',
        mound=('minecraft:coarse_dirt', 'minecraft:terracotta'), candle='minecraft:orange_candle',
        cover=('minecraft:dead_bush', 'bush'), relic={'voidstring': 4}, seed=1462731014,
        base=['minecraft:sandstone', 'minecraft:cut_sandstone', 'minecraft:terracotta'],
        biomes=['minecraft:desert', 'minecraft:badlands', 'minecraft:eroded_badlands',
                'minecraft:wooded_badlands']),
}
# The tomb as it was (原野刀冢) keeps to the forests, taigas, plains and savannas; the snowy, the
# windswept, the meadows and the cherry groves now have their own. (Biomes not on every line are
# optional.)
TOMB_BIOMES = ['minecraft:forest', 'minecraft:flower_forest', 'minecraft:birch_forest',
               'minecraft:old_growth_birch_forest', 'minecraft:dark_forest',
               {'id': 'minecraft:pale_garden', 'required': False},
               {'id': 'minecraft:dappled_forest', 'required': False},
               'minecraft:taiga', 'minecraft:old_growth_pine_taiga', 'minecraft:old_growth_spruce_taiga',
               'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:savanna']
RELIC_KINDS = ['tachi', 'wakizashi', 'raikiri', 'frostmoon', 'voidstring']


def tomb_variant(name, v):
    r = random.Random(v['seed'])
    palette, index, blocks = [], {}, {}

    def state(block, **props):
        key = (block, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': (8, block)}
            if props:
                entry['Properties'] = (10, {k: (8, str(val)) for k, val in props.items()})
            palette.append(entry)
        return index[key]

    def put(x, y, z, block, nbt=None, **props):
        blocks[(x, y, z)] = (state(block, **props), nbt)

    floors = [b for b, w in v['floor'] for _ in range(w)]
    for x in range(9):
        for z in range(9):
            put(x, 0, z, r.choice(floors))
            for y in range(1, 6):
                put(x, y, z, 'minecraft:air')
    put(4, 0, 5, v['mound'][0])
    put(3, 0, 5, v['mound'][1])
    put(5, 0, 5, v['mound'][1])
    put(4, 1, 5, 'minecraft:lightning_rod', facing='up', powered='false', waterlogged='false')
    for y in (1, 2, 3):
        put(4, y, 1, 'minecraft:chiseled_stone_bricks')
    put(4, 4, 1, 'minecraft:stone_brick_slab', type='bottom', waterlogged='false')
    put(3, 1, 1, v['flank'])
    put(5, 1, 1, v['flank'])
    put(4, 1, 2, 'minecraft:chest', {'LootTable': (8, f'oborokage:chests/{name}')},
        facing='south', type='single', waterlogged='false')
    for (x, z) in [(2, 3), (6, 3), (3, 7), (5, 7)]:
        put(x, 1, z, v['candle'], candles=str(r.randint(1, 3)), lit='false', waterlogged='false')
    post_props = {'axis': 'y'} if v['post'].endswith('_log') else {}
    for (x, z) in [(0, 0), (8, 0), (0, 8), (8, 8)]:
        put(x, 1, z, v['post'], **post_props)
        put(x, 2, z, v['post'], **post_props)
        put(x, 3, z, 'minecraft:lantern', hanging='false', waterlogged='false')
    for (x, y, z), block, props in v.get('extra', []):
        put(x, y, z, block, **props)
    cover, how = v['cover']
    air = index[('minecraft:air', ())]
    for _ in range(10):
        x, z = r.randint(1, 7), r.randint(2, 8)
        if blocks[(x, 1, z)][0] != air:
            continue
        if how == 'petals':
            put(x, 1, z, cover, facing=r.choice(['north', 'south', 'east', 'west']),
                flower_amount=str(r.randint(1, 4)))
        elif how == 'bush':
            put(x, 0, z, 'minecraft:terracotta')       # a dead bush needs ground it can stand on
            put(x, 1, z, cover)
        else:
            put(x, 1, z, cover, **how)
    block_list = []
    for (x, y, z), (st, nbt) in sorted(blocks.items()):
        e = {'pos': (9, (3, [x, y, z])), 'state': (3, st)}
        if nbt:
            e['nbt'] = (10, nbt)
        block_list.append(e)
    root = {'DataVersion': (3, 4786), 'size': (9, (3, [9, 6, 9])), 'palette': (9, (10, palette)),
            'blocks': (9, (10, block_list)), 'entities': (9, (10, []))}
    data = bytes([10]) + tag_string('') + nbt_compound(root)
    out = DATA / f'structure/{name}/tomb.nbt'
    out.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb', mtime=0) as g:
        g.write(data)
    out.write_bytes(buf.getvalue())


def tomb_loot(line, name, weights):
    """The tomb's chest (create_data_v1) with its land's blade the more likely relic: half of them."""
    new = line == '26.3'
    rolls = (lambda n: n) if new else (lambda n: float(n))
    extras = [item('oborokage:intent_crystal', [count(1, 2, new)], 6, new),
              item('minecraft:flint', [count(1, 4, new)], 8, new),
              item('minecraft:iron_ingot', [count(1, 3, new)], 6, new),
              item('minecraft:gold_ingot', [count(1, 2, new)], 4, new),
              item('minecraft:amethyst_shard', [count(1, 3, new)], 5, new),
              item('minecraft:bone', [count(1, 3, new)], 6, new),
              item('minecraft:paper', [count(1, 2, new)], 4, new)]
    extra_rolls = {'type': 'minecraft:uniform', 'min': 2, 'max': 4} if new else \
        {'type': 'minecraft:uniform', 'min': 2.0, 'max': 4.0}
    others = [k for k in RELIC_KINDS if k not in weights]
    share, rest = divmod(sum(weights.values()), len(others))
    assert rest == 0, name
    relics = [item(f'oborokage:broken_{k}', weight=weights.get(k, share)) for k in RELIC_KINDS]
    write(overlay(line) / f'loot_table/chests/{name}.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': rolls(1), 'entries': relics},
        {'rolls': extra_rolls, 'entries': extras},
    ], 'random_sequence': f'oborokage:chests/{name}'})


def tomb_structure_json(line, name):
    # The steep lands (snow slopes, windswept hills) take the box adaptation the dojos have: the
    # 1.1.0 survey found the thin one left a quarter of the mountain tombs cut into a slope.
    new = line == '26.3'
    spawn = {'type': 'oborokage:blossom_oni', 'weight': 1}
    if new:
        spawn['count'] = 1
    else:
        spawn.update({'minCount': 1, 'maxCount': 1})
    write(overlay(line) / f'worldgen/structure/{name}.json', {
        'type': 'minecraft:jigsaw', 'biomes': f'#oborokage:has_structure/{name}',
        'max_distance_from_center': 16, 'project_start_to_heightmap': 'WORLD_SURFACE_WG',
        'size': 1, 'spawn_overrides': {'monster': {'bounding_box': 'piece', 'spawns': [spawn]}},
        'start_height': {'absolute': 0}, 'start_pool': f'oborokage:{name}/start',
        'step': 'surface_structures', 'terrain_adaptation': TOMB_VARIANTS[name].get('adapt', 'beard_thin'),
        'use_expansion_hack': False})


def tombs():
    for name, v in TOMB_VARIANTS.items():
        tomb_variant(name, v)
        # A stone base under it where the ground falls away (Foundation.java), in its land's stone.
        write(DATA / f'worldgen/processor_list/{name}_foundation.json', {'processors': [
            {'processor_type': 'oborokage:foundation', 'max_depth': 32, 'stones': v['base']}]})
        write(DATA / f'worldgen/template_pool/{name}/start.json', {
            'elements': [{'element': {'element_type': 'minecraft:single_pool_element',
                                      'location': f'oborokage:{name}/tomb',
                                      'processors': f'oborokage:{name}_foundation',
                                      'projection': 'rigid'},
                          'weight': 1}],
            'fallback': 'minecraft:empty'})
        write(DATA / f'tags/worldgen/biome/has_structure/{name}.json', {'values': v['biomes']})
    write(DATA / 'tags/worldgen/biome/has_structure/blade_tomb.json', {'values': TOMB_BIOMES})
    # One set for all five: each comes up only in its own lands, so the land picks the tomb.
    names = ['blade_tomb'] + list(TOMB_VARIANTS)
    write(DATA / 'worldgen/structure_set/blade_tomb.json', {
        'placement': {'type': 'minecraft:random_spread', 'salt': 1462731005, 'separation': 12,
                      'spacing': 36},
        'structures': [{'structure': f'oborokage:{n}', 'weight': 1} for n in names]})
    write(DATA / 'tags/worldgen/structure/tombs.json', {'values': [f'oborokage:{n}' for n in names]})
    write(DATA / 'advancement/four_tombs.json', {
        'parent': 'oborokage:first_moon',
        'display': {'icon': {'id': 'minecraft:chiseled_stone_bricks'},
                    'title': {'translate': 'advancement.oborokage.four_tombs.title'},
                    'description': {'translate': 'advancement.oborokage.four_tombs.description'},
                    'frame': 'goal', 'show_toast': True, 'announce_to_chat': True, 'hidden': True},
        'criteria': {'complete': {'trigger': 'minecraft:impossible'}},
        'requirements': [['complete']]})


# ------------------------------------------------------------------ the dojos

def worldgen():
    # 石垣: stone laid under the yard where the ground falls away (Foundation.java). The 1.1.0
    # survey found part of the yard in the air in one dojo in five. The ruin's base is mossier.
    for name, stones in [('dojo', ['minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:cobblestone',
                                   'minecraft:mossy_cobblestone', 'minecraft:andesite']),
                         ('ruined_dojo', ['minecraft:mossy_cobblestone', 'minecraft:cobblestone',
                                          'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks'])]:
        write(DATA / f'worldgen/processor_list/{name}_foundation.json', {'processors': [
            {'processor_type': 'oborokage:foundation', 'max_depth': 48, 'stones': stones}]})
        write(DATA / f'worldgen/template_pool/{name}/start.json', {
            'elements': [{'element': {'element_type': 'minecraft:single_pool_element',
                                      'location': f'oborokage:{name}/{name}',
                                      'processors': f'oborokage:{name}_foundation', 'projection': 'rigid'},
                          'weight': 1}],
            'fallback': 'minecraft:empty'})
    # One set for both: the two never overlap, and one dojo in four is a ruin.
    write(DATA / 'worldgen/structure_set/dojo.json', {
        'placement': {'type': 'minecraft:random_spread', 'salt': 1985061137, 'separation': SEPARATION,
                      'spacing': SPACING},
        'structures': [{'structure': 'oborokage:dojo', 'weight': 3},
                       {'structure': 'oborokage:ruined_dojo', 'weight': 1}]})
    write(DATA / 'tags/worldgen/biome/has_structure/ruined_dojo.json', {'values': ['minecraft:cherry_grove']})
    # The dojos: their masters clear what grows in under the hall's roof (Hall.java). The ruin alone:
    # where the 樱下鬼 rise under a roof at night (BlossomOni.checkSpawn).
    write(DATA / 'tags/worldgen/structure/dojos.json', {'values': ['oborokage:dojo', 'oborokage:ruined_dojo']})
    write(DATA / 'tags/worldgen/structure/ruined_dojo.json', {'values': ['oborokage:ruined_dojo']})


if __name__ == '__main__':
    tags()
    advancements()
    worldgen()
    ruined_dojo()
    tombs()
    for line in LINES:
        loot(line)
        ruined_loot(line)
        structure_json(line)
        for name, v in TOMB_VARIANTS.items():
            tomb_loot(line, name, v['relic'])
            tomb_structure_json(line, name)
    print('data written')
