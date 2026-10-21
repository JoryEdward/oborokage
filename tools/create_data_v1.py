"""1.0 data: recipes, loot tables, the blade tomb structure (with its NBT template) and recipe
unlocks. Loot tables, the structure definition and recipe advancements differ between 26.1/26.2
and 26.3, so those are written into each version line's overlay.

Run from the project root: python3 tools/create_data_v1.py
"""
import gzip, io, json, random, struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/oborokage'
LINES = ['26.1', '26.2', '26.3']
RELICS = ['tachi', 'wakizashi', 'raikiri', 'frostmoon', 'voidstring']


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')


def overlay(line):
    return ROOT / f'versions/{line}/main/resources/data/oborokage'


# ------------------------------------------------------------------ recipes (same on every line)

def recipes():
    r = DATA / 'recipe'
    write(r / 'whetstone.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': ['minecraft:grindstone', 'minecraft:flint', 'minecraft:flint'],
        'result': {'id': 'oborokage:whetstone'}})
    write(r / 'training_dummy.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': [' H ', 'SHS', ' S '],
        'key': {'H': 'minecraft:hay_block', 'S': 'minecraft:stick'},
        'result': {'id': 'oborokage:training_dummy'}})
    stones = {'zanshin': ['minecraft:ender_pearl'], 'focus': ['minecraft:lapis_block'],
              'feather': ['minecraft:phantom_membrane'], 'dew': ['minecraft:honey_bottle'],
              'crimson': ['minecraft:red_dye', 'minecraft:gold_ingot']}
    for key, extra in stones.items():
        crystals = ['oborokage:intent_crystal'] * (1 if key == 'crimson' else 2)
        write(r / f'inscription_{key}.json', {
            'type': 'minecraft:crafting_shapeless', 'category': 'misc',
            'ingredients': ['minecraft:smooth_stone'] + crystals + extra,
            'result': {'id': f'oborokage:inscription_{key}'}})
    theme = {'tachi': 'minecraft:amethyst_block', 'wakizashi': 'minecraft:amethyst_block',
             'raikiri': 'minecraft:redstone_block', 'frostmoon': 'minecraft:blue_ice',
             'voidstring': 'minecraft:ender_eye'}
    for relic in RELICS:
        write(r / f'restore_{relic}.json', {
            'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
            'ingredients': [f'oborokage:broken_{relic}', 'minecraft:netherite_ingot', theme[relic]],
            'result': {'id': f'oborokage:{relic}', 'components': {
                'oborokage:inscription': 'ancient',
                'minecraft:custom_model_data': {'strings': ['ancient']}}}})


# ------------------------------------------------------------------ loot tables

def count(lo, hi, new):
    c = {'type': 'minecraft:uniform', 'min': lo, 'max': hi}
    if new:
        return {'type': 'minecraft:set_count', 'count': c}
    return {'function': 'minecraft:set_count', 'count': c, 'add': False}


def looting(new):
    c = {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0}
    if new:
        return {'type': 'minecraft:enchanted_count_increase', 'count': c, 'enchantment': 'minecraft:looting'}
    return {'function': 'minecraft:enchanted_count_increase', 'count': c, 'enchantment': 'minecraft:looting'}


def item(name, mods=None, weight=None, new=False):
    e = {'type': 'minecraft:item', 'name': name}
    if mods:
        e['modifier' if new else 'functions'] = mods
    if weight:
        e['weight'] = weight
    return e


