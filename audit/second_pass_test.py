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
    if item_id == 'third_silicon_factory': continue  # 0.3.55: блоковая модель, пин ниже
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
    if item_id == 'third_silicon_factory':  # 0.3.55: указывает на блоковую модель (3D)
        assert model=='gonzotech:block/third_silicon_factory'
        continue
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
# Сломанная оболочка (0.3.49): лут НЕ выпадает, сломанная позиция восстанавливается
# оригиналом (skipPos=null) — комнаты не теряют качество при деинициализации.
assert 'shellBroken' in litho_struct_code and 'Block.dropResources' not in litho_struct_code
assert '// skipPos = null: сломанная позиция тоже восстанавливается оригиналом.' in litho_struct
assert 'if (skipPos != null && p.equals(skipPos)) continue;' in litho_struct_code
# ── 0.3.49: сохранение класса ячеек при деинициализации ──
assert 'DEINIT_KIND_PRESERVED' in litho_struct_code
assert 'private static final Map<Long, RoomTopology.Kind> LAST_ORIGINAL_KIND = new HashMap<>();' in litho_struct
assert 'LAST_ORIGINAL_KIND.put(controller.memberPos(i).asLong(),' in litho_struct_code
assert 'LAST_ORIGINAL_KIND.put(memberPos[i].asLong(), CleanRoomDetector.kind(originalStates[i]));' in litho_struct_code
assert 'public static RoomTopology.Kind deinitKindOverrideAt(ServerLevel level, BlockPos pos)' in litho_struct_code
assert 'public static RoomTopology.Kind shellKindWithoutProxy(BlockPos pos)' in litho_struct_code
litho_detector=(ROOT/'src/main/java/com/gonzotech/cleanroom/CleanRoomDetector.java').read_text()
assert 'SiliconFactoryStructure.shellKindWithoutProxy(pos)' in litho_detector
assert 'SiliconFactoryStructure.deinitKindOverrideAt(level, pos)' in litho_detector
# ── 0.3.48: страж ре-ентерабельности — свапы form/invalidate не ломают структуру ──
assert 'if (SUPPRESSED.contains(brokenPos.asLong())) return;' in litho_struct_code
assert 'if (SUPPRESSED.contains(pos.asLong())) return; // программная перестановка, см. partRemoved' in litho_struct
# Повторная form не переписывает оригиналы (guard на isFormed).
assert re.search(r'if \(controller\.isFormed\(\)\) \{[^}]*return;', litho_struct_code, re.S)
# Поворот на 90° принимается (вариант 2 неинвариантен: колонки B вдоль Z и вдоль X).
assert 'private static int rotatedSlot(int slot)' in litho_struct_code
assert 'for (int rotation = 0; rotation < 2; rotation++)' in litho_struct_code
assert 'int expected = rotation == 0 ? slot : rotatedSlot(slot);' in litho_struct_code
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
# BE: NBT-цикл formed→restorePending; конвейер чипа по ТЗ автора (28.09.2026, 0.3.42).
litho_be=(litho/'SiliconFactoryBlockEntity.java').read_text()
litho_be_code=re.sub(r'/\*.*?\*/|//[^\n]*','',litho_be,flags=re.S)
assert 'restorePending = true;' in litho_be_code and 'SiliconFactoryStructure.restoreController(server, be)' in litho_be_code
assert 'ModItems.CHIP_SOUP.get()' in litho_be_code
assert 'import com.gonzotech.core.registry.ModItems;' in litho_be
# 0.3.46: суп расходуется по одному (было set(EMPTY) — съедало стак); один чип в полёте.
assert 'be.items.get(INPUT_SLOT).shrink(1);' in litho_be_code
assert 'be.items.set(INPUT_SLOT, ItemStack.EMPTY)' not in litho_be_code
assert 'be.items.get(TRANSIT_FIRST).isEmpty()' in litho_be_code and 'be.items.get(TRANSIT_LAST).isEmpty()' in litho_be_code
# Автоматизация: WorldlyContainer — вставка только суп в слот 0, извлечение только чипы+кремень.
assert 'implements WorldlyContainer, MenuProvider, GtuSink' in litho_be_code
assert 'public boolean canPlaceItem(int slot, ItemStack stack)' in litho_be_code
assert 'slot == OUTPUT_SLOT || slot >= SLAG_BASE' in litho_be_code
assert 'import net.minecraft.core.Direction;' in litho_be
# Прокси-контейнер оболочки: трубы/воронки цепляются к любой части структуры.
litho_shell_be=(ROOT/'src/main/java/com/gonzotech/machines/litho/SiliconFactoryShellBlockEntity.java')
assert litho_shell_be.is_file()
shell_be_code=re.sub(r'/\*.*?\*/|//[^\n]*','',litho_shell_be.read_text(),flags=re.S)
assert 'implements WorldlyContainer' in shell_be_code
assert 'SiliconFactoryStructure.controllerAt(server, worldPosition)' in shell_be_code
assert 'be.canTakeItemThroughFace(slot, stack, side)' in shell_be_code
litho_shell_block=(ROOT/'src/main/java/com/gonzotech/machines/litho/SiliconFactoryShellBlock.java').read_text()
assert 'implements EntityBlock' in litho_shell_block
assert 'new SiliconFactoryShellBlockEntity(pos, state)' in litho_shell_block
assert 'SiliconFactoryShellBlockEntity::new, false,' in bes_mods
# Цифры автора: хранение 29086, приём 322 GTU/сек, течение 3.8 GTU/t, скачки 28 GTU/9т, потеря 0.003 GTU/t.
assert 'CAPACITY_MILLI = 6_204_000L' in litho_be_code  # 0.3.64 ребаланс
assert 'INTAKE_MILLI_PER_TICK = 96_000L' in litho_be_code  # 0.3.64 ребаланс
assert 'RUN_MILLI_PER_TICK = 3_800L' in litho_be_code
assert 'SPIKE_MILLI = 28_000L' in litho_be_code and 'SPIKE_INTERVAL_TICKS = 9' in litho_be_code
assert 'IDLE_MILLI_PER_TICK = 3L' in litho_be_code
assert 'STEP_TICKS = {180, 320, 90}' in litho_be_code
# Конвейер: вход только суп, транзит 1/2, выход; брак → flint в шлак-слот шага.
assert 'INPUT_SLOT = 0' in litho_be_code and 'TRANSIT_FIRST = 1' in litho_be_code
assert 'OUTPUT_SLOT = 3' in litho_be_code and 'SLAG_BASE = 4' in litho_be_code
assert 'new ItemStack(Items.FLINT)' in litho_be_code and 'import net.minecraft.world.item.Items;' in litho_be
# Шанс брака: 100→1, 50→9, 10→24, 0→36, вне контура → 26 (линейно между точками).
assert 'if (qualityPercent < 0) return 26.0;' in litho_be_code
assert 'if (qualityPercent >= 100) return 1.0;' in litho_be_code
assert 'return 9.0 + (qualityPercent - 50) * (1.0 - 9.0) / 50.0;' in litho_be_code
assert 'return 24.0 + (qualityPercent - 10) * (9.0 - 24.0) / 40.0;' in litho_be_code
assert 'return 36.0 + qualityPercent * (24.0 - 36.0) / 10.0;' in litho_be_code
# Энергия списывается только при наличии запаса; скачок — на каждом 9-м тике третьего бара.
assert 'if (!be.gtu.has(need)) return;' in litho_be_code
assert '(be.progress + 1) % SPIKE_INTERVAL_TICKS == 0' in litho_be_code
# Брак на шагах 0/1 завершает цепочку (step=-1) — иначе фабрика фармила бы flint на пустом шаге.
assert litho_be.count('be.step = -1; // заготовка пропала') == 2
assert 'be.room = roomAround(server, be);' in litho_be_code
# 0.3.53: распад обнуляет контроллер по образцу турбины/парогена; структура
# не зовёт clearFormed напрямую — только полный clearStructure.
assert 'public void clearStructure()' in litho_be_code
assert 'items.clear();' in litho_be_code and 'gtu.set(0L);' in litho_be_code
assert 'step = -1;' in litho_be_code and 'clearFormed();' in litho_be_code
assert 'controller.clearStructure();' in litho_struct_code
assert 'controller.clearFormed();' not in litho_struct_code
# 0.3.54 (решение автора): начинка при распаде ВЫПАДАЕТ предметами у контроллера.
# Импорт Containers закреплён строкой — его потеря уже ломала сборку (0.3.52).
assert 'import net.minecraft.world.Containers;' in litho_be
assert 'Containers.dropItemStack(server, worldPosition.getX() + 0.5,' in litho_be_code
assert 'if (level instanceof ServerLevel server && !isRemoved()) {' in litho_be_code
assert "gtu.set(0L);" in litho_be_code
# 0.3.52 hotfix: java.util-импорты roomAround — один раз уже потеряны при
# перезаписи окружения (реальный фейл javac «cannot find symbol: Set/HashSet»).
assert 'import java.util.HashSet;' in litho_be
assert 'import java.util.Set;' in litho_be
# 0.3.55: заливка из клетки контроллера + проверка «покинула ли коробку».
assert 'java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();' in litho_be_code
assert 'if (kind == RoomTopology.Kind.FORBIDDEN) return null; // открытый воздух' in litho_be
assert 'if (seen.size() > 4096) return null; // защита от гигантских полостей' in litho_be
assert 'if (entry == null) return null; // заливка не покинула коробку — станок замурован' in litho_be
assert 'return CleanRoomSystem.room(server, entry);' in litho_be_code
# ── 0.3.59: лор «Потери: N GTU/блок» у проводов/теплотруб (импорты закреплены
# строкой — кросс-пакетные импорты уже терялись молча) ──
pipe_loss_tooltip=(ROOT/'src/main/java/com/gonzotech/core/client/PipeLossTooltip.java').read_text()
for pin in ('import com.gonzotech.machines.network.PipeBlock;',
            'import com.gonzotech.machines.network.PipeLoss;',
            'import com.gonzotech.machines.network.PipeType;',
            'import com.gonzotech.machines.network.SecondTierPipe;',
            'if (!(block instanceof PipeBlock pipe)) return;',
            'if (type != PipeType.WIRE && type != PipeType.HEAT) return;',
            'long milli = PipeLoss.perCell(block instanceof SecondTierPipe, type == PipeType.HEAT);',
            '"tooltip.gonzotech.pipe_loss_heat" : "tooltip.gonzotech.pipe_loss",'):
    assert pin in pipe_loss_tooltip, 'pipe loss tooltip pin: '+pin
