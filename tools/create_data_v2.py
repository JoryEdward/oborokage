"""1.0.0-beta play-test round, data: the kazuuchi and the rebalanced blade recipes, the 樱下鬼's and 落英剑主's loot,
entity type tags, and the cherry-grove dojo (structure, set, pool, biome tag, chest loot and its NBT
template with the master, three swordsmen and two training posts placed in it).

Run after create_data_v1: python3 tools/create_data_v1.py && python3 tools/create_data_v2.py
"""
import gzip, io, json, random, struct, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from create_data_v1 import DATA, LINES, ROOT, count, item, looting, overlay, write  # noqa: E402

TACHI_BLADES = ['tachi', 'raikiri', 'frostmoon', 'voidstring']


# ------------------------------------------------------------------ recipes

def shaped(name, pattern, key, category='equipment'):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
        'key': key, 'result': {'id': f'oborokage:{name}'}})


def recipes():
    # The blank: two iron ingots and a stick on the diagonal (an iron sword stands upright).
    shaped('kazuuchi', ['  I', ' I ', 'S  '], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})
    # 胧影, the strongest blade, is forged from a kazuuchi with a nether star at its heart.
    shaped('tachi', ['DSD', 'NKN', 'ABA'], {
        'D': 'minecraft:diamond_block', 'S': 'minecraft:nether_star', 'N': 'minecraft:netherite_ingot',
        'K': 'oborokage:kazuuchi', 'A': 'minecraft:amethyst_block', 'B': 'minecraft:blaze_rod'})
    # The three themed long blades: a kazuuchi, three diamonds, one netherite ingot, two themes.
    themes = {'raikiri': ('minecraft:redstone_block', 'minecraft:lightning_rod'),
              'frostmoon': ('minecraft:blue_ice', 'minecraft:prismarine_crystals'),
              'voidstring': ('minecraft:ender_eye', 'minecraft:shulker_shell')}
    for blade, (x, y) in themes.items():
        shaped(blade, ['XDY', 'DKD', ' N '], {
            'X': x, 'Y': y, 'D': 'minecraft:diamond', 'K': 'oborokage:kazuuchi',
            'N': 'minecraft:netherite_ingot'})
    # 胧月 (the wakizashi): forged short; the other way is grinding a kazuuchi with six 剑魄.
    shaped('wakizashi', ['  D', ' D ', 'NA '], {
        'D': 'minecraft:diamond', 'N': 'minecraft:netherite_ingot', 'A': 'minecraft:amethyst_block'})


