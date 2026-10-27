#!/usr/bin/env python3
"""ENG-001: actual Java energy core plus static wiring/PNG/loot/JEI contracts.
No claim of MC/NeoForge compilation, loot-engine execution or in-game acceptance.
Java 21 + javac or ECJ_JAR; Pillow required. Does not download dependencies.
"""
from pathlib import Path
import json,os,re,shutil,subprocess,tempfile,sys
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'src/main/java/com/gonzotech';RES=ROOT/'src/main/resources';checks=0

def check(value,name):
    global checks
    checks+=1
    assert value,name

def source(path):return (SRC/path).read_text()
def data(path):return json.loads((RES/path).read_text())

be=source('machines/block/entity/ThirdEnergyStorageBlockEntity.java')
check('implements GtuSink' in be and 'PipeRouting.drain(server,pos,PipeType.WIRE,budget,now' in be,'actual GTU network sink/output')
check('energy.receive(amount,simulate,level.getGameTime())' in be and 'energy.availableOutput(now)' in be and 'energy.extract(paid,false,now)' in be,'per-tick aggregate core wired, path loss sender-paid')
check('energy.idle(now)' in be and 'energy.restore(tag.getLong("EnergyMilli"))' in be and 'tag.putLong("EnergyMilli",energy.stored())' in be,'exact save/load and tick loss')
check('energy.stored()==0 ? 0 : 1+(int)(14L*energy.stored()/energy.spec().capacity())' in be,'comparator empty/full scale')
for name in ['AccumulatorBlockEntity','SecondAccumulatorBlockEntity']:
    check('target instanceof ThirdEnergyStorageBlockEntity other' in source(f'machines/block/entity/{name}.java'),'old-to-new equalization: '+name)
check('target instanceof AccumulatorBlockEntity store' in be and 'target instanceof SecondAccumulatorBlockEntity store' in be,'new-to-old equalization')
accu=source('machines/block/entity/ThirdAccumulatorBlockEntity.java')
check('items.set(slot,stack)' in accu and 'super.setItem' not in accu,'ordinary slots do not unpack carriers')
check('stack.getMaxStackSize()' in accu and 'getMaxStackSize()' in accu,'ordinary item/container limits')
check('ThirdStorageDefs.ITEM_SLOTS,ThirdStorageDefs.ACCUMULATOR' in accu,'three stored slots')
battery=source('machines/block/ThirdBatteryBlock.java');item=source('machines/item/ThirdBatteryItem.java')
check('getDrops(BlockState state, LootParams.Builder params)' in battery and 'LootContextParams.BLOCK_ENTITY' in battery,'battery persistence is loot-context based')
check('super.getDrops(state,params)' in battery and 'stack.is(asItem())' in battery and 'battery.saveToItem(stack)' in battery,'stamp only actual battery loot')
check('playerWillDestroy' not in battery and 'popResource' not in battery,'no second/manual-player-only drop')
check('battery.loadFromItem(stack)' in battery and 'extends AccumulatorBlock implements LiquidBlockContainer' in battery,'canister-style placed storage and exact item restore')
check('return false;' in battery and 'canPlaceLiquid' in battery and 'placeLiquid' in battery,'no accidental waterlogging')
check('properties.stacksTo(1)' in item and 'inventoryTick' not in item,'nonstackable passive battery item')
check('getLong(ENERGY_KEY)' in item and 'putLong(ENERGY_KEY,value)' in item and 'data.update' in item,'long exact custom data, preserve other custom tags')
check('ThirdStorageDefs.BATTERY.capacity()' in item,'corrupt charge clamped to finite capacity')
menu=source('machines/menu/ThirdStorageMenu.java')
check('new Slot(container,slot,80,17+18*slot)' in menu and 'addPlayerInventory(inv,8,84)' in menu,'canvas slots (208,145/163/181) and standard player inventory')
check('TickEnergyStorage.decode(data.get(0),data.get(1),data.get(2))' in menu,'independent limbs decoded only at read')
for name in ['Accumulator','Battery']:
    check('new SimpleContainerData(ThirdStorageDefs.SYNC_PARTS)' in source(f'machines/menu/Third{name}Menu.java'),'independent client data: '+name)
    screen=source(f'machines/client/Third{name}Screen.java')
    check('GtUnits.x1(menu.gtuUnits())' in screen and 'GtUnits.x1(menu.capacityUnits())' in screen,'III one-decimal display: '+name)
    check(f'ModMenus.THIRD_{name.upper()}.get(), Third{name}Screen::new' in source('machines/client/MachineClient.java'),'screen registered: '+name)
check('x+62, barY=y+17' in source('machines/client/ThirdAccumulatorScreen.java'),'bar canvas (190,145)')
check('machineGui("accumulator_gui.png","third_battery_gui_neo.png")' in source('machines/client/ThirdBatteryScreen.java'),'ordinary Classic reuse, separate battery NEO')
for name in ['third_accumulator','third_battery']:
    for file in ['machines/registry/ModMachines.java','machines/registry/ModBlockEntities.java','machines/registry/ModMenus.java']:
        check(f'"{name}"' in source(file),'registry '+file+'/'+name)
    check(f'ModMachines.{name.upper()}_ITEM.get()' in source('machines/crafting/TierThreeCrafting.java'),'physical Opening III gate: '+name)
    check(not (RES/f'data/gonzotech/recipe/{name}.json').exists(),'no invented author recipe: '+name)
    loot=data(f'data/gonzotech/loot_table/blocks/{name}.json')
    check(len(loot['pools'])==1 and loot['pools'][0]['rolls']==1.0 and loot['pools'][0]['entries']==[{'type':'minecraft:item','name':'gonzotech:'+name}],'single ordinary self loot: '+name)
    check(f'gonzotech:{name}' in data('data/minecraft/tags/block/mineable/pickaxe.json')['values'],'pickaxe mining '+name)
    check(data(f'assets/gonzotech/items/{name}.json')['model']['model']==f'gonzotech:block/{name}','1.21.4 inventory model '+name)
    for lang in ['ru_ru','en_us']:
        check('block.gonzotech.'+name in data(f'assets/gonzotech/lang/{lang}.json'),'localization '+name+'/'+lang)