universal_tooltip=(ROOT/'src/main/java/com/gonzotech/core/client/UniversalTooltip.java').read_text()
assert 'PipeLossTooltip.append(event);' in universal_tooltip
lang_ru=json.loads((ROOT/'src/main/resources/assets/gonzotech/lang/ru_ru.json').read_text())
lang_en=json.loads((ROOT/'src/main/resources/assets/gonzotech/lang/en_us.json').read_text())
assert lang_ru['tooltip.gonzotech.pipe_loss']=='Потери: %s GTU/блок'
assert lang_ru['tooltip.gonzotech.pipe_loss_heat']=='Потери: %s GTH/блок'
assert lang_en['tooltip.gonzotech.pipe_loss']=='Loss: %s GTU/block'
assert lang_en['tooltip.gonzotech.pipe_loss_heat']=='Loss: %s GTH/block'
assert len(lang_ru)==len(lang_en)

# ── 0.3.58: потери GTU/GTH за блок проноса (числа автора 28.09.2026) ──
pipe_loss=(ROOT/'src/main/java/com/gonzotech/machines/network/PipeLoss.java').read_text()
pipe_loss_code=re.sub(r'/\*.*?\*/|//[^\n]*','',pipe_loss,flags=re.S)
for const in ('WIRE_T1 = 80;','WIRE_T2 = 90;','HEAT_T1 = 220;','HEAT_T2 = 180;'):
    assert const in pipe_loss_code, 'pipe loss const: '+const
