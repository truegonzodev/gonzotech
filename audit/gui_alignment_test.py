#!/usr/bin/env python3
"""Static production wiring + actual PNG geometry, not an in-game renderer test.
Requires Pillow. Prevent square/24-frame slicing and slot/texture drift from returning.
"""
from pathlib import Path
from fractions import Fraction
from PIL import Image
import json
import re

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/main/java/com/gonzotech'
GUI = ROOT / 'src/main/resources/assets/gonzotech/textures/gui'
checks = 0


def check(ok, message):
    global checks
    checks += 1
    assert ok, message


menu = (SRC / 'machines/menu/ChemicalPlantMenu.java').read_text()
screen = (SRC / 'machines/client/ChemicalPlantScreen.java').read_text()
fg = Image.open(GUI / 'third_chemical_plant_gui.png').convert('RGBA')
bg = Image.open(GUI / 'third_chemical_plant_gui_bg.png').convert('RGBA')
check(fg.size == bg.size == (512, 512), '512x512 author sheets')
slots = {int(i): (int(x)+128, int(y)+128) for i, x, y in
         re.findall(r'new Slot\(be, (\d+), (\d+), (\d+)\)', menu)}
grid = re.search(r'new Slot\(be, slotIndex, (\d+) \+ col \* 18, (\d+) \+ row \* 18\)', menu)
check(grid is not None, 'ingredient grid wiring')
gx, gy = map(int, grid.groups())
for row in range(3):
    for col in range(3):
        slots[3+row*3+col] = (128+gx+18*col, 128+gy+18*row)
expected = {0: (154,145), 1: (154,163), 2: (154,181), 12: (280,163)}
expected.update({3+row*3+col: (190+18*col,145+18*row) for row in range(3) for col in range(3)})
check(slots == expected, '13 machine slots, unchanged indices, exact author coordinates')
check(menu.count('return ChemicalPlantRecipes.isCatalyst(stack);') == 3, 'all catalysts still filtered')
check('new Slot(be, 12, 152, 35)' in menu and 'return false;' in menu, 'output cannot accept input')
check('addPlayerInventory(inv, 8, 84)' in menu, 'player inventory unchanged')
player_slots = [(136+18*c,212+18*r) for r in range(3) for c in range(9)]
player_slots += [(136+18*c,270) for c in range(9)]
for x,y in list(slots.values()) + player_slots:
    check(fg.crop((x,y,x+16,y+16)).getcolors() == [(256,(139,139,139,255))],
          f'item hitbox exactly covers the 16x16 interior at {x},{y}')
for prefix, expected_rect in [('gtu',(136,145,16,52)), ('prog',(244,163,34,16))]:
    values = []
    for dim in 'XYWH':
        value = re.search(rf'int {prefix}{dim} = (?:[xy] \+ )?(\d+);', screen)
        check(value is not None, f'{prefix}{dim} declaration')
        values.append(int(value[1]) + (128 if dim in 'XY' else 0))
    check(tuple(values) == expected_rect, f'{prefix} drawing rectangle')
    check(f'inRect(mouseX, mouseY, {prefix}X, {prefix}Y, {prefix}W, {prefix}H)' in screen,
          f'{prefix} tooltip covers entire gauge')
    x,y,w,h = values
    open_pixels = [(px,py) for py in range(y,y+h) for px in range(x,x+w)
                   if fg.getpixel((px,py))[3] <= 8]
    check(bool(open_pixels), f'{prefix} opening exists')
    check(min(px for px,py in open_pixels) == x and max(px for px,py in open_pixels) == x+w-1,
          f'{prefix} opening spans full bar width')
    check(all(bg.getpixel(p)[3] == 255 for p in open_pixels), f'{prefix} opaque backing')

client = (SRC / 'core/psyche/client/PsycheCrisisClient.java').read_text()
constants = {k:int(v) for k,v in re.findall(r'private static final int FRAME_(\w+) = (\d+);',client)}
w,h,count,ticks = (constants[k] for k in ('WIDTH','HEIGHT','COUNT','TICKS'))
strip = Image.open(GUI / 'crisis_screen_effect.png')
meta = json.loads((GUI / 'crisis_screen_effect.png.mcmeta').read_text())['animation']
check((w,h,count) == (128,72,23), 'approved 16:9 frame geometry')
check(w*9 == h*16 and strip.size == (w,h*count), 'UV denominators exactly match real PNG')
check((meta['width'],meta['height'],meta['frametime']) == (w,h,ticks), 'metadata agrees with renderer')
check(meta['frames'] == list(range(count)), 'all 23 frames, no frame 23/24')
check('frame = (frame + 1) % (FRAME_COUNT * FRAME_TICKS);' in client, 'whole animation cycle')
check('int v = (frame / FRAME_TICKS) * FRAME_HEIGHT;' in client, 'advance by frame height, not width')
check('FRAME_SIZE' not in client, 'no square-source assumption')
check('width / (float) FRAME_WIDTH, height / (float) FRAME_HEIGHT' in client, 'independent destination axes')
check('FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT * FRAME_COUNT' in client, 'source region/strip dimensions')
check('TEX_SCREEN_EFFECT, 0, 0, 0.0F, (float) v' in client, 'stationary full-screen destination')
# Model the source UVs of that verified blit signature over three complete cycles.
# Exact rational arithmetic checks pixel centres: no neighbouring frame can be sampled.
for tick in range(count*ticks*3):
    frame = (tick % (count*ticks)) // ticks
    v = frame*h
    check(0 <= v and v+h <= strip.height, 'frame stays inside strip')
    for px,py in [(0,0),(w-1,0),(w//2,h//2),(0,h-1),(w-1,h-1)]:
        u = Fraction(2*px+1,2*w)
        uv = Fraction(2*(v+py)+1,2*h*count)
        sampled = (int(u*strip.width),int(uv*strip.height))
        check(sampled == (px,v+py), 'exact texel, no vertical strip drift')
for dw,dh in [(1280,720),(1920,1080),(3840,2160)]:
    check(Fraction(dw,w) == Fraction(dh,h), 'square texels on 16:9 screens')
print(f'Chemical GUI / crisis frame geometry checks passed: {checks} (static + actual PNGs)')