def unlocks(line):
    key = 'recipes' if line == '26.3' else 'recipe'
    triggers = {'kazuuchi': 'minecraft:iron_ingot', 'wakizashi': 'minecraft:netherite_ingot'}
    for blade in TACHI_BLADES:
        triggers[blade] = 'oborokage:kazuuchi'
    for recipe, trigger in triggers.items():
        write(overlay(line) / f'advancement/recipes/combat/{recipe}.json', {
            'parent': 'minecraft:recipes/root',
            'criteria': {
                'has_item': {'trigger': 'minecraft:inventory_changed',
                             'conditions': {'items': [{'items': trigger}]}},
                'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                                   'conditions': {key: f'oborokage:{recipe}'}}},
            'requirements': [['has_item', 'has_the_recipe']],
            'rewards': {'recipes': [f'oborokage:{recipe}']}})


# ------------------------------------------------------------------ loot

def chance(entry, p, new, looting_bonus=None):
    """Gate an entry on a random chance, raised per Looting level when looting_bonus is given."""
    if looting_bonus is None:
        cond = {'type' if new else 'condition': 'minecraft:random_chance', 'chance': p}
    else:
        cond = {'type' if new else 'condition': 'minecraft:random_chance_with_enchanted_bonus',
                'unenchanted_chance': p, 'enchantment': 'minecraft:looting',
                'enchanted_chance': {'type': 'minecraft:linear', 'base': round(p + looting_bonus, 4),
                                     'per_level_above_first': looting_bonus}}
    entry['condition' if new else 'conditions'] = cond if new else [cond]
    return entry


def loot(line):
    new = line == '26.3'
    lt = overlay(line) / 'loot_table'
    rolls = (lambda n: n) if new else (lambda n: float(n))
    write(lt / 'entities/blossom_oni.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': rolls(1), 'entries': [item('minecraft:bone', [count(0, 2, new), looting(new)], new=new)]},
        {'rolls': rolls(1), 'entries': [chance(item('oborokage:blade_soul'), .33, new, .1)]},
        {'rolls': rolls(1), 'entries': [chance(item('oborokage:intent_crystal'), .1, new)]},
    ], 'random_sequence': 'oborokage:entities/blossom_oni'})
    # The relic (7.9%, the broken form of its own blade) is dropped in code: PetalLord.
    write(lt / 'entities/petal_lord.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': rolls(1), 'entries': [item('oborokage:intent_crystal', [count(1, 3, new), looting(new)], new=new)]},
        {'rolls': rolls(1), 'entries': [item('oborokage:blade_soul', [count(2, 4, new), looting(new)], new=new)]},
    ], 'random_sequence': 'oborokage:entities/petal_lord'})
    uniform = (lambda a, b: {'type': 'minecraft:uniform', 'min': a, 'max': b}) if new else \
        (lambda a, b: {'type': 'minecraft:uniform', 'min': float(a), 'max': float(b)})
    entries = [item('oborokage:kazuuchi', weight=3),
               item('oborokage:blade_soul', [count(1, 3, new)], 8, new),
               item('oborokage:intent_crystal', [count(1, 2, new)], 3, new),
               item('minecraft:iron_ingot', [count(2, 5, new)], 10, new),
               item('minecraft:flint', [count(2, 6, new)], 8, new),
               item('minecraft:paper', [count(2, 6, new)], 6, new),
               item('minecraft:cherry_sapling', [count(1, 2, new)], 6, new),
               item('minecraft:pink_petals', [count(2, 8, new)], 6, new),
               item('minecraft:golden_apple', weight=2),
               item('minecraft:emerald', [count(1, 4, new)], 5, new)]
    entries += [item(f'oborokage:inscription_{k}', weight=1)
                for k in ['zanshin', 'focus', 'feather', 'dew', 'crimson']]
    write(lt / 'chests/dojo.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': uniform(4, 7), 'entries': entries}], 'random_sequence': 'oborokage:chests/dojo'})


# ------------------------------------------------------------------ tags

def tags():
    mc = ROOT / 'src/main/resources/data/minecraft/tags'
    write(mc / 'entity_type/undead.json', {'replace': False, 'values': ['oborokage:blossom_oni']})
    write(mc / 'entity_type/burn_in_daylight.json', {'replace': False, 'values': ['oborokage:blossom_oni']})
    # A ghost does not freeze, and drifts over powder snow instead of sinking into it.
    write(mc / 'entity_type/freeze_immune_entity_types.json', {'replace': False, 'values': ['oborokage:blossom_oni']})
    write(mc / 'entity_type/powder_snow_walkable_mobs.json', {'replace': False, 'values': ['oborokage:blossom_oni']})
    swords = json.loads((mc / 'item/swords.json').read_text())
    if 'oborokage:kazuuchi' not in swords['values']:
        swords['values'].insert(0, 'oborokage:kazuuchi')
    write(mc / 'item/swords.json', swords)
    # The master shrugs off the iaido's execution; its blows are capped in code instead.
    write(DATA / 'tags/entity_type/finisher_exempt.json', {
        'replace': False, 'values': ['oborokage:training_dummy', 'oborokage:petal_lord']})


# ------------------------------------------------------------------ the dojo

def structure_json(line):
    write(overlay(line) / 'worldgen/structure/dojo.json', {
        'type': 'minecraft:jigsaw', 'biomes': '#oborokage:has_structure/dojo',
        'max_distance_from_center': 16, 'project_start_to_heightmap': 'WORLD_SURFACE_WG',
        'size': 1, 'spawn_overrides': {}, 'start_height': {'absolute': 0},
        'start_pool': 'oborokage:dojo/start', 'step': 'surface_structures',
        'terrain_adaptation': 'beard_box', 'use_expansion_hack': False})