pipe_routing=(ROOT/'src/main/java/com/gonzotech/machines/network/PipeRouting.java').read_text()
pipe_routing_code=re.sub(r'/\*.*?\*/|//[^\n]*','',pipe_routing,flags=re.S)
for pin in ('PipeLoss.delivered(amount, lossMilli);','PipeLoss.flow(accepted, lossMilli);',
            'private static long pathLoss(Level level, List<PathStep> path, PipeType type)',
            'if (st.getBlock() instanceof UniversalNodeBlock) continue;',
            # 0.3.110: экранированная семья эпохи 3 — потери за клетку ×0.88
            'Math.round(PipeLoss.perCell(true, heatType) * ThirdTierPipe.STAT_FACTOR)',
            'lanes.add(new Lane(raw, null, 0, pos));'):
    assert pin in pipe_routing_code, 'routing loss pin: '+pin
assert pipe_routing_code.count('pathLossCells(level, path,') == 3  # 0.3.90: 2 билдера дорожек + обёртка pathLoss

# ── 0.3.61: дюп дверей закрыт; открытая гермодверь = динамическая утечка ──
heavy=(ROOT/'src/main/java/com/gonzotech/core/block/HeavyDoorBlock.java').read_text()
heavy_code=re.sub(r'/\*.*?\*/|//[^\n]*','',heavy,flags=re.S)
# дюп: вторая половина снимается молча ВСЕГДА (не только креатив/без инструмента)
assert 'BlockState twin = twinState(level, pos, state);' in heavy_code
assert 'Block.dropResources(twin, level, pos);' in heavy_code
assert 'protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params)' in heavy_code
assert 'return List.of();' in heavy_code
# симметричное снятие пары (верх↔низ)
assert 'BlockPos otherPos = upper ? pos.below() : pos.above();' in heavy_code
# утечка: чистый класс + проводка; топология НЕ рвётся (дверь остаётся герметиком)
door_leaks=(ROOT/'src/main/java/com/gonzotech/cleanroom/DoorLeaks.java').read_text()
assert 'OUTSIDE_LOSS_PER_SECOND = 8.0;' in door_leaks
assert 'EQUALIZE_PER_SECOND = 0.08;' in door_leaks
clean_system=(ROOT/'src/main/java/com/gonzotech/cleanroom/CleanRoomSystem.java').read_text()
clean_system_code=re.sub(r'/\*.*?\*/|//[^\n]*','',clean_system,flags=re.S)
for pin in ('processDoorLeaks(level);','DoorLeaks.settle(ledger, doors);',
            'doors.add(new DoorLeaks.Door(first, second));   // дверь между двумя контурами',
            'doors.add(new DoorLeaks.Door(first, outside ? null : first));'):
    assert pin in clean_system, 'door leak pin: '+pin  # пины с // — по сырому тексту
