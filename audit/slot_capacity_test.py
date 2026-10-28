#!/usr/bin/env python3
"""PERF-001/2: production per-slot admission, real upstream handler algorithms.
MC storage/types and Mixin application are explicit boundaries. --mutation-check
requires AssertionError (compiler failure never counts as a killed mutant).
"""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,tempfile
from _radiation_contracts import method
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/com/gonzotech'
FIX=ROOT/'audit/fixtures/slot_capacity'

def read(p):return (JAVA/p).read_text()
def methods(text, signatures):return '\n'.join(method(text,s) for s in signatures)

STUBS={
'net/minecraft/core/Direction.java':'package net.minecraft.core; public enum Direction{DOWN,UP,NORTH,SOUTH,EAST,WEST}',
'net/minecraft/world/item/Item.java':'package net.minecraft.world.item; public class Item{}',
'net/minecraft/world/item/Items.java':'package net.minecraft.world.item; public class Items{public static final Item TNT=new Item(),BUCKET=new Item();}',
'com/gonzotech/machines/item/MemoryCardItem.java':'package com.gonzotech.machines.item;public class MemoryCardItem extends net.minecraft.world.item.Item{}',
'net/minecraft/world/item/ItemStack.java':'''package net.minecraft.world.item;
public class ItemStack{public static final ItemStack EMPTY=new ItemStack(null,0);private final Item item;private int count;public final int component;
public ItemStack(Item i,int n){this(i,n,73);}public ItemStack(Item i,int n,int c){item=i;count=n;component=c;}
public Item getItem(){return item;}public int getCount(){return count;}public int getMaxStackSize(){return 64;}public boolean isEmpty(){return count<=0||item==null;}public boolean is(Item i){return item==i;}
public ItemStack copy(){return copyWithCount(count);}public ItemStack copyWithCount(int n){return new ItemStack(item,n,component);}public void setCount(int n){count=n;}public void grow(int n){count+=n;}public void shrink(int n){count-=n;}public ItemStack split(int n){int k=Math.min(n,count);count-=k;return copyWithCount(k);}
public static boolean isSameItemSameComponents(ItemStack a,ItemStack b){return a.item==b.item&&a.component==b.component;}}
''',
'net/minecraft/world/Container.java':'''package net.minecraft.world;import net.minecraft.world.item.ItemStack;public interface Container{int getContainerSize();ItemStack getItem(int s);void setItem(int s,ItemStack v);void setChanged();int getMaxStackSize();boolean canPlaceItem(int s,ItemStack v);ItemStack removeItem(int s,int n);boolean isEmpty();}''',
'net/minecraft/world/WorldlyContainer.java':'''package net.minecraft.world;import net.minecraft.core.Direction;import net.minecraft.world.item.ItemStack;public interface WorldlyContainer extends Container{int[] getSlotsForFace(Direction d);boolean canPlaceItemThroughFace(int s,ItemStack v,Direction d);boolean canTakeItemThroughFace(int s,ItemStack v,Direction d);}''',
'net/neoforged/neoforge/items/IItemHandlerModifiable.java':'''package net.neoforged.neoforge.items;import net.minecraft.world.item.ItemStack;public interface IItemHandlerModifiable{int getSlots();ItemStack getStackInSlot(int s);ItemStack insertItem(int s,ItemStack v,boolean sim);ItemStack extractItem(int s,int n,boolean sim);void setStackInSlot(int s,ItemStack v);int getSlotLimit(int s);boolean isItemValid(int s,ItemStack v);}''',
'org/jetbrains/annotations/Nullable.java':'package org.jetbrains.annotations;public @interface Nullable{}',
'org/spongepowered/asm/mixin/Mixin.java':'package org.spongepowered.asm.mixin;public @interface Mixin{Class<?>[] value();}',
'org/spongepowered/asm/mixin/Shadow.java':'package org.spongepowered.asm.mixin;public @interface Shadow{}',
'org/spongepowered/asm/mixin/Final.java':'package org.spongepowered.asm.mixin;public @interface Final{}',
'org/spongepowered/asm/mixin/injection/At.java':'package org.spongepowered.asm.mixin.injection;public @interface At{String value();}',
'org/spongepowered/asm/mixin/injection/Inject.java':'package org.spongepowered.asm.mixin.injection;public @interface Inject{String[] method();At at();boolean cancellable() default false;}',
'org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java':'package org.spongepowered.asm.mixin.injection.callback;public class CallbackInfoReturnable<T>{T value;public CallbackInfoReturnable(T v){value=v;}public T getReturnValue(){return value;}public void setReturnValue(T v){value=v;}}',
'com/llamalad7/mixinextras/injector/wrapmethod/WrapMethod.java':'package com.llamalad7.mixinextras.injector.wrapmethod;public @interface WrapMethod{String[] method();}',
'com/llamalad7/mixinextras/injector/wrapoperation/Operation.java':'package com.llamalad7.mixinextras.injector.wrapoperation;public interface Operation<T>{T call(Object...args);}',
'com/gonzotech/radiation/engine/ExternalRadiation.java':'package com.gonzotech.radiation.engine;import net.minecraft.world.item.ItemStack;public class ExternalRadiation{public static void merge(ItemStack a,ItemStack b,int n){}}',
}
IMPORTS='''import java.util.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.core.Direction;import com.gonzotech.machines.inventory.SlotCapacity;import com.gonzotech.machines.item.MemoryCardItem;'''
BASE='''package probe;'''+IMPORTS+'''public class Base implements WorldlyContainer{public final List<ItemStack> items=new ArrayList<>();public int changes,drops;
public Base(){for(int i=0;i<40;i++)items.add(ItemStack.EMPTY);}public int getContainerSize(){return items.size();}public ItemStack getItem(int s){return items.get(s);}public void setItem(int s,ItemStack v){items.set(s,v);setChanged();}public void setChanged(){changes++;}public int getMaxStackSize(){return 64;}public boolean canPlaceItem(int s,ItemStack v){return true;}public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}public ItemStack removeItem(int s,int n){return items.get(s).split(n);}public int[] getSlotsForFace(Direction d){return new int[]{0};}public boolean canPlaceItemThroughFace(int s,ItemStack v,Direction d){return canPlaceItem(s,v);}public boolean canTakeItemThroughFace(int s,ItemStack v,Direction d){return true;}}
'''
PROBE=r'''package probe;IMPORTS
import net.neoforged.neoforge.items.wrapper.*;public class Probe{
static int checks;static Item card=new MemoryCardItem(),stone=new Item();static void eq(int a,int b,String why){checks++;if(a!=b)throw new AssertionError(why+": "+a+" != "+b);}static void yes(boolean v,String why){eq(1,v?1:0,why);}static ItemStack cards(int n){return new ItemStack(card,n);}
static void attempt(Base dst,int slot,boolean sided,Item item,int n){
 var handler=sided?new SidedInvWrapper(dst,Direction.NORTH):new InvWrapper(dst);
 int wrapperSlot=slot; // NORTH exposes identity slots in the fixture (DOWN is separately remapped below).
 ItemStack source=new ItemStack(item,n);int dirty=dst.changes;
 ItemStack simulated=handler.insertItem(wrapperSlot,source,true);
 eq(n-1,simulated.getCount(),"simulation predicts one");eq(0,dst.getItem(slot).getCount(),"simulation storage read-only");eq(dirty,dst.changes,"simulation dirty read-only");eq(n,source.getCount(),"simulation preserves caller stack");
 ItemStack left=handler.insertItem(wrapperSlot,source,false);eq(n-1,left.getCount(),"accepted one, remainder retained");eq(n,source.getCount(),"wrapper does not consume caller object");eq(1,dst.getItem(slot).getCount(),"slot count one");eq(n,dst.getItem(slot).getCount()+left.getCount(),"conservation");eq(73,dst.getItem(slot).component,"component preserved");
 ItemStack again=handler.insertItem(wrapperSlot,source,false);eq(n,again.getCount(),"occupied rejects entire offer");eq(1,dst.getItem(slot).getCount(),"second insertion never grows singleton");eq(0,dst.drops,"no floor overflow");
}
public static void main(String[]args){try{runProbe();}catch(RuntimeException e){throw new AssertionError("admission allowed invalid write",e);}}
public static void runProbe(){
 for(int n:new int[]{1,2,16,64})for(boolean sided:new boolean[]{false,true}){
  attempt(new Plotnick(),9,sided,card,n);attempt(new Plotnick(),10,sided,card,n);attempt(new Seismic(),0,sided,Items.TNT,n);
 }
 Plotnick p=new Plotnick();eq(1,new InvWrapper(p).getSlotLimit(9),"A limit");eq(1,new InvWrapper(p).getSlotLimit(10),"B limit");eq(64,new InvWrapper(p).getSlotLimit(0),"ordinary slot unchanged");
 ItemStack left=new InvWrapper(p).insertItem(0,new ItemStack(stone,64),false);eq(0,left.getCount(),"ordinary stack accepted");eq(64,p.getItem(0).getCount(),"ordinary capacity not globally clamped");
 p=new Plotnick();left=new InvWrapper(p).insertItem(13,new ItemStack(stone,64),false);eq(0,left.getCount(),"depot accepted full stack");eq(64,p.virtual.get(2).getCount(),"virtual depot not singleton");
 // DOWN wrapper slot0 maps to virtual index11, NOT inventory card slot9/10.
 var down=new SidedInvWrapper(p,Direction.DOWN);eq(64,down.getSlotLimit(9),"sided wrapper slot9 maps to virtual inventory slot20, not card9");
 eq(0,new SidedInvWrapper(p,Direction.NORTH).getSlotLimit(-1),"negative sided index bounded");eq(0,new SidedInvWrapper(p,Direction.NORTH).getSlotLimit(1000),"invalid sided index bounded");
 for(int slot:new int[]{9,10}){
  p=new Plotnick();for(int i=0;i<9;i++)p.items.set(i,new ItemStack(stone,64));for(int i=0;i<24;i++)p.virtual.set(i,new ItemStack(stone,64));yes(ItemRouting.insertOne(p,Direction.NORTH,cards(1)),"pipe inserts first card");
  // A then B can each take one; third must fail without caller debit.
  yes(ItemRouting.insertOne(p,Direction.NORTH,cards(1)),"pipe inserts second card into B");
  yes(!ItemRouting.insertOne(p,Direction.NORTH,cards(1)),"both occupied reject third");eq(1,p.getItem(9).getCount(),"A stays one");eq(1,p.getItem(10).getCount(),"B stays one");
  p=new Plotnick();p.items.set(slot,cards(1));yes(!p.canPlaceItem(slot,cards(64)),"occupied card admission denies bulk");yes(!ItemRouting.tryPlaceForProbe(p,slot,cards(1)),"pipe capacity guards even bypassed canPlace");
 }
 Seismic t=new Seismic();yes(ItemRouting.insertOne(t,Direction.UP,new ItemStack(Items.TNT,1)),"pipe TNT first");yes(!ItemRouting.insertOne(t,Direction.UP,new ItemStack(Items.TNT,1)),"pipe TNT occupied");
 for(boolean rejected:new boolean[]{false,true})for(int slot:new int[]{9,10}){
  p=new Plotnick();ItemStack source=cards(64);if(rejected)p.reject=true;
  left=net.minecraft.world.level.block.entity.HopperBlockEntity.move(p,source,slot,Direction.NORTH);
  eq(rejected?64:63,left.getCount(),"Hopper bulk admission remainder");eq(rejected?0:1,p.getItem(slot).getCount(),"Hopper never writes bulk");eq(64,source.getCount(),"bounded Hopper offer leaves donor input intact");
  eq(64,left.getCount()+p.getItem(slot).getCount(),"Hopper bulk conservation");
 }
 t=new Seismic();left=net.minecraft.world.level.block.entity.HopperBlockEntity.move(t,new ItemStack(Items.TNT,64),0,Direction.UP);eq(63,left.getCount(),"Hopper TNT remainder");eq(1,t.getItem(0).getCount(),"Hopper TNT one");
 left=net.minecraft.world.level.block.entity.HopperBlockEntity.move(t,new ItemStack(Items.TNT,16),0,Direction.UP);eq(16,left.getCount(),"Hopper occupied returns full offer");eq(0,t.drops,"Hopper no drops");
 Plotnick wrongCard=new Plotnick();ItemStack refused=new InvWrapper(wrongCard).insertItem(9,new ItemStack(stone,64),false);eq(64,refused.getCount(),"wrong item in card slot retained");eq(0,wrongCard.getItem(9).getCount(),"wrong card-slot type not written");
 Seismic wrongTnt=new Seismic();refused=new SidedInvWrapper(wrongTnt,Direction.NORTH).insertItem(0,cards(64),false);eq(64,refused.getCount(),"wrong TNT-slot item retained");eq(0,wrongTnt.getItem(0).getCount(),"wrong TNT-slot type not written");
 Base globalOne=new Base(){public int getMaxStackSize(){return 1;}};
 left=net.minecraft.world.level.block.entity.HopperBlockEntity.move(globalOne,new ItemStack(Items.TNT,64),0,Direction.UP);eq(63,left.getCount(),"global singleton gets one from bulk offer");eq(1,globalOne.getItem(0).getCount(),"global singleton never receives stack");
 left=net.minecraft.world.level.block.entity.HopperBlockEntity.move(globalOne,new ItemStack(Items.TNT,1),0,Direction.UP);eq(1,left.getCount(),"global singleton cannot grow via vanilla merge");eq(1,globalOne.getItem(0).getCount(),"global singleton remains one");
 Base ordinary=new Base();left=net.minecraft.world.level.block.entity.HopperBlockEntity.move(ordinary,new ItemStack(stone,64),0,Direction.UP);eq(64,ordinary.getItem(0).getCount(),"unbounded vanilla path unchanged");
 p=new Plotnick();p.items.set(9,cards(16));left=new InvWrapper(p).insertItem(9,cards(1),false);eq(1,left.getCount(),"preexisting overload gets no extra");eq(16,p.getItem(9).getCount(),"no legacy trim/migration");p.setItem(9,p.getItem(9));eq(16,p.getItem(9).getCount(),"legacy in-place update not a new insertion");
 for(Base dst:new Base[]{new Plotnick(),new Seismic()}){
  int slot=dst instanceof Plotnick?9:0;ItemStack bad=new ItemStack(dst instanceof Plotnick?card:Items.TNT,64);boolean failed=false;
  try{dst.setItem(slot,bad);}catch(IllegalArgumentException e){failed=true;}
  yes(failed,"raw replacement is not bulk insertion");eq(64,bad.getCount(),"invalid raw replacement does not trim caller");eq(0,dst.getItem(slot).getCount(),"invalid raw replacement leaves slot intact");eq(0,dst.drops,"no direct-write spill workaround");
 }
 System.out.println("Slot capacity PASS: "+checks+" assertions; upstream wrappers/MC doubles, manual hook wiring, no native bootstrap");
}}
'''.replace('IMPORTS',IMPORTS)