def worldgen():
    # 1.0.2: cherry groves are small (0.1-0.5% of the land); spacing 10 / separation 4 puts a dojo in
    # about 70% of groves 100 blocks across or more (survey over six seeds, docs/REVIEW_1.0.2.md).
    write(DATA / 'worldgen/structure_set/dojo.json', {
        'placement': {'type': 'minecraft:random_spread', 'salt': 1985061137, 'separation': 4,
                      'spacing': 10},
        'structures': [{'structure': 'oborokage:dojo', 'weight': 1}]})
    write(DATA / 'worldgen/template_pool/dojo/start.json', {
        'elements': [{'element': {'element_type': 'minecraft:single_pool_element',
                                  'location': 'oborokage:dojo/dojo',
                                  'processors': 'minecraft:empty', 'projection': 'rigid'},
                      'weight': 1}],
        'fallback': 'minecraft:empty'})
    write(DATA / 'tags/worldgen/biome/has_structure/dojo.json', {'values': ['minecraft:cherry_grove']})


# NBT: (type, value) pairs. 1 byte, 3 int, 5 float, 6 double, 8 string, 9 list (inner, items), 10 compound.
def tag_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def payload(t, v):
    if t == 1:
        return struct.pack('>b', v)
    if t == 3:
        return struct.pack('>i', v)
    if t == 5:
        return struct.pack('>f', v)
    if t == 6:
        return struct.pack('>d', v)
    if t == 8:
        return tag_string(v)
    if t == 10:
        return b''.join(bytes([kt]) + tag_string(k) + payload(kt, kv) for k, (kt, kv) in v.items()) + b'\0'
    if t == 9:
        inner, items = v
        return bytes([inner]) + struct.pack('>i', len(items)) + b''.join(payload(inner, i) for i in items)
    raise ValueError(t)


W, H, D = 17, 12, 17


