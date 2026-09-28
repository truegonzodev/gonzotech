#!/usr/bin/env python3
"""Execute production bodies with small API stubs; NOT Minecraft or a full mod build.
Tests logout reset, gauge rectangles, successful consumption accounting, waste policy
and the actual filter drain transaction. Also checks recipe/preset/tag wiring.
"""
from pathlib import Path
import json, os, re, shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/main/java/com/gonzotech'
def read(p): return (SRC / p).read_text(encoding='utf-8')
def method(s, name):
    start = re.search(r'(?:public|private|protected) (?:static )?[\w<>]+ '+re.escape(name)+r'\(', s).start()
    end = s.index('{',start)+1; depth=1
    while depth:
        depth += (s[end]=='{')-(s[end]=='}'); end+=1
    return s[start:end]
client=read('core/psyche/client/PsycheCrisisClient.java')
gui=read('machines/client/MachineScreen.java')
account=read('core/item/ConsumptionAccounting.java')
waste=read('core/item/WasteProtection.java')
routing=read('machines/network/ItemFilterRouting.java')
first_scavenger=read('machines/network/ItemScavengerBlock.java')
second_scavenger=read('machines/network/SecondItemScavengerBlock.java')
assert 'extends ItemScavengerBlock' in second_scavenger
assert 'scavenger.isPowered(level, npos)' in routing
assert 'ClientPlayerNetworkEvent.LoggingOut' in client and 'PsycheCrisisClient.class' in read('GonzoTechMod.java')
assert 'mouseHandler' not in method(client,'onLogout')

# ── 0.3.32: сектора хитбоксов баков, белые разделители крана, дымка = 0.3.30 ──
# Дымка полностью откачена к 0.3.30: цельноэкранные текстура+дымка без scissor-зон,
# подписи шкал снова внутри PsycheHud.
assert 'enableScissor' not in client and 'paintEverywhereExcept' not in client
assert client.index('renderScreenEffect(g, width, height);') < client.index('renderFakeDeath(g, mc, width, height);'), 'haze before fake death'
psy=read('core/psyche/client/PsycheHud.java')
assert 'drawString' in psy, 'labels back inside PsycheHud'
assert not (SRC/'core/psyche/client/PsycheHudLabels.java').exists()
# Филлер: у каждого бака ровно два сектора (канал 4×52 и «брюшко» 12×14), без пиксельных масок.
filler=read('machines/client/FillerScreen.java')
assert 'NativeImage' not in filler and 'isOverSlot' not in filler and 'inGaugeHole' not in filler
assert 'inRect(mouseX, mouseY, leftTankX, barY, 4, barH)' in filler \
    and 'inRect(mouseX, mouseY, leftTankX + 4, barY + 19, 12, 14)' in filler \
    and 'inRect(mouseX, mouseY, rightTankX + 12, barY, 4, barH)' in filler \
    and 'inRect(mouseX, mouseY, rightTankX, barY + 19, 12, 14)' in filler
# Кран: разделители «:» и «/» белые, остальное — цвет шкалы; lang-ключи .name.
tap=read('machines/client/DispensingTapScreen.java')
assert 'literal(":")' in tap and 'literal(" /")' in tap and tap.count('withStyle(ChatFormatting.WHITE)') == 2
for lang in ['ru_ru','en_us']:
    langjson=json.loads((ROOT/f'src/main/resources/assets/gonzotech/lang/{lang}.json').read_text(encoding='utf-8'))
    assert 'gui.gonzotech.distillate.name' in langjson and 'gui.gonzotech.wort.name' in langjson
    assert 'gui.gonzotech.distillate.title' not in langjson and 'gui.gonzotech.wort.title' not in langjson
# ── 0.3.33: альт-житель — копия вилладжера без АИ + яйцо призыва ──
ent=read('core/registry/ModEntities.java')
assert 'MobCategory.MISC' in ent and 'sized(0.6F, 1.95F)' in ent and 'eyeHeight(1.62F)' in ent \
    and 'clientTrackingRange(10)' in ent, 'dimensions exactly like vanilla villager'
assert 'AltVillagerEntity::new' in ent and 'build(ResourceKey.create(Registries.ENTITY_TYPE' in ent
alt=read('core/entity/AltVillagerEntity.java')
assert 'extends PathfinderMob' in alt and 'removeWhenFarAway' in alt
alt_code=re.sub(r'/\*.*?\*/|//[^\n]*','',alt,flags=re.S)
for banned in ['Goal', 'Brain', 'goalSelector', 'registerGoals', 'SpawnPlacements']:
    assert banned not in alt_code, 'no AI by design: ' + banned
# 0.3.36: скин-варианты 70/15/11/4, рулетка в конструкторе = любой путь спавна.
assert 'SynchedEntityData.defineId(AltVillagerEntity.class, EntityDataSerializers.INT)' in alt_code
assert 'defineSynchedData(SynchedEntityData.Builder' in alt_code, 'variant synced to client'
assert 'weightedPick(this.getRandom())' in alt_code, 'roll in constructor covers egg/summon/spawner/natural'
assert 'putString("AltVariant", this.getVariant().name())' in alt_code, 'skin persists in NBT'
assert 'byName(tag.getString("AltVariant")' in alt_code, 'legacy saves reroll'
variant=read('core/entity/AltVariant.java')
variant_code=re.sub(r'/\*.*?\*/|//[^\n]*','',variant,flags=re.S)
for frag in ['ALT("alt_villager", 70)', 'ANOTHER_ALT("another_alt_villager", 15)',
             'NOT_ALT("not_alt_villager", 11)', 'SO_ALT("so_alt_villager", 4)']:
    assert frag in variant_code, 'author weights: ' + frag