def loot(line):
    new = line == '26.3'
    lt = overlay(line) / 'loot_table'
    rolls = (lambda n: n) if new else (lambda n: float(n))
    explosion = {'type': 'minecraft:survives_explosion'} if new else [
        {'condition': 'minecraft:survives_explosion'}]
    block_pool = {'rolls': rolls(1), 'entries': [item('oborokage:whetstone')]}
    block_pool['condition' if new else 'conditions'] = explosion
    write(lt / 'blocks/whetstone.json', {'type': 'minecraft:block', 'pools': [block_pool],
                                         'random_sequence': 'oborokage:blocks/whetstone'})
    # After the 1.0.0-beta play test a swordsman drops only an intent crystal, 1% of the time, and no relics
    # (create_data_v2 writes the 樱下鬼's and 落英剑主's tables).
    crystal = item('oborokage:intent_crystal')
    crystal['condition' if new else 'conditions'] = (
        {'type': 'minecraft:random_chance', 'chance': .01} if new
        else [{'condition': 'minecraft:random_chance', 'chance': .01}])
    write(lt / 'entities/swordsman.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': rolls(1), 'entries': [crystal]},
    ], 'random_sequence': 'oborokage:entities/swordsman'})
    write(lt / 'entities/training_dummy.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': rolls(1), 'entries': [item('oborokage:training_dummy')]}],
        'random_sequence': 'oborokage:entities/training_dummy'})
    extras = [item('oborokage:intent_crystal', [count(1, 2, new)], 6, new),
              item('minecraft:flint', [count(1, 4, new)], 8, new),
              item('minecraft:iron_ingot', [count(1, 3, new)], 6, new),
              item('minecraft:gold_ingot', [count(1, 2, new)], 4, new),
              item('minecraft:amethyst_shard', [count(1, 3, new)], 5, new),
              item('minecraft:bone', [count(1, 3, new)], 6, new),
              item('minecraft:paper', [count(1, 2, new)], 4, new)]
    extra_rolls = {'type': 'minecraft:uniform', 'min': 2, 'max': 4} if new else \
        {'type': 'minecraft:uniform', 'min': 2.0, 'max': 4.0}
    write(lt / 'chests/blade_tomb.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': rolls(1), 'entries': [item(f'oborokage:broken_{r}') for r in RELICS]},
        {'rolls': extra_rolls, 'entries': extras},
    ], 'random_sequence': 'oborokage:chests/blade_tomb'})


# ------------------------------------------------------------------ recipe unlocks

