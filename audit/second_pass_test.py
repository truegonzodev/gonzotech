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

# ── 0.3.31: слои кризисной дымки, тултипы филлера, лут слизи, тултип змеевика ──
# Дымка: scissor-композиция вокруг защищённых зон, подписи/тексты — под ней.
assert 'enableScissor' in client and 'paintEverywhereExcept' in client and 'canHurtPlayer' in client
assert client.index('renderScreenEffect(g, width, height);') < client.index('renderFakeDeath(g, mc, width, height);'), 'haze before fake death'
mod=read('GonzoTechMod.java')
order=[mod.index(x) for x in ['PsycheTremorClient.class','WrenchHud.class','SpeedometerHud.class','SolarWatchHud.class','PsycheHudLabels.class','PsycheCrisisClient.class','register(com.gonzotech.core.psyche.client.PsycheHud.class)']]
assert order==sorted(order), 'HUD text before haze, psyche bars after'
psy=read('core/psyche/client/PsycheHud.java'); labels=read('core/psyche/client/PsycheHudLabels.java')
assert 'drawString' not in psy and 'drawString' in labels, 'labels moved out of PsycheHud'
assert 'specs(' in psy and 'PsycheHud.specs' in labels
# Филлер: хитбокс по дыркам PNG + подавление тултипов над слотами.
filler=read('machines/client/FillerScreen.java')
assert 'NativeImage.read' in filler and 'getResourceManager' in filler and 'closeForegroundMask' in filler
assert 'inGaugeHole' in filler and 'isOverSlot' in filler
assert filler.count('inGaugeHole(mouseX, mouseY')==5, '4 gauges + arch use PNG holes'
assert 'isOverSlot(mouseX, mouseY)' in filler and 'inRect(mouseX, mouseY, x + 80, y + 35, 16, 16)' in filler
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