assert 'random.nextInt(totalWeight())' in variant_code and 'textures/entity/alt/' in variant_code
model=read('core/client/AltVillagerModel.java')
for frag in ['head.yRot = state.yRot', 'head.xRot = state.xRot',
             'Mth.cos(state.walkAnimationPos * 0.6662F)', '* 1.4F * state.walkAnimationSpeed * 0.5F',
             '+ (float) Math.PI']:
    assert frag in model, 'villager animation: ' + frag
renderer=read('core/client/AltVillagerRenderer.java')
assert 'ModelLayers.VILLAGER' in renderer and '0.5F' in renderer, 'vanilla mesh + shadow'
assert 'extractRenderState' in renderer and 'state.texture = entity.getVariant().texture()' in renderer, 'variant texture into render state'
assert 'getTextureLocation(AltVillagerRenderState state)' in renderer
assert 'entity/alt_villager.png' not in renderer, 'texture comes from the variant, not a constant'
# 0.3.35: перекрёстные ссылки на классы мода обязаны иметь импорт
# (реальная сборка javac ловит пропущенный import — регресс-защита).
assert 'import com.gonzotech.core.entity.AltVillagerEntity;' in renderer, 'cross-package import must exist'
for src_name in ['core/client/AltVillagerClient.java']:
    src_text=read(src_name)
    assert 'com.gonzotech.core.registry.ModEntities' in src_text, 'qualified registry reference'
alt_client=read('core/client/AltVillagerClient.java')
assert 'RegisterRenderers' in alt_client and 'AltVillagerRenderer::new' in alt_client
alt_mod=read('GonzoTechMod.java')
assert 'AltVillagerClient::onRegisterRenderers' in alt_mod and 'EntityAttributeCreationEvent' in alt_mod \
    and 'Villager.createAttributes()' in alt_mod
alt_items=read('core/registry/ModItems.java')
assert 'SpawnEggItem(ModEntities.ALT.get(), props)' in alt_items

# ── 0.3.40: литографическая линия — defs, рецепты, полный гейт тира 3 ──
NEW_ITEMS=['chip_1','chip_2','chip_3','uv_lamp','chip_blank','photoresist','chip_blanky','chip_soup','third_silicon_factory']
for item_id in NEW_ITEMS:
    assert f'"{item_id}"' in alt_items, 'item def: ' + item_id
assert 'RUBBER_BLOCK_ITEM =\n        ITEMS.registerSimpleBlockItem("rubber_block", ModBlocks.RUBBER_BLOCK)' in alt_items
blocks_src=read('core/registry/ModBlocks.java')
assert '"rubber_block", componentBlockProperties()' in blocks_src
tabs_src=read('core/registry/ModCreativeTabs.java')
for item_id in NEW_ITEMS:
    assert f'ModItems.{item_id.upper()}.get()' in tabs_src, 'tab wiring: ' + item_id
assert 'RUBBER_BLOCK_ITEM.get()' in tabs_src
# Ресурсы: item-модели существуют, ссылаются на существующие текстуры.
for item_id in NEW_ITEMS:
    m=json.loads((ROOT/f'src/main/resources/assets/gonzotech/models/item/{item_id}.json').read_text())
    tex=m['textures']['layer0']
    assert (ROOT/f'src/main/resources/assets/gonzotech/textures/{tex.split("gonzotech:",1)[1]}.png').is_file(), 'texture for ' + item_id