def unlocks(line):
    key = 'recipes' if line == '26.3' else 'recipe'
    adv = overlay(line) / 'advancement/recipes/misc'
    triggers = {'whetstone': 'minecraft:flint', 'training_dummy': 'minecraft:hay_block'}
    for k in ['zanshin', 'focus', 'feather', 'dew', 'crimson']:
        triggers[f'inscription_{k}'] = 'oborokage:intent_crystal'
    for r in RELICS:
        triggers[f'restore_{r}'] = f'oborokage:broken_{r}'
    for recipe, trigger in triggers.items():
        write(adv / f'{recipe}.json', {
            'parent': 'minecraft:recipes/root',
            'criteria': {
                'has_item': {'trigger': 'minecraft:inventory_changed',
                             'conditions': {'items': [{'items': trigger}]}},
                'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                                   'conditions': {key: f'oborokage:{recipe}'}}},
            'requirements': [['has_item', 'has_the_recipe']],
            'rewards': {'recipes': [f'oborokage:{recipe}']}})


# ------------------------------------------------------------------ the blade tomb

def structure_json(line):
    new = line == '26.3'
    # The undead keep the tombs at night (swordsmen did before the play-test round).
    spawn = {'type': 'oborokage:blossom_oni', 'weight': 1}
    if new:
        spawn['count'] = 1
    else:
        spawn.update({'minCount': 1, 'maxCount': 1})
    write(overlay(line) / 'worldgen/structure/blade_tomb.json', {
        'type': 'minecraft:jigsaw', 'biomes': '#oborokage:has_structure/blade_tomb',
        'max_distance_from_center': 16, 'project_start_to_heightmap': 'WORLD_SURFACE_WG',
        'size': 1, 'spawn_overrides': {'monster': {'bounding_box': 'piece', 'spawns': [spawn]}},
        'start_height': {'absolute': 0}, 'start_pool': 'oborokage:blade_tomb/start',
        'step': 'surface_structures', 'terrain_adaptation': 'beard_thin', 'use_expansion_hack': False})


def worldgen():
    write(DATA / 'worldgen/structure_set/blade_tomb.json', {
        'placement': {'type': 'minecraft:random_spread', 'salt': 1462731005, 'separation': 12,
                      'spacing': 36},
        'structures': [{'structure': 'oborokage:blade_tomb', 'weight': 1}]})
    write(DATA / 'worldgen/template_pool/blade_tomb/start.json', {
        'elements': [{'element': {'element_type': 'minecraft:single_pool_element',
                                  'location': 'oborokage:blade_tomb/tomb',
                                  'processors': 'minecraft:empty', 'projection': 'rigid'},
                      'weight': 1}],
        'fallback': 'minecraft:empty'})
    write(DATA / 'tags/worldgen/biome/has_structure/blade_tomb.json', {'values': [
        '#minecraft:is_forest', '#minecraft:is_taiga', 'minecraft:plains',
        'minecraft:sunflower_plains', 'minecraft:cherry_grove', 'minecraft:meadow',
        'minecraft:savanna']})


# A minimal NBT writer for the structure template.
def tag_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def nbt_compound(d):
    out = b''
    for k, (t, v) in d.items():
        out += bytes([t]) + tag_string(k) + payload(t, v)
    return out + b'\0'


def payload(t, v):
    if t == 3:
        return struct.pack('>i', v)
    if t == 8:
        return tag_string(v)
    if t == 10:
        return nbt_compound(v)
    if t == 9:
        inner, items = v
        return bytes([inner]) + struct.pack('>i', len(items)) + b''.join(payload(inner, i) for i in items)
    raise ValueError(t)


def tomb():
    """A small shrine: a mossy stone floor, a stele with a chest of relics before it, a blade-like
    lightning rod driven into a mound, unlit candles and lanterns on the corner posts."""
    r = random.Random(1462731005)
    size = (9, 6, 9)
    palette, index = [], {}

    def state(name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': (8, name)}
            if props:
                entry['Properties'] = (10, {k: (8, v) for k, v in props.items()})
            palette.append(entry)
        return index[key]

    blocks = {}
    for x in range(9):
        for z in range(9):
            roll = r.random()
            name = 'minecraft:mossy_stone_bricks' if roll < .35 else \
                'minecraft:cracked_stone_bricks' if roll < .5 else 'minecraft:stone_bricks'
            blocks[(x, 0, z)] = (state(name), None)
            for y in range(1, 6):
                blocks[(x, y, z)] = (state('minecraft:air'), None)
    blocks[(4, 0, 5)] = (state('minecraft:moss_block'), None)
    blocks[(3, 0, 5)] = (state('minecraft:coarse_dirt'), None)
    blocks[(5, 0, 5)] = (state('minecraft:coarse_dirt'), None)
    blocks[(4, 1, 5)] = (state('minecraft:lightning_rod', facing='up', powered='false',
                               waterlogged='false'), None)
    for y in (1, 2, 3):
        blocks[(4, y, 1)] = (state('minecraft:chiseled_stone_bricks'), None)
    blocks[(4, 4, 1)] = (state('minecraft:stone_brick_slab', type='bottom', waterlogged='false'), None)
    blocks[(3, 1, 1)] = (state('minecraft:mossy_stone_bricks'), None)
    blocks[(5, 1, 1)] = (state('minecraft:mossy_stone_bricks'), None)
    blocks[(4, 1, 2)] = (state('minecraft:chest', facing='south', type='single', waterlogged='false'),
                         {'LootTable': (8, 'oborokage:chests/blade_tomb')})
    for (x, z) in [(2, 3), (6, 3), (3, 7), (5, 7)]:
        blocks[(x, 1, z)] = (state('minecraft:candle', candles=str(r.randint(1, 3)), lit='false',
                                   waterlogged='false'), None)
    for (x, z) in [(0, 0), (8, 0), (0, 8), (8, 8)]:
        blocks[(x, 1, z)] = (state('minecraft:mossy_cobblestone'), None)
        blocks[(x, 2, z)] = (state('minecraft:mossy_cobblestone'), None)
        blocks[(x, 3, z)] = (state('minecraft:lantern', hanging='false', waterlogged='false'), None)
    for _ in range(9):
        x, z = r.randint(1, 7), r.randint(2, 8)
        if blocks[(x, 1, z)][0] == index[('minecraft:air', ())]:
            blocks[(x, 1, z)] = (state('minecraft:moss_carpet'), None)
    block_list = []
    for (x, y, z), (s, nbt) in sorted(blocks.items()):
        entry = {'pos': (9, (3, [x, y, z])), 'state': (3, s)}
        if nbt:
            entry['nbt'] = (10, nbt)
        block_list.append(entry)
    root = {
        # 26.1's data version: newer lines upgrade the template with their own data fixers.
        'DataVersion': (3, 4786),
        'size': (9, (3, list(size))),
        'palette': (9, (10, palette)),
        'blocks': (9, (10, block_list)),
        'entities': (9, (10, [])),
    }
    data = bytes([10]) + tag_string('') + nbt_compound(root)
    out = DATA / 'structure/blade_tomb/tomb.nbt'
    out.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode='wb', mtime=0) as g:
        g.write(data)
    out.write_bytes(buf.getvalue())


if __name__ == '__main__':
    recipes()
    worldgen()
    tomb()
    for line in LINES:
        loot(line)
        unlocks(line)
        structure_json(line)
    print('data written')