clean_detector=(ROOT/'src/main/java/com/gonzotech/cleanroom/CleanRoomDetector.java').read_text()
assert 'public static boolean isOpenHermeticDoor(BlockState state)' in clean_detector
assert 'if (side == null) { outside = true; continue; }' in clean_system_code  # 0.3.63: воздух вне контуров = улица
assert 'RoomTopology.Kind.FORBIDDEN' not in clean_system_code  # стены/жидкости стороной не считаются
assert 'HeavyDoorBlock.OPEN' in clean_detector

# ── 0.3.64: ВЕЛИКИЙ РЕБАЛАНС — авторские числа (всё ненаписанное не тронуто) ──
mdefs=(ROOT/'src/main/java/com/gonzotech/machines/energy/MachineDefs.java').read_text()
s2defs=(ROOT/'src/main/java/com/gonzotech/machines/energy/SecondTierDefs.java').read_text()
nuke=(ROOT/'src/main/java/com/gonzotech/machines/energy/NuclearDefs.java').read_text()
ptype=(ROOT/'src/main/java/com/gonzotech/machines/network/PipeType.java').read_text()
for pin in ("FIREBOX_GTH_CAPACITY = 16_004 * MILLI;","BOILER_GTH_CAPACITY = 16_004 * MILLI;",
    "BOILER_GTH_INTAKE = 58 * MILLI;","BOILER_WATER_INTAKE = 82;","BOILER_WATER_CAPACITY = 8_000;",
    "BOILER_STEAM_OUTPUT = 82;","STIRLING_GTU_CAPACITY = 122 * MILLI;","STIRLING_GTU_OUTPUT = 32 * MILLI;",
    "STIRLING_STEAM_CAPACITY = 6_000;","ELECTRIC_GTU_CAPACITY = 3_648 * MILLI;","ELECTRIC_GTU_INTAKE = 24 * MILLI;",
    "ACCUMULATOR_GTU_INTAKE = 32 * MILLI;","ACCUMULATOR_GTU_OUTPUT = 32 * MILLI;",
    "PUMP_GTU_CAPACITY = 362 * MILLI;","PUMP_GTU_INTAKE = 24 * MILLI;",
    "COBBLE_GTU_CAPACITY = 362 * MILLI;","COBBLE_GTU_INTAKE = 24 * MILLI;",
    "CRUSHER_GTU_CAPACITY = 5_202 * MILLI;","CRUSHER_GTU_INTAKE = 48 * MILLI;",
    "CRUSHER_BASE_TICKS = 410;","CRUSHER_FULL_TICKS = 280;",
    "CRUSHER_GTU_MILLI_PER_TICK_MIN = 976;","CRUSHER_GTU_MILLI_PER_TICK_MAX = 1_571;",
    "CENTRIFUGE_GTU_CAPACITY = 2_202 * MILLI;","CENTRIFUGE_GTU_INTAKE = 52 * MILLI;",
    "CENTRIFUGE_HOT_WATER_CAPACITY = 8_000;","CENTRIFUGE_WATER_CAPACITY = 6_000;",
    "CENTRIFUGE_WATER_INTAKE = 256;","CENTRIFUGE_STEAM_INTAKE = 256;","CENTRIFUGE_WASH_TICKS = 310;",
    "UNIVERSAL_FLUID_OUTPUT = 316;",
    # 0.3.142: per-port cap preserved; SteamGen cycle targets moved to SteamGenMath.
    "STEAMGEN_GTH_PER_PORT_MILLI = 312 * MILLI;","STEAMGEN_GTH_LOSS = 667;",
    "STEAMGEN_STEAM_LOSS = 1;"):
    assert pin in mdefs, 'mdefs: '+pin