def sources(overrides=None):
 overrides=overrides or {}
 def src(path):return overrides.get(path,read(path))
 out=dict(STUBS)
 out['probe/Base.java']=BASE
 out['probe/Probe.java']=PROBE
 out['com/gonzotech/machines/inventory/SlotCapacity.java']=src('machines/inventory/SlotCapacity.java')
 p=src('machines/block/entity/PlotnickBlockEntity.java')
 out['probe/Plotnick.java']='package probe;'+IMPORTS+'''public class Plotnick extends Base implements SlotCapacity{
static final int SLOT_CARD_A=9,SLOT_CARD_B=10,SLOTS_ACTIVE=9,CONTAINER_SIZE=11,CONTAINER_TOTAL=35,MAX_STACK=64,STACKS_PER_TYPE=4,OUT_STACKS_PER_TYPE=2,VIRTUAL_SLOTS=24;boolean networkStatsDirty;public boolean reject;public final List<ItemStack> virtual=new ArrayList<>(Collections.nCopies(24,ItemStack.EMPTY));static int capacityFor(ItemStack s){return Math.min(64,s.getMaxStackSize());}void insertOrDrop(ItemStack s,Object p){drops++;}Object worldPosition;void setVirtual(int i,ItemStack s){virtual.set(i,s);}public ItemStack getItem(int s){return s>=11?virtual.get(s-11):items.get(s);}
'''+methods(p,['public void setItem(', 'private static boolean isAllowedCard(', 'public boolean canPlaceItem(', 'public int slotCapacity(', 'public int getMaxStackSize(', 'public int[] getSlotsForFace(', 'public boolean canPlaceItemThroughFace('])+'''
public boolean canPlaceForProbe(int s,ItemStack v){return !reject&&canPlaceItem(s,v);}}
'''
 # Refusal is a boundary probe independent of production canPlaceItem rules.
 out['probe/Plotnick.java']=out['probe/Plotnick.java'].replace('public boolean canPlaceItem(int slot, ItemStack stack) {','public boolean canPlaceItem(int slot, ItemStack stack) { if(reject)return false;')
 t=src('machines/block/entity/SeismicSourceBlockEntity.java')
 out['probe/Seismic.java']='package probe;'+IMPORTS+'public class Seismic extends Base implements SlotCapacity{static final int SLOT_DYNAMITE=0;static final int[] SLOTS={0};'+methods(t,['public static boolean isDynamite(', 'public boolean canPlaceItem(', 'public void setItem(', 'public int slotCapacity(', 'public int getMaxStackSize(', 'public int[] getSlotsForFace(', 'public boolean canPlaceItemThroughFace('])+'}'
 route=src('machines/network/ItemRouting.java')
 out['probe/ItemRouting.java']='package probe;'+IMPORTS+'public class ItemRouting{'+methods(route,['static boolean insertOne(', 'private static boolean tryPlace('])+'public static boolean tryPlaceForProbe(Container c,int s,ItemStack v){return tryPlace(c,s,v);}}'
 for name in ['InvWrapper','SidedInvWrapper']:
  text=(FIX/(name+'.java')).read_text()
  mix=src('mixin/'+name+'SlotCapacityMixin.java')
  body=method(mix,'private void gonzo$slotLimit(')
  original=method(text,'public int getSlotLimit(')
  expression='getInv().getMaxStackSize()' if name=='InvWrapper' else 'slotLimit.applyAsInt(slot)'
  replacement='''public int getSlotLimit(int slot){CallbackInfoReturnable<Integer> cir=new CallbackInfoReturnable<>(EXPR);gonzo$slotLimit(slot,cir);return cir.getReturnValue();}'''.replace('EXPR',expression)
  text=text.replace(original,replacement).replace('import net.minecraft.world.item.ItemStack;', 'import net.minecraft.world.item.ItemStack;import com.gonzotech.machines.inventory.SlotCapacity;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;')
  text=text[:text.rfind('}')]+body+'\n}'
  out['net/neoforged/neoforge/items/wrapper/'+name+'.java']=text
  out['com/gonzotech/mixin/'+name+'SlotCapacityMixin.java']=mix
 for cls in ['BrewingStandBlockEntity','AbstractFurnaceBlockEntity']:
  out['net/minecraft/world/level/block/entity/'+cls+'.java']='package net.minecraft.world.level.block.entity;public class '+cls+' extends probe.Base{}'
 h=(FIX/'HopperMethods.java.txt').read_text().replace('@Nullable ','').replace('tryMoveInItem(', 'rawMove(')
 hook=src('mixin/HopperSlotCapacityMixin.java')
 out['net/minecraft/world/level/block/entity/HopperBlockEntity.java']='''package net.minecraft.world.level.block.entity;'''+IMPORTS+'''import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
public class HopperBlockEntity extends probe.Base{long tickedGameTime;boolean isOnCustomCooldown(){return false;}void setCooldown(int n){}public static ItemStack move(Container to,ItemStack stack,int slot,Direction d){return gonzo$admit(null,to,stack,slot,d,a->rawMove((Container)a[0],(Container)a[1],(ItemStack)a[2],(int)a[3],(Direction)a[4]));}
'''+h+method(hook,'private static ItemStack gonzo$admit(')+'}'
 out['com/gonzotech/mixin/HopperSlotCapacityMixin.java']=hook
 return out

