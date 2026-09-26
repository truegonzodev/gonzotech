#!/usr/bin/env python3
"""Actual filter core + GUI/resource wiring. Not an in-game NeoForge test.
Java 21 required (JAVA_HOME/PATH); alternatively Java 21 + ECJ_JAR. Pillow required.
"""
from pathlib import Path
from PIL import Image, ImageChops
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/main/java/com/gonzotech'
RES = ROOT / 'src/main/resources'
checks = 0

def check(ok, name):
    global checks
    checks += 1
    assert ok, name


def read(path):
    return (SRC / path).read_text()


def data(path):
    return json.loads((RES / path).read_text())


fg = Image.open(RES / 'assets/gonzotech/textures/gui/third_air_filter_gui.png').convert('RGBA')
bg = Image.open(RES / 'assets/gonzotech/textures/gui/third_air_filter_gui_bg.png').convert('RGBA')
check(fg.size == bg.size == (512, 512), '512 sheets')
for x, y, w, h in [(136, 145, 16, 52), (190, 145, 52, 16), (190, 181, 52, 16)]:
    opening = ImageChops.invert(fg.crop((x, y, x+w, y+h)).getchannel('A'))
    bounds = opening.getbbox()
    # Author's 2c1fb45 art has shaped fish openings, not placeholder rectangles.
    check(bounds is not None and bounds[0] == 0 and bounds[2] == w, 'gauge opening spans its full fill width')
    backing = bg.crop((x, y, x+w, y+h)).getchannel('A')
    check(all(backing.getpixel((px, py)) == 255 for py in range(h) for px in range(w)
              if opening.getpixel((px, py)) > 247), 'every visible gauge pixel has a background')
slots = [(x, 163) for x in (190, 208, 226)]
slots += [(136 + 18*c, 212 + 18*r) for r in range(3) for c in range(9)]
slots += [(136 + 18*c, 270) for c in range(9)]
for x, y in slots:
    check(fg.crop((x, y, x+16, y+16)).getcolors() == [(256, (139, 139, 139, 255))], '16x16 slot matches menu')
for bar in ('bar_air_1', 'bar_air_2'):
    check(Image.open(RES / f'assets/gonzotech/textures/gui/{bar}.png').size == (16, 16), '16x16 placeholder')
screen = read('machines/client/AirFilterScreen.java')
check('drawHBarTexRightToLeft' in screen and 'drawHBarTex(' in screen, 'opposite fill directions')
check(screen.count('inRect(mouseX, mouseY, x + 62, y + 17, 52, 16)') == 1, 'full upper hitbox')
check(screen.count('inRect(mouseX, mouseY, x + 62, y + 53, 52, 16)') == 1, 'full lower hitbox')
check('imageHeight = 184' not in screen and 'x + 40, y + 145' not in screen, 'no old upside-down layout/hitbox')
menu = read('machines/menu/AirFilterMenu.java')
check('62 + slot * 18, 35' in menu and 'addPlayerInventory(inventory, 8, 84)' in menu, 'machine above player')
be = read('cleanroom/AirFilterBlockEntity.java')
check('canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }' in be, 'automation input only')
check('FilterCycle.Step step = be.cycle.tick(' in be and 'step.energySpent()' in be, 'BE uses tested accounting')
check('intake.offer(' in be and 'FilterCycle.fromTicks(' in be, 'shared intake and exact persisted fuel ticks')
interior = set(data('data/gonzotech/tags/block/clean_room_interior.json')['values'])
check({'minecraft:heavy_weighted_pressure_plate', 'minecraft:light_weighted_pressure_plate', 'minecraft:light',
       'gonzotech:singular_energy_source', 'gonzotech:singular_heat_source'} <= interior, 'new interior blocks')
check(not interior.intersection(data('data/gonzotech/tags/block/clean_room_seal.json')['values']), 'interior does not become airtight shell')
variant = data('data/gonzotech/painting_variant/gonzo.json')
check((variant['width'], variant['height']) == (4, 5), '4x5 painting')
check(Image.open(RES / 'assets/gonzotech/textures/paintings/gonzo.png').size == (64, 80), 'original 64x80 art')
atlas = data('assets/minecraft/atlases/paintings.json')['sources'][0]
check(atlas['sprite'] == variant['asset_id'] and atlas['resource'] == 'gonzotech:paintings/gonzo', 'plural texture folder mapped into vanilla atlas')
check(data('assets/gonzotech/items/gonzo_painting.json')['model']['model'] == 'minecraft:item/painting', 'vanilla inventory model')
check('Rarity.UNCOMMON' in read('core/item/GonzoPaintingItem.java'), 'uncommon item')
check('GonzoPaintingMixin' in data('gonzotech.mixins.json')['mixins'], 'custom variant pick/drop hooks registered')
check('ModSounds.register(modEventBus)' in read('GonzoTechMod.java') and data('assets/gonzotech/sounds.json') == {}, 'future sounds wired, no missing fake sound')

home = os.environ.get('JAVA_HOME')
java = str(Path(home) / 'bin/java') if home else shutil.which('java')
javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file():
    raise SystemExit('Java 21 required: set JAVA_HOME or PATH')
with tempfile.TemporaryDirectory(prefix='gonzotech-air-filter-') as output:
    if os.environ.get('ECJ_JAR'):
        compiler = [java, '-jar', os.environ['ECJ_JAR'], '-21', '-proc:none']
    elif javac and Path(javac).is_file():
        compiler = [javac, '--release', '21']
    else:
        raise SystemExit('javac required, or Java 21 + ECJ_JAR')
    sources = [SRC / 'cleanroom/FilterCycle.java', SRC / 'cleanroom/FilterIntake.java', ROOT / 'audit/AirFilterSelfTest.java']
    subprocess.run(compiler + ['-d', output] + list(map(str, sources)), check=True)
    subprocess.run([java, '-cp', output, 'AirFilterSelfTest'], check=True)
print(f'Air filter / painting / sounds static checks passed: {checks}')