for pin in ("WIRE_THROUGHPUT = 89L * MachineDefs.MILLI;","HEAT_THROUGHPUT = 562L * MachineDefs.MILLI;",
    "WATER_THROUGHPUT = 852L;","STEAM_THROUGHPUT = 852L;","UNIVERSAL_FLUID_THROUGHPUT = 682L;",
    "ACCUMULATOR_GTU_CAPACITY = 32_608 * MachineDefs.MILLI;","ACCUMULATOR_GTU_OUTPUT = 122 * MachineDefs.MILLI;",
    "ELECTRIC_GTU_CAPACITY = 5_202 * MachineDefs.MILLI;","ELECTRIC_GTU_INTAKE = 48 * MachineDefs.MILLI;",
    "ALLOY_FOUNDRY_GTU_CAPACITY = 2_202 * MachineDefs.MILLI;","ALLOY_FOUNDRY_GTU_INTAKE = 52 * MachineDefs.MILLI;",
    "GRINDER_GTU_CAPACITY = 644 * MachineDefs.MILLI;","GRINDER_GTU_INTAKE = 48 * MachineDefs.MILLI;",
    "GRINDER_GTU_MILLI_PER_TICK = 900;","GRINDER_TICKS = 160;",
    "PRESS_GTU_CAPACITY = 1_876 * MachineDefs.MILLI;","PRESS_GTU_INTAKE = 48 * MachineDefs.MILLI;",
    "PRESS_FATIGUE_TICKS = 140;","PUMP_GTU_CAPACITY = 644 * MachineDefs.MILLI;","PUMP_GTU_INTAKE = 48 * MachineDefs.MILLI;",
    "PUMP_WATER_CAPACITY = 21_000;","PUMP_WATER_OUTPUT = 396;",
    "COBBLE_GTU_INTAKE = 48 * MachineDefs.MILLI;","COBBLE_WATER_INTAKE = 368;","COBBLE_WATER_CAPACITY = 9_000;",
    "COBBLE_GTU_MILLI_PER_TICK = 1_050;","COBBLE_TICKS = 80;"):
    assert pin in s2defs, 's2: '+pin
assert 'NUCLEAR_FIREBOX_GTH_PER_TICK = 116 * MachineDefs.MILLI;' in nuke
assert 'NUCLEAR_FIREBOX_GTH_OUTPUT = 282 * MachineDefs.MILLI;' in nuke
# 0.3.143: author-set natural fuel baselines with preserved form ratios.
assert 'URANIUM_INGOT_BURN_TICKS = 130 * TICKS_PER_SECOND;' in nuke
assert 'URANIUM_NUGGET_BURN_TICKS = (URANIUM_INGOT_BURN_TICKS + 4) / 9;' in nuke
assert 'URANIUM_BLOCK_BURN_TICKS = 9 * URANIUM_INGOT_BURN_TICKS;' in nuke
assert 'URANINITE_BURN_TICKS = (5 * URANIUM_INGOT_BURN_TICKS + 4) / 9;' in nuke
assert 'THORIUM_INGOT_BURN_TICKS = 40 * TICKS_PER_SECOND;' in nuke
assert 'THORIUM_NUGGET_BURN_TICKS = (7 * THORIUM_INGOT_BURN_TICKS + 30) / 60;' in nuke
assert 'THORIUM_BLOCK_BURN_TICKS = 9 * THORIUM_INGOT_BURN_TICKS;' in nuke
assert 'THORIANITE_BURN_TICKS = (7 * THORIUM_INGOT_BURN_TICKS + 6) / 12;' in nuke
assert 'GtUnits.GTH, 256 * 1000,' in ptype and 'GtUnits.WATER, 392,' in ptype and 'GtUnits.STEAM, 392,' in ptype
litho=(ROOT/'src/main/java/com/gonzotech/machines/litho/SiliconFactoryBlockEntity.java').read_text()
assert 'CAPACITY_MILLI = 6_204_000L;' in litho and 'INTAKE_MILLI_PER_TICK = 96_000L;' in litho
fcycle=(ROOT/'src/main/java/com/gonzotech/cleanroom/FilterCycle.java').read_text()
assert 'CAPACITY_GTU = 2202;' in fcycle and 'INTAKE_MILLI_PER_TICK = 52000;' in fcycle
chem=(ROOT/'src/main/java/com/gonzotech/machines/block/entity/ChemicalPlantBlockEntity.java').read_text()
assert 'GTU_CAPACITY = 2_560L;' in chem and 'MAX_GTU_INTAKE_MILLI = 96L * MachineDefs.MILLI;' in chem
assert 'REACTION_TICKS = ChemicalPlantRecipes.DEFAULT_REACTION_TICKS;' in chem
filler=(ROOT/'src/main/java/com/gonzotech/machines/block/entity/FillerBlockEntity.java').read_text()
assert 'GTU_CAPACITY = 8_808;' in filler and 'CANISTER_DRAIN_PER_TICK = 128;' in filler
assert 'Math.min(amount, 96L * MachineDefs.MILLI)' in filler  # приём GTU 96
assert 'smeltTotal = 270;' in filler and 'evapProgress >= 120' in filler  # операции ×1.5
u2=(ROOT/'src/main/java/com/gonzotech/machines/network/SecondUniversalNodeBlock.java').read_text()
assert 'THROUGHPUT_FACTOR = 0.91D;' in u2 and 'return THROUGHPUT_FACTOR;' in u2
furn2=(ROOT/'src/main/java/com/gonzotech/machines/block/entity/SecondElectricFurnaceBlockEntity.java').read_text()
assert '(long) SecondTierDefs.ELECTRIC_GTU_INTAKE' in furn2  # свой приём 48, не общий 24 с T1
sgm=(ROOT/'src/main/java/com/gonzotech/machines/steamgen/SteamGenMath.java').read_text()
assert 'CURVE_EXCHANGERS = {0, 2, 5, 12, 22, 26};' in sgm  # 0.3.142: новая целевая кривая