def run(overrides=None):
 home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else shutil.which('java');javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
 compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-nowarn'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
 with tempfile.TemporaryDirectory(prefix='gonzo-slot-capacity-') as tmp:
  root=Path(tmp);paths=[]
  for name,text in sources(overrides).items():
   p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);paths.append(str(p))
  p=subprocess.run(compiler+['-d',str(root/'classes')]+paths,capture_output=True,text=True)
  assert p.returncode==0,'compiler failure is not a mutation kill:\n'+p.stdout+p.stderr
  return subprocess.run([java,'-cp',str(root/'classes'),'probe.Probe'],capture_output=True,text=True)

if __name__=='__main__':
 for record in json.loads((FIX/'manifest.json').read_text())['files']:
  assert hashlib.sha256((FIX/record['file']).read_bytes()).hexdigest()==record['sha256'],record
 config=json.loads((ROOT/'src/main/resources/gonzotech.mixins.json').read_text())['mixins']
 for name in ['InvWrapperSlotCapacityMixin','SidedInvWrapperSlotCapacityMixin','HopperSlotCapacityMixin']:assert name in config,name
 # Check injector declaration/registration as wiring evidence, not runtime weaving.
 for name in ['InvWrapper','SidedInvWrapper']:
  hook=read('mixin/'+name+'SlotCapacityMixin.java')
  assert '@Mixin('+name+'.class)' in hook
  assert '@Inject(method = "getSlotLimit", at = @At("RETURN"), cancellable = true)' in hook
 assert '@WrapMethod(method = "tryMoveInItem")' in read('mixin/HopperSlotCapacityMixin.java')
 assert 'popResource' not in method(read('machines/block/entity/SeismicSourceBlockEntity.java'),'public void setItem(')
 for name in ['PlotnickMenu','SeismicSourceMenu','ReactorMenu']:
  assert 'return 1;' in method(read('machines/menu/'+name+'.java'),'public int getMaxStackSize(')
 result=run();assert result.returncode==0,result.stdout+result.stderr;print(result.stdout.strip())
 if '--mutation-check' in sys.argv:
  mutations=[
   ('machines/inventory/SlotCapacity.java','Math.min(limit, slots.slotCapacity(slot))','limit'),
   ('machines/inventory/SlotCapacity.java','if (!stack.isEmpty() && stack.getCount() >','if (false && stack.getCount() >'),
   ('machines/inventory/SlotCapacity.java','inventory.getMaxStackSize() >= stack.getMaxStackSize()','true'),
   ('machines/block/entity/PlotnickBlockEntity.java','? 1 : MAX_STACK','? MAX_STACK : MAX_STACK'),
   ('mixin/InvWrapperSlotCapacityMixin.java','if (wrapper.getInv() instanceof SlotCapacity)','if (false)'),
   ('mixin/SidedInvWrapperSlotCapacityMixin.java','if (inv instanceof SlotCapacity)','if (false)'),
   ('mixin/SidedInvWrapperSlotCapacityMixin.java','SlotCapacity.limit(inv, inventorySlot)','SlotCapacity.limit(inv, slot)'),
   ('mixin/HopperSlotCapacityMixin.java','offered, slot, direction','stack, slot, direction'),
   ('machines/network/ItemRouting.java','SlotCapacity.limit(dst, slot)','SlotCapacity.limit(dst, 0)'),
   ('machines/block/entity/PlotnickBlockEntity.java','isAllowedCard(slot, stack) && items.get(slot).isEmpty()','isAllowedCard(slot, stack)'),
  ]
  for path,old,new in mutations:
   text=read(path);assert text.count(old)==1,(path,old);result=run({path:text.replace(old,new)})
   assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived or failed for another reason: '+path+' '+new+'\n'+result.stdout+result.stderr
   print('REJECTED:',path,old,'->',new)
  print(f'Mutation sensitivity: {len(mutations)}/{len(mutations)} assertion-killed')