# Блок резины: blockstate+model+loot+тег кирки.
assert json.loads((ROOT/'src/main/resources/assets/gonzotech/blockstates/rubber_block.json').read_text())
bm=json.loads((ROOT/'src/main/resources/assets/gonzotech/models/block/rubber_block.json').read_text())
assert bm['textures']['all']=='gonzotech:block/industry/rubber_block'
loot_rubber=json.loads((ROOT/'src/main/resources/data/gonzotech/loot_table/blocks/rubber_block.json').read_text())
assert loot_rubber['pools'][0]['entries'][0]['name']=='gonzotech:rubber_block'
assert '"gonzotech:rubber_block"' in (ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json').read_text()
# Рецепты: 14 файлов, все выводы/ингредиенты существуют как item-дефиниции.
EXPECTED_RECIPES=['uv_lamp','uv_lamp_from_gold_wire','uv_lamp_from_copper_wire','uv_lamp_from_aluminum_wire',
 'chip_blank','photoresist','chip_blanky','chip_soup','chip_soup_from_gold_wire','chip_soup_from_silver_wire',
 'rubber_block','third_silicon_factory','third_silicon_factory_from_gold_wire',
 'third_silicon_factory_from_silver_wire','third_silicon_factory_from_copper_wire']
recipe_dir=ROOT/'src/main/resources/data/gonzotech/recipe'
for recipe_id in EXPECTED_RECIPES:
    rp=recipe_dir/f'{recipe_id}.json'
    assert rp.is_file(), 'recipe: ' + recipe_id
    rd=json.loads(rp.read_text())
    # lang-существование выводов/ингредиентов проверяется ниже по словарю en_us.json.
tier3=read('machines/crafting/TierThreeCrafting.java')
tier3_code=re.sub(r'/\*.*?\*/|//[^\n]*','',tier3,flags=re.S)
# Список книги: все 15 id обязаны лежать внутри ОДНОГО List.of(...) до его ");" —
# разрыв списка посреди выражения делает файл некомпилируемым (0.3.40 hotfix).
blocks=re.findall(r'List\.of\(([^;]*?)\);', tier3_code, re.S)
assert any(all(f'"gonzotech:{rid}"' in b for rid in EXPECTED_RECIPES) for b in blocks), 'book list entries in one List.of'
GATED={**{i: i.upper() for i in NEW_ITEMS}, 'rubber_block': 'RUBBER_BLOCK_ITEM'}
chain=re.search(r'return item ==[^;]*;', tier3_code, re.S)
assert chain, 'isGatedOutput return chain'
for item_id, const in GATED.items():
    assert const + '.get()' in chain.group(0), 'craft gate: ' + item_id
# Цепочка — одно выражение: до закрывающей "}" метода не должно остаться висячих "|| item ==".
method_end=tier3_code.find('}', chain.end())
assert method_end != -1 and '|| item ==' not in tier3_code[chain.end():method_end], 'orphan || after return chain'
# Ингредиенты/выводы рецептов существуют как зарегистрированные предметы (по lang-ключам, не substring).
lang_en=json.loads((ROOT/'src/main/resources/assets/gonzotech/lang/en_us.json').read_text(encoding='utf-8'))
for recipe_id in EXPECTED_RECIPES:
    rd=json.loads((recipe_dir/f'{recipe_id}.json').read_text())
    outs=rd['result']['id'] if isinstance(rd['result'],dict) else rd['result']
    assert f'item.gonzotech.{outs.split(":")[1]}' in lang_en         or f'block.gonzotech.{outs.split(":")[1]}' in lang_en, 'lang for output: ' + outs
    ing=rd.get('ingredients') or list(rd['key'].values())
    for ing_id in ing:
        assert ing_id.startswith('minecraft:') or f'item.gonzotech.{ing_id.split(":")[1]}' in lang_en, 'lang for ingredient: ' + ing_id
# Item-дефиниции 1.21.4 обязаны существовать и указывать на модель.
for item_id in NEW_ITEMS+['rubber_block']:
    idef=json.loads((ROOT/f'src/main/resources/assets/gonzotech/items/{item_id}.json').read_text())
    model=idef['model']['model']
    assert (ROOT/f'src/main/resources/assets/gonzotech/models/item/{item_id}.json').is_file()
    assert model==f'gonzotech:item/{item_id}'

# ── 0.3.41: многоблочная литография — структура 3×3×2, UV 96×80, GUI ──
litho=ROOT/'src/main/java/com/gonzotech/machines/litho'
litho_struct=(litho/'SiliconFactoryStructure.java').read_text()
# Авторские матрицы: вариант 1 слой 1 DRD/RCR/DRD, слой 2 RBR/BSB/RBR; вариант 2 RD R/DCD/RDR + BRB/BSB/BRB;
# вариант 3 DDD/DCD/DDD + RBR/BSB/RBR. Слот = x + 3z + 9слой, корень (верхний центр) = 13.
assert "{'D', 'R', 'D', 'R', 'C', 'R', 'D', 'R', 'D', 'R', 'B', 'R', 'B', 'S', 'B', 'R', 'B', 'R'}" in litho_struct
assert "{'R', 'D', 'R', 'D', 'C', 'D', 'R', 'D', 'R', 'B', 'R', 'B', 'B', 'S', 'B', 'B', 'R', 'B'}" in litho_struct
assert "{'D', 'D', 'D', 'D', 'C', 'D', 'D', 'D', 'D', 'R', 'B', 'R', 'B', 'S', 'B', 'R', 'B', 'R'}" in litho_struct
assert 'SLOT_ROOT = 13' in litho_struct
litho_struct_code=re.sub(r'/\*.*?\*/|//[^\n]*','',litho_struct,flags=re.S)
# Материалы раскладок: D — алюминий/нержавейка/корпус; R — фарфор; C — резина; B — боросиликат; S — фабрика.
for marker in ('METAL_BLOCKS.get("aluminum_block")', 'METAL_BLOCKS.get("stainless_steel_block")',
               'ALUMINUM_HOUSING.get()', 'PORCELAIN.get()', 'RUBBER_BLOCK.get()',
               'BORE_STAINED_GLASS.get()', 'THIRD_SILICON_FACTORY.get()'):
    assert marker in litho_struct_code, 'layout material: ' + marker
# Оболочка — джокер (код != S) и участвует в EntityPlaceEvent-крюке.
assert "code != 'S'" in litho_struct_code and 'onBlockPlace(BlockEvent.EntityPlaceEvent' in litho_struct_code
# Сломанная оболочка выпадает оригиналом через loot оригинала; skipPos не восстанавливается.
assert 'shellBroken' in litho_struct_code and 'Block.dropResources(original, level, pos)' in litho_struct_code
assert 'if (skipPos != null && p.equals(skipPos)) continue;' in litho_struct_code
# Повторная form не переписывает оригиналы (guard на isFormed).
assert re.search(r'if \(controller\.isFormed\(\)\) \{[^}]*return;', litho_struct_code, re.S)
blocks_mods=(ROOT/'src/main/java/com/gonzotech/core/registry/ModBlocks.java').read_text()
assert '"third_silicon_factory", com.gonzotech.machines.litho.SiliconFactoryBlock::new' in blocks_mods
assert '"third_silicon_factory_shell"' in blocks_mods
items_mods=(ROOT/'src/main/java/com/gonzotech/core/registry/ModItems.java').read_text()
assert 'registerSimpleBlockItem("third_silicon_factory", ModBlocks.THIRD_SILICON_FACTORY)' in items_mods
menus_mods=(ROOT/'src/main/java/com/gonzotech/machines/registry/ModMenus.java').read_text()
assert 'MENUS.register("third_silicon_factory"' in menus_mods
bes_mods=(ROOT/'src/main/java/com/gonzotech/machines/registry/ModBlockEntities.java').read_text()
assert 'SiliconFactoryBlockEntity::new, false,' in bes_mods
litho_client=(ROOT/'src/main/java/com/gonzotech/machines/client/MachineClient.java').read_text()
assert 'ModMenus.SILICON_FACTORY.get(), SiliconFactoryScreen::new' in litho_client
main_mod=(ROOT/'src/main/java/com/gonzotech/GonzoTechMod.java').read_text()
assert 'SiliconFactoryStructure.clearAll()' in main_mod
assert 'NeoForge.EVENT_BUS.addListener(com.gonzotech.machines.litho.SiliconFactoryStructure::onBlockPlace)' in main_mod
# BE: NBT-цикл formed→restorePending; процесс-заглушка суп-набор → chip_<вариант>.
litho_be=(litho/'SiliconFactoryBlockEntity.java').read_text()
litho_be_code=re.sub(r'/\*.*?\*/|//[^\n]*','',litho_be,flags=re.S)
assert 'restorePending = true;' in litho_be_code and 'SiliconFactoryStructure.restoreController(server, be)' in litho_be_code
assert 'PROGRESS_TOTAL = 100' in litho_be_code and 'ModItems.CHIP_SOUP.get()' in litho_be_code
assert 'import com.gonzotech.core.registry.ModItems;' in litho_be
litho_menu=(ROOT/'src/main/java/com/gonzotech/machines/menu/SiliconFactoryMenu.java').read_text()
assert 'int x = 61 + i * 36;' in litho_menu and 'addSlot(new Slot(container, i, x, 17)' in litho_menu
assert 'int x = 43 + i * 36;' in litho_menu and 'x, 53)' in litho_menu
assert 'addPlayerInventory(inventory, 8, 84);' in litho_menu
assert 'stillValid(access, player, ModBlocks.THIRD_SILICON_FACTORY.get())' in litho_menu
litho_screen=(ROOT/'src/main/java/com/gonzotech/machines/client/SiliconFactoryScreen.java').read_text()
assert 'silicon_factory_chip_" + variant + "_gui.png' in litho_screen
assert 'silicon_factory_gui.png' in litho_screen
assert 'drawVBarTex(graphics, x + 8, y + 17, 16, 52,' in litho_screen
# Кросс-пакетные/событийные импорты фиксируем явно: ECJ без classpath их не ловит (0.3.41 hotfix).
assert 'import net.neoforged.neoforge.event.level.BlockEvent;' in litho_struct
assert 'import com.gonzotech.machines.menu.SiliconFactoryMenu;' in litho_be
assert 'import net.minecraft.world.level.block.Block;' in litho_be
assert 'renderComponentTooltip(font,' in litho_screen
# Ресурсы: blockstate фабрики (4 ключа) и оболочки (54), 54 срез-модели, UV-листы 96×80.
from PIL import Image as LithoImage
factory_bs=json.loads((ROOT/'src/main/resources/assets/gonzotech/blockstates/third_silicon_factory.json').read_text())
assert len(factory_bs['variants'])==4 and 'formed=true,variant=3' in factory_bs['variants']
shell_bs=json.loads((ROOT/'src/main/resources/assets/gonzotech/blockstates/third_silicon_factory_shell.json').read_text())
assert len(shell_bs['variants'])==54
for slot in range(18):
    for v in (1,2,3):
        mp=ROOT/f'src/main/resources/assets/gonzotech/models/block/third_silicon_factory/slice_{slot}_chip_{v}.json'
        mm=json.loads(mp.read_text())
        assert mm['textures']['sheet']==f'gonzotech:block/third/silicon_factory_chip_{v}_formed'
slice13=json.loads((ROOT/'src/main/resources/assets/gonzotech/models/block/third_silicon_factory/slice_13_chip_1.json').read_text())
assert slice13['elements'][0]['faces']['up']['uv']==[10.6667, 9.6, 13.3333, 12.8]
assert slice13['elements'][0]['faces']['down'].get('cullface')=='down'
for v in (1,2,3):
    tp=ROOT/f'src/main/resources/assets/gonzotech/textures/block/third/silicon_factory_chip_{v}_formed.png'
    with LithoImage.open(tp) as im:
        assert im.size==(96,80) and im.mode=='RGBA', tp.name
# Loot: фабрика дропает себя, оболочка — пустой файл.
factory_loot=json.loads((ROOT/'src/main/resources/data/gonzotech/loot_table/blocks/third_silicon_factory.json').read_text())
assert factory_loot['pools'][0]['entries'][0]['name']=='gonzotech:third_silicon_factory'
shell_loot=json.loads((ROOT/'src/main/resources/data/gonzotech/loot_table/blocks/third_silicon_factory_shell.json').read_text())
assert 'pools' not in shell_loot
# Чистая комната: размещаемые резина/нержавейка/алюминий; корпус уже был.
interior=json.loads((ROOT/'src/main/resources/data/gonzotech/tags/block/clean_room_interior.json').read_text())
for b in ('gonzotech:rubber_block','gonzotech:stainless_steel_block','gonzotech:aluminum_block','gonzotech:aluminum_housing'):
    assert b in interior['values'], 'clean room interior: ' + b
pickaxe_tag=json.loads((ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json').read_text())
assert 'gonzotech:third_silicon_factory' in pickaxe_tag['values']
# Lang: ключ фабрики переехал в block.*, ключ прогресса на месте, ru=en.
lang_ru=json.loads((ROOT/'src/main/resources/assets/gonzotech/lang/ru_ru.json').read_text(encoding='utf-8'))
assert 'block.gonzotech.third_silicon_factory' in lang_en and 'item.gonzotech.third_silicon_factory' not in lang_en
assert 'block.gonzotech.third_silicon_factory' in lang_ru and 'gui.gonzotech.silicon_factory.progress' in lang_ru
assert lang_en['gui.gonzotech.silicon_factory.progress'].endswith('%s%%') == lang_ru['gui.gonzotech.silicon_factory.progress'].endswith('%s%%')
eggdef=json.loads((ROOT/'src/main/resources/assets/gonzotech/items/alt_spawn_egg.json').read_text())
# 0.3.34: яйцо без тинтов — обычная полноцветная PNG-текстура.
assert eggdef['model']['model']=='gonzotech:item/alt_spawn_egg' and 'tints' not in eggdef['model']
eggmodel=json.loads((ROOT/'src/main/resources/assets/gonzotech/models/item/alt_spawn_egg.json').read_text())
assert eggmodel['parent']=='minecraft:item/generated' and eggmodel['textures']['layer0']=='gonzotech:item/alt_spawn_egg'
from PIL import Image
# 0.3.36: четыре скина 64×64 в entity/alt/; старый одиночный файл удалён.
alttex=Image.open(ROOT/'src/main/resources/assets/gonzotech/textures/entity/alt/alt_villager.png')
assert alttex.size==(64,64), 'standard 64x64 entity skin layout'
for alt_skin in ['alt_villager','another_alt_villager','not_alt_villager','so_alt_villager']:
    alt_img=Image.open(ROOT/f'src/main/resources/assets/gonzotech/textures/entity/alt/{alt_skin}.png')
    assert alt_img.size==(64,64), '64x64 skin: ' + alt_skin
assert not (ROOT/'src/main/resources/assets/gonzotech/textures/entity/alt_villager.png').exists(), 'old single skin removed'
eggtex=Image.open(ROOT/'src/main/resources/assets/gonzotech/textures/item/alt_spawn_egg.png')
assert eggtex.size==(16,16) and eggtex.getpixel((8,3))[3]==255, 'own full-colour egg png'
tabs=read('core/registry/ModCreativeTabs.java')
assert tabs.count('ALT_SPAWN_EGG.get()')==1
assert tabs.index('ALT_SPAWN_EGG.get()') > tabs.index('ModItems.CANISTER.get()'), 'egg in adaptations, next to buckets and canister'
for lang in ['ru_ru','en_us']:
    langjson=json.loads((ROOT/f'src/main/resources/assets/gonzotech/lang/{lang}.json').read_text(encoding='utf-8'))
    assert 'entity.gonzotech.alt' in langjson and 'item.gonzotech.alt_spawn_egg' in langjson
# Лут слизи: все шансы самородков /7.
loot=json.loads((ROOT/'src/main/resources/data/gonzotech/loot_table/blocks/radioactive_slime_block.json').read_text())
chances=[pool['conditions'][-1]['chance'] for pool in loot['pools'][1:]]
assert all(abs(c-e)<1e-12 for c,e in zip(chances,[0.5/7,0.5/7,0.3/7])), chances
# Тултип змеевика: «Охлаждение: <цвет>N mB/t» + серые «- Блок X льда: +N mB/t».
gtu=read('core/text/GtUnits.java'); snk=read('machines/client/SnaketypeCondenserScreen.java')
assert 'cooling_rate", num(value, WATER)' in gtu
for lang in ['ru_ru','en_us']:
    langjson=json.loads((ROOT/f'src/main/resources/assets/gonzotech/lang/{lang}.json').read_text(encoding='utf-8'))
    assert langjson['gui.gonzotech.condenser.cooling_rate'].count('%s')==1 and ' mB/t' in langjson['gui.gonzotech.condenser.cooling_rate']
    for key,mult in [('regular',1),('packed',3),('europan',7),('blue',12),('superdense',29)]:
        value=langjson[f'gui.gonzotech.condenser.ice_{key}']
        assert value.startswith('- ') and value.count('%d')==1 and '+%d mB/t' in value, (lang,key,value)
assert snk.count('.withStyle(ChatFormatting.GRAY)')>=6 and 'reg * 1)' in snk and 'sup * 29)' in snk
assert not (SRC/'machines/client/GuiMask.java').exists()
assert all(x not in gui for x in ['GuiMask','clipRect','NativeImage','getResourceManager'])
assert gui.index('blitSheet(g, backgroundTexture()') < gui.index('drawMachine(g, x, y') < gui.index('blitSheet(g, foregroundTexture()')
for name in ['core/item/DrinkItem','radiation/CysteamineItem','radiation/PentacinItem','radiation/DtpaItem','radiation/RadAbsorbentItem']:
    s=read(name+'.java'); body=method(s,'finishUsingItem')
    assert body.count('ConsumptionAccounting.record(serverPlayer, stack);') == 1
    assert body.index('instanceof ServerPlayer') < body.index('ConsumptionAccounting.record') < body.index('stack.shrink')
    assert 'super.finishUsingItem' not in body
    if name.endswith('DtpaItem'):
        assert body.index('hasEffect(ModEffects.TREATMENT_COURSE)') < body.index('ConsumptionAccounting.record')
    if name.endswith('DrinkItem'):
        assert body.index('ConsumptionAccounting.record') < body.index('instabuild') < body.index('createFilledResult')
registry=read('core/registry/ModItems.java')
assert registry.count('props.food(MASH_FOOD, MASH_CONSUMABLE)') == 2
assert 'ConsumptionAccounting' not in registry
assert '"waste_barrel", ModBlocks.WASTE_BARREL, new Item.Properties().fireResistant()' in registry
res=ROOT/'src/main/resources/data/gonzotech'
tag=json.loads((res/'tags/item/non_disposable.json').read_text())
assert tag == {'replace':False,'values':['gonzotech:waste_barrel']}
r=json.loads((res/'recipe/waste_barrel.json').read_text())
assert [[r['key'][x] for x in row] for row in r['pattern']] == [
 ['gonzotech:steel_plate','gonzotech:tungsten_ingot','gonzotech:steel_plate'],
 ['gonzotech:tellurium_ingot','gonzotech:plutonium_block','gonzotech:tellurium_ingot'],
 ['gonzotech:lead_ingot','gonzotech:tungsten_ingot','gonzotech:lead_ingot']]
assert r['result']=={'id':'gonzotech:waste_barrel','count':1}
assert 'Map.entry("waste_barrel", 0.7 * RadUnits.MILLI)' in read('radiation/RadSources.java')
assert 'Map.entry("waste_barrel", 2.3 * RadUnits.MILLI)' in read('radiation/ItemToxicity.java')


# Verify the common conversion endpoints used in the six-metal report.
for metal in ['radium','lithium','neodymium','rhenium','lead','bismuth']:
    for method_name in ['smelting','blasting','smoking']:
        recipe=json.loads((res/f'recipe/{metal}_ingot_from_{metal}_dust_{method_name}.json').read_text())
        assert recipe['ingredient']=='gonzotech:'+metal+'_dust'
        assert recipe['result']['id']=='gonzotech:'+metal+'_ingot'
        assert recipe['result'].get('count',1)==1
    recipe=json.loads((res/f'recipe/{metal}_ingot_from_nuggets.json').read_text())
    assert recipe['pattern']==['NNN']*3 and recipe['key']['N']=='gonzotech:'+metal+'_nugget'
    assert recipe['result']['count']==1
    recipe=json.loads((res/f'recipe/{metal}_nugget_from_{metal}_ingot.json').read_text())
    assert recipe['result']['count']==9
slime=json.loads((res/'loot_table/blocks/radioactive_slime_block.json').read_text())
radium_pool=next(pool for pool in slime['pools'] if pool['entries'][0]['name']=='gonzotech:radium_nugget')
# 0.3.31: шансы самородков /7 (Ra 1/14, U 1/14, Pu 3/70 до Fortune).
assert any(c.get('chance')==0.5/7 for c in radium_pool['conditions'])

harness='''
import java.util.*;
public class SecondPassHarness {
    static int checks;
    static void check(boolean ok) { checks++; if(!ok) throw new AssertionError("check "+checks); }
    static boolean fakeDeath, attackWasDown;
    static Object fakeDeathCause;
    static int lockTicks,frame;
    static float lockedYaw,lockedPitch;
    static boolean matches(ItemFilterBlockEntity be, ItemStack stack) { return be.matched; }
    public static void main(String[] args) {
        for(int i=0;i<100;i++) {
            fakeDeath=attackWasDown=true;fakeDeathCause=new Object();lockTicks=6;frame=42;lockedYaw=90;lockedPitch=45;
            PsycheNetwork.CLIENT_DATA=new Object();
            onLogout(new ClientPlayerNetworkEvent.LoggingOut());
            check(!fakeDeath && !attackWasDown && fakeDeathCause==null && lockTicks==0 && frame==0);
            check(lockedYaw==0 && lockedPitch==0 && PsycheNetwork.CLIENT_DATA==null);
            onLogout(new ClientPlayerNetworkEvent.LoggingOut());check(!fakeDeath); // idempotent, no mouse access
        }
        SecondPassHarness h=new SecondPassHarness();
        for(int x:new int[]{-128,0,31,128}) for(int y:new int[]{0,50,128})
        for(int w:new int[]{1,16,34,52}) for(int height:new int[]{1,16,52})
        for(float f:new float[]{-1,0,0.1f,0.5f,1,2}) for(int mode=0;mode<4;mode++) {
            GuiGraphics g=new GuiGraphics(); ResourceLocation tex=new ResourceLocation();
            switch(mode) {
                case 0 -> h.drawVBarTex(g,x,y,w,height,f,tex);
                case 1 -> h.drawHBarTex(g,x,y,w,height,f,tex);
                case 2 -> h.drawHBarTexRightToLeft(g,x,y,w,height,f,tex);
                case 3 -> h.drawHBarTexFull(g,x,y,w,height,f,tex);
            }
            int fill=Math.round(clamp01(f)*(mode==0?height:w));
            check(g.enables==(fill>0?1:0) && g.enables==g.disables);
            if(fill>0) {
                int[] expected= mode==0 ? new int[]{x,y+height-fill,x+w,y+height}
                    : mode==2 ? new int[]{x+w-fill,y,x+w,y+height} : new int[]{x,y,x+fill,y+height};
                check(Arrays.equals(expected,g.rect));check(g.blits>0);
            } else check(g.blits==0);
        }
        for(boolean drink:new boolean[]{false,true}) for(int n:new int[]{0,1,16}) {
            ServerPlayer p=new ServerPlayer();ItemStack stack=new ItemStack(new Item(false,drink),n);
            record(p,stack);
            check(p.stats==(n>0?1:0) && p.triggers==p.stats && p.events==p.stats);
            check(stack.count==n); // accounting never shrinks/creates a remainder
            if(n>0) { check(p.seenCount==n);check(p.lastEvent==(drink?GameEvent.DRINK:GameEvent.EAT)); }
        }
        for(boolean protectedItem:new boolean[]{false,true}) {
            ItemEntity item=new ItemEntity(new ItemStack(new Item(protectedItem,false),1));
            for(boolean initiallyInvulnerable:new boolean[]{false,true}) {
                EntityInvulnerabilityCheckEvent e=new EntityInvulnerabilityCheckEvent(item,initiallyInvulnerable);
                WasteProtection.onDamageCheck(e);check(e.invulnerable==(protectedItem||initiallyInvulnerable));
            }
            WasteProtection.onExpire(new ItemExpireEvent(item)); check(item.unlimited==protectedItem);
        }
        EntityInvulnerabilityCheckEvent normalEntity=new EntityInvulnerabilityCheckEvent(new Object(),false);
        WasteProtection.onDamageCheck(normalEntity);check(!normalEntity.invulnerable);
        for(boolean protectedItem:new boolean[]{false,true}) for(boolean matched:new boolean[]{false,true})
        for(boolean signal:new boolean[]{false,true}) for(boolean ignore:new boolean[]{false,true})
        for(FirstScavenger scavenger:new FirstScavenger[]{new FirstScavenger(),new SecondScavenger()}) {
            Level level=new Level();level.signal=signal;
            boolean powered=scavenger.isPowered(level,new BlockPos());
            check(powered==(scavenger instanceof SecondScavenger && signal));
            check(level.powerQueries==(scavenger instanceof SecondScavenger?1:0));
            Item item=new Item(protectedItem,false); Container source=new Container(new ItemStack(item,4));
            Container sink=new Container(new ItemStack(item,0)); ItemFilterBlockEntity be=new ItemFilterBlockEntity(matched);
            List<ItemRouting_Sink> sinks=List.of(new ItemRouting_Sink(sink,null,List.of(new BlockPos())));
            ChannelBudget pass=new ChannelBudget(10),reject=new ChannelBudget(10),total=new ChannelBudget(10);
            int moved=drainContainer(new Level(),new BlockPos(),be,null,source,sinks,sinks,
                powered?new BlockPos():null,pass,reject,total,0,0,ignore);
            boolean blocked=protectedItem&&!matched&&powered;
            check(moved==(blocked?0:4));check(source.getItem(0).count==(blocked?4:0));
            check(sink.getItem(0).count==(!blocked&&(matched||!powered)?4:0));
            check(source.changed==(blocked?0:4));check(total.remaining==(blocked?10:6));
        }
        // Protected slot does not starve ordinary junk in subsequent slots.
        Container mixed=new Container(new ItemStack(new Item(true,false),4),new ItemStack(new Item(false,false),4));
        int moved=drainContainer(new Level(),new BlockPos(),new ItemFilterBlockEntity(false),null,mixed,List.of(),List.of(),
            new BlockPos(),new ChannelBudget(10),new ChannelBudget(10),new ChannelBudget(10),0,0,false);
        check(moved==4 && mixed.getItem(0).count==4 && mixed.getItem(1).count==0);
        System.out.println("Second-pass production-body checks passed: "+checks+" (API stubs, not Minecraft)");
    }
'''
harness+='\n'.join(method(client,n) for n in ['onLogout'])
harness+='\n'+ '\n'.join(method(gui,n) for n in ['drawVBarTex','drawHBarTex','drawHBarTexRightToLeft','drawHBarTexFull','clamp01'])
harness+='\n'+method(account,'record')+'\n'+method(routing,'drainContainer')+'\n}\n'
harness+='class WasteProtection { static final Object NON_DISPOSABLE=new Object();\n'
harness+='\n'.join(method(waste,n) for n in ['isProtected','onDamageCheck','onExpire'])+'\n}\n'
harness+='''
class PsycheNetwork { static Object CLIENT_DATA; }
class ClientPlayerNetworkEvent { static class LoggingOut {} }
class ResourceLocation {}
class RenderType { static Object guiTextured(Object r) { return r; } }
class GuiGraphics {
    int enables,disables,blits; int[] rect;
    void enableScissor(int a,int b,int c,int d) { rect=new int[]{a,b,c,d};enables++; }
    void disableScissor() { disables++; }
    void blit(java.util.function.Function<Object,Object> type,ResourceLocation tex,int x,int y,float u,float v,int w,int h,int tw,int th) { blits++; }
}
class Item { boolean protectedItem,drink; Item(boolean p,boolean d) { protectedItem=p;drink=d; } }
class ItemStack {
    Item item;int count;
    ItemStack(Item i,int c) { item=i;count=c; }
    boolean isEmpty() { return count==0; }
    boolean is(Object tag) { return count>0 && item.protectedItem; }
    Item getItem() { return item; }
    ItemUseAnimation getUseAnimation() { return item.drink?ItemUseAnimation.DRINK:ItemUseAnimation.EAT; }
    ItemStack copy() { return new ItemStack(item,count); }
    void setCount(int n) { count=n; }
}
enum ItemUseAnimation { EAT,DRINK }
class GameEvent { static final Object EAT=new Object(), DRINK=new Object(); }
class ServerPlayer {
    int stats,triggers,events,seenCount;Object lastEvent;
    void awardStat(Object stat) { stats++; }
    void gameEvent(Object event) { events++;lastEvent=event; }
}
class Stats { static final Stats ITEM_USED=new Stats(); Object get(Item i) { return i; } }
class CriteriaTriggers {
    static final CriteriaTriggers CONSUME_ITEM=new CriteriaTriggers();
    void trigger(ServerPlayer p,ItemStack s) { p.triggers++;p.seenCount=s.count; }
}
class ItemEntity { ItemStack stack;boolean unlimited; ItemEntity(ItemStack s){stack=s;} ItemStack getItem(){return stack;} void setUnlimitedLifetime(){unlimited=true;} }
record ItemExpireEvent(ItemEntity getEntity) {}
class EntityInvulnerabilityCheckEvent {
    Object entity;boolean invulnerable;
    EntityInvulnerabilityCheckEvent(Object e,boolean i){entity=e;invulnerable=i;}
    Object getEntity(){return entity;}void setInvulnerable(boolean v){invulnerable=v;}
}
class Level { boolean signal;int powerQueries;boolean hasNeighborSignal(BlockPos pos){powerQueries++;return signal;} }
class BlockPos {}
class Direction {}
class ItemFilterBlockEntity { boolean matched;ItemFilterBlockEntity(boolean m){matched=m;} }
class Container {
    ItemStack[] items;int changed;
    Container(ItemStack... s){items=s;}
    ItemStack getItem(int i){return items[i];}
    void removeItem(int i,int n){items[i].count-=n;}
    void setChanged(){changed++;}
}
record ItemRouting_Sink(Container container,Direction face,List<BlockPos> path) {}
class ItemRouting {
    static int[] extractableSlots(Container c,Direction d){return java.util.stream.IntStream.range(0,c.items.length).toArray();}
    static boolean canTake(Container c,int slot,ItemStack s,Direction d){return true;}
    static boolean insertOne(Container c,Direction d,ItemStack s){c.items[0].count++;return true;}
}
class ChannelBudget {
    int remaining;ChannelBudget(int r){remaining=r;}boolean exhausted(){return remaining<=0;}
    boolean canMove(Item i){return remaining>0;}void record(Item i){remaining--;}
}
class ItemFlowTracker { static void record(Level l,BlockPos p,Item i,int n){} }
'''
harness+='class FirstScavenger {'+method(first_scavenger,'isPowered')+'}\n'
harness+='class SecondScavenger extends FirstScavenger {'+method(second_scavenger,'isPowered')+'}\n'
home=os.environ.get('JAVA_HOME')
java=str(Path(home)/'bin/java') if home else shutil.which('java')
javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file(): raise SystemExit('Java 21 required')
with tempfile.TemporaryDirectory(prefix='gonzotech-second-pass-') as tmp:
    source=Path(tmp)/'SecondPassHarness.java';source.write_text(harness,encoding='utf-8')
    compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
    subprocess.run(compiler+['-encoding','UTF-8','-d',tmp,str(source)],check=True)
    subprocess.run([java,'-cp',tmp,'SecondPassHarness'],check=True)
print('Consumption / mash / waste recipe, preset, tag and render-order wiring passed (static)')