# ── 0.3.62: компиляция HUD-хвоста — append есть только у MutableComponent ──
hud=(ROOT/'src/main/java/com/gonzotech/machines/client/WrenchHud.java').read_text()
assert 'import net.minecraft.network.chat.MutableComponent;' in hud
assert 'MutableComponent line = Component.empty().append(name).append(sep).append(amount);' in hud
assert '\n        Component line = Component.empty().append' not in hud  # точная форма бага (однострочник с последующим line.append); многострочная цепочка канистры — легальна

# ── 0.3.60: фикс «19 вместо 38» + кумулятивные потери в HUD ключа ──
assert 'budget = Math.min(budget, entrySum);' not in pipe_routing_code, \
    'регрессия 0.3.59: прямой приёмник снова делит проводную ёмкость'
assert 'entrySum' not in pipe_routing_code
flow_tracker=(ROOT/'src/main/java/com/gonzotech/machines/network/FlowTracker.java').read_text()
assert 'public static void recordLoss(Level level, BlockPos pipe, PipeType type, long lossMilli)' in flow_tracker
assert 'public static long getLoss(Level level, BlockPos pipe, PipeType type)' in flow_tracker
pipe_flow=(ROOT/'src/main/java/com/gonzotech/machines/network/PipeFlowNetwork.java').read_text()
assert 'long lossMilli, int clumpSize) implements CustomPacketPayload' in pipe_flow  # 0.3.102: счётчик клампа вернулся
# 0.3.142: один профильный цикл/т, затраты и выход читаются из SteamGenMath.
steam_be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/SteamGenCoreBlockEntity.java").read_text()
assert "SteamGenMath.waterPerCycle(cores, precious)" in steam_be
assert "SteamGenMath.gthPerCycleMilli(cores, precious)" in steam_be
assert "SteamGenMath.steamPerCycleMilli(cores, sumCH, precious)" in steam_be
assert "eventRemainderMilli" not in steam_be
assert 'ByteBufCodecs.VAR_LONG, FlowPayload::lossMilli,' in pipe_flow
assert pipe_flow.count('FlowTracker.getLoss(level, pos, pipeType)') == 1  # 0.3.104: осевых ответов больше нет
wrench=(ROOT/'src/main/java/com/gonzotech/machines/client/WrenchHud.java').read_text()
for pin in ('e.lossMilli = payload.lossMilli();',
            # 0.3.73: пробел перед потерями внутри литерала ниже
            'Component.literal(" (+" + lossText(e.lossMilli) + ")")',
            'private static String lossText(long milli)'):
    assert pin in wrench, 'wrench loss pin: '+pin
for pin in ('private static long[] pathLossCells(Level level, List<PathStep> path, PipeType type)',
            'FlowTracker.recordLoss(level, s.pipe(), type, cumulative);',
            'cumulative = PipeLoss.prefix(lossCells, i);',
            'lanes.add(new Lane(recording(level, observed, type, path, lossCells), path, loss, pos));'):
    assert pin in pipe_routing_code, 'routing 0.3.60 pin: '+pin
assert pipe_routing_code.count('pathLossCells(level, path,') == 3