def dojo():
    """A dojo on an andesite yard: a vermilion (mangrove) torii at the gate, a stone path, stone lanterns, a cherry
    hedge around the yard; the hall has paper walls on dark posts, a bamboo floor, a deepslate tile
    gable roof, and the master's chest between a whetstone and lanterns at the back."""
    r = random.Random(1985061137)
    palette, index, blocks = [], {}, {}

    def state(name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': (8, name)}
            if props:
                entry['Properties'] = (10, {k: (8, str(v)) for k, v in props.items()})
            palette.append(entry)
        return index[key]

    def put(x, y, z, name, nbt=None, **props):
        blocks[(x, y, z)] = (state(name, **props), nbt)

    stairs = lambda name, facing: dict(name=name, facing=facing, half='bottom', shape='straight', waterlogged='false')

    for x in range(W):
        for z in range(D):
            for y in range(1, H):
                put(x, y, z, 'minecraft:air')
            edge = x in (0, W - 1) or z in (0, D - 1)
            # Andesite and tuff read as a raked gravel yard but never fall, whatever is below.
            put(x, 0, z, 'minecraft:stone_bricks' if edge else
                ('minecraft:andesite' if r.random() < .8 else 'minecraft:tuff'))
    # The hedge around the yard, open at the gate.
    for x in range(W):
        for z in range(D):
            if (x in (0, W - 1) or z in (0, D - 1)) and not (z == 0 and 6 <= x <= 10) and z <= 7:
                put(x, 1, z, 'minecraft:cherry_leaves', distance=1, persistent='true', waterlogged='false')
    # The path from the gate to the hall steps.
    for z in range(0, 8):
        for x in range(7, 10):
            put(x, 0, z, 'minecraft:polished_andesite')
    # The torii.
    for x in (6, 10):
        for y in range(1, 5):
            put(x, y, 1, 'minecraft:stripped_mangrove_log', axis='y')
    for x in range(7, 10):
        put(x, 4, 1, 'minecraft:stripped_mangrove_log', axis='x')
    for x in range(5, 12):
        put(x, 5, 1, 'minecraft:stripped_mangrove_log', axis='x')
    for x in range(4, 13):
        put(x, 6, 1, 'minecraft:dark_oak_slab', type='bottom', waterlogged='false')
    # Stone lanterns either side of the path.
    for x in (4, 12):
        put(x, 1, 4, 'minecraft:stone_brick_wall', east='none', north='none', south='none',
            west='none', up='true', waterlogged='false')
        put(x, 2, 4, 'minecraft:lantern', hanging='false', waterlogged='false')
        put(x, 3, 4, 'minecraft:stone_brick_slab', type='bottom', waterlogged='false')
    # The hall: x 3..13, z 8..15, floor at y 1.
    for x in range(3, 14):
        for z in range(8, 16):
            put(x, 0, z, 'minecraft:stone_bricks')
            put(x, 1, z, 'minecraft:bamboo_mosaic')
            wall = x in (3, 13) or z in (8, 15)
            if not wall:
                continue
            post = (x in (3, 13) and z in (8, 11, 15)) or (z in (8, 15) and x in (3, 6, 10, 13))
            door = z == 8 and 7 <= x <= 9
            for y in range(2, 6):
                if door and y <= 4:
                    continue
                if post or y == 5:
                    put(x, y, z, 'minecraft:stripped_dark_oak_log', axis='y') if post and y < 5 else \
                        put(x, y, z, 'minecraft:dark_oak_planks')
                elif y == 2:
                    put(x, y, z, 'minecraft:dark_oak_planks')
                elif y == 3 and not door and (x + z) % 3 == 1:
                    put(x, y, z, 'minecraft:white_stained_glass')
                else:
                    put(x, y, z, 'minecraft:white_concrete')
    for x in range(4, 13):  # close the strip between the front and back walls and the eaves
        for z in (8, 15):
            put(x, 6, z, 'minecraft:dark_oak_planks')
    for x in range(7, 10):
        put(x, 1, 7, **stairs('minecraft:stone_brick_stairs', 'south'))
    # The roof: deepslate tiles over z 7..16, ridge at z 11..12, gables closed with planks.
    for z in range(7, 17):
        h = 6 + min(z - 7, 16 - z)
        for x in range(2, 15):
            if z in (11, 12):
                put(x, h, z, 'minecraft:deepslate_tiles')
            else:
                put(x, h, z, **stairs('minecraft:deepslate_tile_stairs', 'south' if z <= 10 else 'north'))
        if 8 <= z <= 15:
            for y in range(6, h):
                for x in (3, 13):
                    put(x, y, z, 'minecraft:dark_oak_planks')
    # Inside, at the back: the master's chest between a whetstone and lanterns.
    put(8, 2, 14, 'minecraft:chest', {'LootTable': (8, 'oborokage:chests/dojo')},
        facing='north', type='single', waterlogged='false')
    put(7, 2, 14, 'oborokage:whetstone')
    for x in (4, 12):
        for z in (9, 14):
            put(x, 2, z, 'minecraft:lantern', hanging='false', waterlogged='false')

    def entity(eid, x, y, z, yaw, persistent=True):
        nbt = {'id': (8, eid), 'Rotation': (9, (5, [float(yaw), 0.0]))}
        if persistent:
            nbt['PersistenceRequired'] = (1, 1)
        return {'pos': (9, (6, [x, y, z])), 'blockPos': (9, (3, [int(x), int(y), int(z)])),
                'nbt': (10, nbt)}

    # Yaw 180 faces the gate (template -z): the master looks out of the hall at whoever comes.
    entities = [entity('oborokage:petal_lord', 8.5, 2.0, 12.5, 180),
                entity('oborokage:swordsman', 4.5, 1.0, 2.5, 180),
                entity('oborokage:swordsman', 12.5, 1.0, 2.5, 180),
                entity('oborokage:swordsman', 8.5, 1.0, 5.5, 180),
                entity('oborokage:training_dummy', 3.5, 1.0, 6.5, 90, False),
                entity('oborokage:training_dummy', 13.5, 1.0, 6.5, 270, False)]
    block_list = []
    for (x, y, z), (s, nbt) in sorted(blocks.items()):
        e = {'pos': (9, (3, [x, y, z])), 'state': (3, s)}
        if nbt:
            e['nbt'] = (10, nbt)
        block_list.append(e)
    root = {'DataVersion': (3, 4786), 'size': (9, (3, [W, H, D])), 'palette': (9, (10, palette)),
            'blocks': (9, (10, block_list)), 'entities': (9, (10, entities))}
    data = bytes([10]) + tag_string('') + payload(10, root)
    out = DATA / 'structure/dojo/dojo.nbt'
    out.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb', mtime=0) as g:
        g.write(data)
    out.write_bytes(buf.getvalue())


if __name__ == '__main__':
    recipes()
    tags()
    worldgen()
    dojo()
    for line in LINES:
        loot(line)
        unlocks(line)
        structure_json(line)
    print('data written')