creative=source('core/registry/ModCreativeTabs.java')
check('output.accept(ModItems.CANISTER.get());\n                output.accept(com.gonzotech.machines.registry.ModMachines.THIRD_BATTERY_ITEM.get());' in creative,'battery immediately beside canister')
check('output.accept(com.gonzotech.machines.registry.ModMachines.THIRD_ACCUMULATOR_ITEM.get());' in creative,'III functional creative group')
for theme,suffix in [('classic',''),('neo','_neo')]:
    fg=Image.open(RES/f'assets/gonzotech/textures/gui/{theme}/third_accumulator_gui{suffix}.png').convert('RGBA')
    bg=Image.open(RES/f'assets/gonzotech/textures/gui/{theme}/third_accumulator_gui_bg{suffix}.png').convert('RGBA')
    check(fg.size==bg.size==(512,512),'512x512 '+theme)
    check(fg.crop((190,145,206,197)).getchannel('A').getextrema()==(0,0),'full gauge opening '+theme)
    check(bg.crop((190,145,206,197)).getchannel('A').getextrema()==(255,255),'opaque gauge backing '+theme)
    for y in [145,163,181]:check(fg.crop((208,y,224,y+16)).getcolors()==[(256,(139,139,139,255))],'ordinary slot interior '+theme+str(y))
previews=source('integration/jei/AlloyCraftingJeiRecipes.java')
check('AlloyEquipmentStats.Kind.values()' in previews and 'kind.pattern()' in previews and 'kind.outputItem()' in previews,'all six equipment forms from live definitions')
check('HeatExchangerCellRecipe.pattern()' in previews and 'AlloyGreatswordRecipe.pattern()' in previews,'cell and greatsword from live shapes')
check('new SlotDisplay.ItemStackSlotDisplay(new ItemStack(item))' in previews and 'Ingredient.of(item),display(item)' in previews,'plain composition-free outputs and inputs')
check(not any(x in previews for x in ['ModDataComponents','fromComposition(','createAlloyStack(','RecipeManager.','registerRecipe']),'no fabricated composition or server registration')
check('registration.addRecipes(RecipeTypes.CRAFTING, AlloyCraftingJeiRecipes.build(registration.getVanillaRecipeFactory()))' in source('integration/jei/GonzoTechJeiPlugin.java'),'previews registered through JEI-only factory')
check('"jei_preview/"+id' in previews,'unique display-only IDs')
# Pure production Java, no MC stubs. No silent toolchain SKIP.
home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else shutil.which('java');javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file():raise SystemExit('Java 21 required')
compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
with tempfile.TemporaryDirectory(prefix='gonzotech-third-storage-') as output:
    files=[SRC/'machines/energy/ThirdStorageDefs.java',SRC/'machines/energy/TickEnergyStorage.java',ROOT/'audit/ThirdStorageSelfTest.java']
    subprocess.run(compiler+['-d',output]+list(map(str,files)),check=True)
    subprocess.run([java,'-cp',output,'ThirdStorageSelfTest'],check=True)
print(f'ENG-001 static resource/wiring checks PASS: {checks}; MC build/playtest not executed')

if '--mutation-check' in sys.argv:
    original=(SRC/'machines/energy/TickEnergyStorage.java').read_text()
    mutations={
        'per_call_input':('inputTick == tick ? received : 0','0'),
        'per_call_output':('outputTick == tick ? extracted : 0','0'),
        'mutating_receive_simulation':('if (!simulate && accepted > 0)','if (accepted > 0)'),
        'mutating_extract_simulation':('if (!simulate && given > 0)','if (given > 0)'),
        'rounded_persistence':('stored = Math.max(0L, Math.min(spec.capacity(), amount))','stored = Math.max(0L, Math.min(spec.capacity(), amount)) / 1000 * 1000'),
        'lost_parasitic_loss':('Math.min(stored, spec.idleLoss())','0L'),
        'double_loss_same_tick':('if (lossTick == tick) return 0;','if (false) return 0;'),
        'wrong_input_budget':('spec.intake() - used','spec.output() - used'),
        'discard_sync_fraction':('Math.clamp(fraction, 0, 999) +','0L +'),
        'signed_short_overflow':('(int)(stored / 1_000_000L)','(int)stored'),
    }
    for name,(old,new) in mutations.items():
        assert old in original,name
        with tempfile.TemporaryDirectory(prefix='gonzotech-third-mutant-') as output:
            mutated=Path(output)/'TickEnergyStorage.java';mutated.write_text(original.replace(old,new,1))
            proc=subprocess.run(compiler+['-d',output,str(SRC/'machines/energy/ThirdStorageDefs.java'),str(mutated),str(ROOT/'audit/ThirdStorageSelfTest.java')],capture_output=True,text=True)
            assert proc.returncode==0,(name,'compile failure is NOT a mutation kill',proc.stderr)
            proc=subprocess.run([java,'-cp',output,'ThirdStorageSelfTest'],capture_output=True,text=True)
            assert proc.returncode!=0 and 'AssertionError' in proc.stderr,(name,'mutation survived',proc.stdout,proc.stderr)
            print('mutation killed: '+name)
    print(f'ENG-001 mutations rejected: {len(mutations)}/{len(mutations)}; production sources untouched')