# 0.3.55: предмет станка — блоковая модель (3D), плоской item-модели больше нет.
items_json = json.loads((ROOT / 'src/main/resources/assets/gonzotech/items/third_silicon_factory.json').read_text())
assert items_json['model']['model'] == 'gonzotech:block/third_silicon_factory'
assert not (ROOT / 'src/main/resources/assets/gonzotech/models/item/third_silicon_factory.json').exists()
assert 'import com.gonzotech.cleanroom.CleanRoomSystem;' in litho_be
assert 'import com.gonzotech.machines.energy.Sinks.GtuSink;' in litho_be
assert 'implements WorldlyContainer, MenuProvider, GtuSink' in litho_be
litho_menu=(ROOT/'src/main/java/com/gonzotech/machines/menu/SiliconFactoryMenu.java').read_text()
# Раскладка автора (лист −128): конвейер y53 (44/80/116/152), шлак y17 (62/98/134), ГТУ (8,17), бары (62/98/134,35).
assert 'INPUT_SLOT, 44, 53)' in litho_menu
assert 'int x = 80 + (index - SiliconFactoryBlockEntity.TRANSIT_FIRST) * 36;' in litho_menu
assert 'addSlot(new Slot(container, index, x, 53)' in litho_menu
assert 'OUTPUT_SLOT, 152, 53)' in litho_menu
assert 'SLAG_BASE + i, 62 + i * 36, 17)' in litho_menu
assert 'addPlayerInventory(inventory, 8, 84);' in litho_menu
# Правила слотов: транзит — ни класть ни брать (включая Shift), выход и шлак — только брать.
assert 'mayPickup(Player player) {' in litho_menu
assert 'stack.is(ModItems.CHIP_SOUP.get())' in litho_menu
assert 'if (index == SiliconFactoryBlockEntity.TRANSIT_FIRST || index == SiliconFactoryBlockEntity.TRANSIT_LAST)' in litho_menu
assert 'stillValid(access, player, ModBlocks.THIRD_SILICON_FACTORY.get())' in litho_menu
litho_screen=(ROOT/'src/main/java/com/gonzotech/machines/client/SiliconFactoryScreen.java').read_text()
assert 'silicon_factory_chip_" + variant + "_gui.png' in litho_screen
assert 'silicon_factory_gui.png' in litho_screen
# ГТУ (136,145) 16×52; бары (190/226/262,163) 16×34 слева-направо bar_smelting.
assert 'drawVBarTex(graphics, x + 8, y + 17, 16, 52,' in litho_screen
assert 'drawHBarTex(graphics, x + 62 + i * 36, y + 35, 16, 34, fraction, BAR_SMELTING)' in litho_screen
# Раунд 14: ГТУ — формат эпохи 3 X.Y (GtUnits.x1), тысячных больше нет.
assert 'GtUnits.gtuPair(' in litho_screen and 'GtUnits.x1(menu.gtuMilli() / 1000.0D)' in litho_screen
# Тултипы: имена шагов + строка качества (вне контура — «обычный»).
assert 'gui.gonzotech.silicon_factory.etching' in litho_screen
assert 'gui.gonzotech.silicon_factory.photolithography' in litho_screen
assert 'gui.gonzotech.silicon_factory.vulcanization' in litho_screen
assert 'gui.gonzotech.silicon_factory.quality_ambient' in litho_screen
# ── 0.3.47: юнит-фикс шанса брака + прозрачность оболочки для чистой комнаты ──
# Сотые доли процента (0..10000) НЕ передаются в rejectPercent напрямую — только /100.
assert 'rejectPercent(be.qualityHundredths / 100.0)' in litho_be_code
litho_screen_code=re.sub(r'/\*.*?\*/|//[^\n]*','',litho_screen,flags=re.S)
assert 'rejectPercent(quality / 100.0)' in litho_screen_code
# Классификатор комнаты: kindAt отдаёт класс ОРИГИНАЛА для shell-блоков.
detector=(ROOT/'src/main/java/com/gonzotech/cleanroom/CleanRoomDetector.java').read_text()
assert 'public static RoomTopology.Kind kindAt(ServerLevel level, BlockPos pos, BlockState state)' in detector
assert 'SiliconFactoryShellBlock' in detector and 'preservedKind()' in detector
assert 'return kindAt(level, blockPos, level.getBlockState(blockPos));' in detector
system=(ROOT/'src/main/java/com/gonzotech/cleanroom/CleanRoomSystem.java').read_text()
assert 'CleanRoomDetector.kindAt(level, pos, before), CleanRoomDetector.kindAt(level, pos, after)' in system
# Оболочка: NBT-поле PreservedKind + контроллер-первичный preservedKind.
assert 'PreservedKind' in shell_be_code and 'RoomTopology.Kind.valueOf' in shell_be_code
assert 'SiliconFactoryStructure.originalKindAt(server, worldPosition)' in shell_be_code
assert 'public static RoomTopology.Kind originalKindAt(ServerLevel level, BlockPos pos)' in litho_struct_code
assert 'proxy.setPreserved(CleanRoomDetector.kind(originalStates[i]))' in litho_struct_code
# Контроллер: preservedKindAt по оригиналам.
assert 'public RoomTopology.Kind preservedKindAt(BlockPos pos)' in litho_be_code
assert 'CleanRoomDetector.kind(originalStates[i])' in litho_be_code
# Порядок: setFormed+index ДО цикла перестановки; восстановление ДО unindex.
assert litho_struct.index('controller.setFormed') < litho_struct.index('level.setBlock(memberPos[i]')
assert litho_struct.index('controller.originalState(i)') < litho_struct.index('unindex(level, origin)')
# Кросс-пакетные/событийные импорты фиксируем явно: ECJ без classpath их не ловит (0.3.41 hotfix).
assert 'import net.neoforged.neoforge.event.level.BlockEvent;' in litho_struct
assert 'import com.gonzotech.machines.menu.SiliconFactoryMenu;' in litho_be
assert 'import net.minecraft.world.level.block.Block;' in litho_be
assert 'renderComponentTooltip(font,' in litho_screen
# 0.3.42 hotfix: GtUnits — кросс-пакетный импорт, ECJ без classpath не ловит.
assert 'import com.gonzotech.core.text.GtUnits;' in litho_screen
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
# UV от кадра 96×96 (формат .mcmeta-анимации: кадр = ширина×ширина, 0.3.43):
# v = px_y/6 (как и u). Контроль: верхний центр — пиксели (64,48); низ — (16,16).
# UV сторон по градиентным фото автора (0.3.45): west ✓ as-is, east — зеркальные окна,
# north/south — развёрнутые окна (u1>u2 в JSON — легальный флип). Сверяем ВСЕ 54 модели.
def exp_side_u(face, sx, sz):
    return {'west': (sz*16, sz*16+16), 'east': ((2-sz)*16, (3-sz)*16),
            'north': ((sx+1)*16, sx*16), 'south': ((3-sx)*16, (2-sx)*16)}[face]
def r6(x): return round(x/6, 4)
for v in (1,2,3):
    for slot in range(18):
        sx, sy, sz = slot%3, slot//9, (slot//3)%3
        mm=json.loads((ROOT/f'src/main/resources/assets/gonzotech/models/block/third_silicon_factory/slice_{slot}_chip_{v}.json').read_text())
        faces=mm['elements'][0]['faces']
        for face in ('north','south','west','east'):
            u1,u2=exp_side_u(face,sx,sz); v1,v2=48+(1-sy)*16, 64+(1-sy)*16
            assert faces[face]['uv']==[r6(u1),r6(v1),r6(u2),r6(v2)], (slot,v,face,faces[face]['uv'])
        assert faces['down']['uv']==[r6(sx*16),r6(sz*16),r6(sx*16+16),r6(sz*16+16)]
        assert faces['up']['uv']==[r6(48+sx*16),r6(32+sz*16),r6(64+sx*16),r6(48+sz*16)]
        assert faces['down'].get('cullface')==('down' if sy!=0 else None)
slice13=json.loads((ROOT/'src/main/resources/assets/gonzotech/models/block/third_silicon_factory/slice_13_chip_1.json').read_text())
assert slice13['elements'][0]['faces']['up']['uv']==[10.6667, 8.0, 13.3333, 10.6667]
for v in (1,2,3):
    tp=ROOT/f'src/main/resources/assets/gonzotech/textures/block/third/silicon_factory_chip_{v}_formed.png'
    with LithoImage.open(tp) as im:
        # 0.3.55 (автор, e796232): лист анимирован — вертикальная лента кадров
        # 96×96 (высота кратна 96) + .mcmeta с явным списком кадров.
        assert im.mode=='RGBA' and im.width==96 and im.height % 96 == 0, tp.name
    mc=json.loads((ROOT/f'src/main/resources/assets/gonzotech/textures/block/third/silicon_factory_chip_{v}_formed.png.mcmeta').read_text())
    anim=mc['animation']
    assert anim['frametime']>=1 and len(anim['frames'])>=2
    for fr in anim['frames']:
        idx = fr if isinstance(fr,int) else fr['index']
        assert 0 <= idx < im.height//96
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
assert 'block.gonzotech.third_silicon_factory' in lang_ru
for k in ('etching','photolithography','vulcanization','quality','quality_ambient'):
    assert f'gui.gonzotech.silicon_factory.{k}' in lang_ru and f'gui.gonzotech.silicon_factory.{k}' in lang_en, k
assert 'gui.gonzotech.silicon_factory.progress' not in lang_ru and 'gui.gonzotech.silicon_factory.progress' not in lang_en
assert lang_ru['gui.gonzotech.silicon_factory.quality'].startswith('§7') and lang_en['gui.gonzotech.silicon_factory.etching'].startswith('§f')
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
