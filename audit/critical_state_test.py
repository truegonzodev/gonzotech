#!/usr/bin/env python3
"""CRIT-1/2 behavioral regressions and baseline negatives.

Compiles whole production ReactorFuelInventory/ReactorMenu/BaseMachineMenu,
ExternalRadiation/DoseMath/MenuDoseTransaction, extracted panel lifecycle methods,
and hash-pinned upstream handlers with the actual production hook bodies inserted.
MC world/click/close/storage and copy injections are explicit DOUBLES, NOT native
Mixin/Mojmap/GUI/persistence evidence. Native CriticalStateGameTests require MC.
334 additionally compiles the failing native caller against pinned API signatures;
this still does not provide the complete Minecraft/NeoForge classpath.
Java21 javac or JAVA_HOME + ECJ_JAR; --mutation-check also rejects old462183e
and targeted mutants by AssertionError, never by a compile failure.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile

from _radiation_contracts import method
import slot_capacity_test as capacity

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/gonzotech'
BASELINE = '462183e02411427b33838f06a492e21eed7dc881'

EXTRA = {
'com/llamalad7/mixinextras/sugar/Local.java': 'package com.llamalad7.mixinextras.sugar;public @interface Local{int ordinal();}',
'org/spongepowered/asm/mixin/injection/At.java': 'package org.spongepowered.asm.mixin.injection;public @interface At{String value();String target() default "";}',
'org/spongepowered/asm/mixin/injection/Redirect.java': 'package org.spongepowered.asm.mixin.injection;public @interface Redirect{String[] method();At at();int require() default -1;}',
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;public record BlockPos(int x,int y,int z){public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}public BlockPos immutable(){return this;}}''',
'net/minecraft/core/NonNullList.java': '''package net.minecraft.core;public class NonNullList<T> extends java.util.ArrayList<T>{public static<T>NonNullList<T> withSize(int n,T v){var l=new NonNullList<T>();for(int i=0;i<n;i++)l.add(v);return l;}}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.entity.BlockEntity;public class Level{public boolean client;public java.util.Map<BlockPos,BlockEntity> entities=new java.util.HashMap<>();public java.util.Set<BlockPos> cold=new java.util.HashSet<>();public boolean isClientSide(){return client;}public boolean hasChunkAt(BlockPos p){return !cold.contains(p);}public BlockEntity getBlockEntity(BlockPos p){if(!hasChunkAt(p))throw new AssertionError("cold BE lookup");return entities.get(p);}}''',
'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;public class ServerLevel extends net.minecraft.world.level.Level{}''',
'net/minecraft/world/level/block/entity/BlockEntity.java': '''package net.minecraft.world.level.block.entity;import net.minecraft.core.BlockPos;import net.minecraft.world.level.Level;public class BlockEntity{public Level level;public BlockPos pos;public boolean removed;public Level getLevel(){return level;}public BlockPos getBlockPos(){return pos;}public boolean isRemoved(){return removed;}public void setChanged(){}}''',
'net/minecraft/world/Container.java': '''package net.minecraft.world;import net.minecraft.world.item.ItemStack;import net.minecraft.world.entity.player.Player;import net.minecraft.world.level.block.entity.BlockEntity;public interface Container{int getContainerSize();ItemStack getItem(int s);void setItem(int s,ItemStack v);void setChanged();int getMaxStackSize();boolean canPlaceItem(int s,ItemStack v);ItemStack removeItem(int s,int n);boolean isEmpty();default ItemStack removeItemNoUpdate(int s){return removeItem(s,999);}default void clearContent(){}default boolean stillValid(Player p){return true;}static boolean stillValidBlockEntity(BlockEntity b,Player p){return !b.isRemoved()&&b.getLevel()==p.level()&&p.near&&b.getLevel().hasChunkAt(b.pos)&&b.getLevel().getBlockEntity(b.pos)==b;}}''',
'net/minecraft/world/ContainerHelper.java': '''package net.minecraft.world;import java.util.List;import net.minecraft.world.item.ItemStack;public class ContainerHelper{public static ItemStack removeItem(List<ItemStack> l,int s,int n){return l.get(s).split(n);}public static ItemStack takeItem(List<ItemStack>l,int s){return l.set(s,ItemStack.EMPTY);}}''',
'net/minecraft/world/entity/player/Inventory.java': '''package net.minecraft.world.entity.player;public class Inventory extends probe.Base{public final Player player;public Inventory(Player p){player=p;}}''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;import net.minecraft.world.level.Level;import net.minecraft.world.inventory.AbstractContainerMenu;import net.minecraft.world.item.ItemStack;public class Player{public Level world;public boolean near=true;public AbstractContainerMenu containerMenu;public int closes,returned;public final Inventory inventory=new Inventory(this);public Player(Level l){world=l;}public Level level(){return world;}public void closeContainer(){closes++;returned+=containerMenu.getCarried().getCount();containerMenu.setCarried(ItemStack.EMPTY);containerMenu=null;}}''',
'net/minecraft/world/inventory/ClickType.java': '''package net.minecraft.world.inventory;public enum ClickType{PICKUP,QUICK_MOVE,QUICK_CRAFT,SWAP,THROW,PICKUP_ALL,CLONE}''',
'net/minecraft/world/inventory/Slot.java': '''package net.minecraft.world.inventory;import net.minecraft.world.Container;import net.minecraft.world.item.ItemStack;public class Slot{public final Container container;public final int slot;public Slot(Container c,int s,int x,int y){container=c;slot=s;}public ItemStack getItem(){return container.getItem(slot);}public boolean hasItem(){return !getItem().isEmpty();}public void setByPlayer(ItemStack s){container.setItem(slot,s);}public void setChanged(){container.setChanged();}public boolean mayPlace(ItemStack s){return true;}public int getMaxStackSize(){return container.getMaxStackSize();}}''',
'net/minecraft/world/inventory/ContainerData.java': '''package net.minecraft.world.inventory;public interface ContainerData{int get(int n);void set(int n,int v);int getCount();}''',
'net/minecraft/world/inventory/MenuType.java': '''package net.minecraft.world.inventory;public class MenuType<T>{}''',
'net/minecraft/world/inventory/AbstractContainerMenu.java': '''package net.minecraft.world.inventory;import net.minecraft.world.item.ItemStack;import net.minecraft.world.entity.player.Player;public abstract class AbstractContainerMenu{public final java.util.List<Slot> slots=new java.util.ArrayList<>();private ItemStack carried=ItemStack.EMPTY;public int delegateCalls;public AbstractContainerMenu(MenuType<?>t,int id){}public void addDataSlots(ContainerData d){}public Slot addSlot(Slot s){slots.add(s);return s;}public ItemStack getCarried(){return carried;}public void setCarried(ItemStack s){carried=s;}public abstract boolean stillValid(Player p);public abstract ItemStack quickMoveStack(Player p,int i);protected boolean moveItemStackTo(ItemStack s,int a,int b,boolean rev){delegateCalls++;return false;}public void clicked(int s,int b,ClickType t,Player p){delegateCalls++;if(t==ClickType.QUICK_MOVE){quickMoveStack(p,s);return;}if(s>=0&&s<slots.size()&&!carried.isEmpty())slots.get(s).setByPlayer(carried.split(1));}}''',
'net/minecraft/network/RegistryFriendlyByteBuf.java': '''package net.minecraft.network;public class RegistryFriendlyByteBuf{public int readVarInt(){return 3;}}''',
'com/gonzotech/machines/menu/MenuHelper.java': '''package com.gonzotech.machines.menu;import net.minecraft.world.entity.player.Inventory;import net.minecraft.network.RegistryFriendlyByteBuf;public class MenuHelper{public static<T>T readBlockEntity(Inventory i,RegistryFriendlyByteBuf b,Class<T> c){throw new UnsupportedOperationException();}}''',
'com/gonzotech/machines/registry/ModMenus.java': '''package com.gonzotech.machines.registry;public class ModMenus{public static final Ref REACTOR=new Ref();public static class Ref{public net.minecraft.world.inventory.MenuType<?> get(){return new net.minecraft.world.inventory.MenuType<>();}}}''',
'com/gonzotech/core/registry/ModItems.java': '''package com.gonzotech.core.registry;public class ModItems{public static final Ref UF_TVEL=new Ref();public static class Ref{private final net.minecraft.world.item.Item item=new net.minecraft.world.item.Item();public net.minecraft.world.item.Item get(){return item;}}}''',
'com/gonzotech/machines/block/entity/ReactorFuelSheathBlockEntity.java': '''package com.gonzotech.machines.block.entity;import net.minecraft.world.item.ItemStack;import net.minecraft.core.BlockPos;public class ReactorFuelSheathBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity{public static final int MAX_TVEL_SLOTS=18;public ItemStack fuel=ItemStack.EMPTY;public BlockPos min,max;public BlockPos reactorMin(){return min;}public BlockPos reactorMax(){return max;}public ItemStack getItem(int i){return fuel;}public void setItem(int i,ItemStack s){fuel=s;}public ItemStack removeItem(int i,int n){return fuel.split(n);}public ItemStack removeItemNoUpdate(int i){ItemStack r=fuel;fuel=ItemStack.EMPTY;return r;}public void clearContent(){fuel=ItemStack.EMPTY;}}''',
'com/gonzotech/radiation/RadSources.java': '''package com.gonzotech.radiation;public class RadSources{public static java.util.List<net.minecraft.world.item.ItemStack> contentsOf(net.minecraft.world.item.ItemStack s){return java.util.List.of();}}''',
}

PROBE = r'''package probe;
import net.minecraft.core.*;import net.minecraft.server.level.*;import net.minecraft.world.entity.player.*;import net.minecraft.world.inventory.*;import net.minecraft.world.item.*;import net.neoforged.neoforge.items.wrapper.*;import com.gonzotech.machines.block.entity.*;import com.gonzotech.machines.menu.*;import com.gonzotech.machines.reactor.*;import com.gonzotech.radiation.engine.*;
public class CriticalProbe{
static int checks;static void eq(double a,double b,String why){checks++;if(Math.abs(a-b)>0.000001)throw new AssertionError(why+": "+a+" != "+b);}static void yes(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
static class Fixture{ServerLevel level=new ServerLevel();ReactorControlPanelBlockEntity panel=new ReactorControlPanelBlockEntity();ReactorFuelSheathBlockEntity sheath=new ReactorFuelSheathBlockEntity();BlockPos min=new BlockPos(0,0,0),max=new BlockPos(3,3,3);Player player=new Player(level);ReactorMenu menu;
Fixture(){panel.level=level;panel.pos=new BlockPos(0,1,1);sheath.level=level;sheath.pos=new BlockPos(1,1,1);sheath.min=min;sheath.max=max;level.entities.put(panel.pos,panel);level.entities.put(sheath.pos,sheath);bind();menu=new ReactorMenu(1,player.inventory,panel,3);player.containerMenu=menu;menu.setCarried(new ItemStack(com.gonzotech.core.registry.ModItems.UF_TVEL.get(),1));}
void bind(){panel.configureStructure(min,max,1,0,new long[0],new long[0],new long[0]);}}
static void reactor(){
 Fixture good=new Fixture();yes(good.menu.stillValid(good.player),"fresh menu valid");good.menu.clicked(0,0,ClickType.PICKUP,good.player);eq(good.sheath.fuel.getCount(),1,"fresh writes live owner");eq(good.menu.getCarried().getCount(),0,"fresh cursor debited exactly once");
 for(int change=0;change<12;change++)for(ClickType action:ClickType.values())for(boolean directShift:new boolean[]{false,true}){
  Fixture f=new Fixture();var inventory=new ReactorFuelInventory(f.panel,3);var old=f.sheath;
  switch(change){
   case 0,9->{var n=new ReactorFuelSheathBlockEntity();n.level=f.level;n.pos=old.pos;n.min=f.min;n.max=f.max;f.level.entities.put(n.pos,n);old.removed=change==0;}
   case 1->{f.panel.clearStructure();f.bind();}
   case 2->{f.bind();}
   case 3->{f.level.cold.add(old.pos);}
   case 4->{var p=new ReactorControlPanelBlockEntity();p.level=f.level;p.pos=f.panel.pos;f.level.entities.put(p.pos,p);}
   case 5->{old.level=new ServerLevel();}
   case 6->{old.min=new BlockPos(9,9,9);}
   case 7->{f.panel.removed=true;}
   case 8->{old.removed=true;old.removed=false;f.panel.clearStructure();f.bind();}
   case 10->{f.level.cold.add(f.panel.pos);}
   case 11->{f.level.cold.add(new BlockPos(8,0,8));}
  }
  yes(!f.menu.stillValid(f.player),"stale menu invalid change="+change);
  if(directShift)f.menu.quickMoveStack(f.player,0);else f.menu.clicked(0,0,action,f.player);eq(f.player.closes,1,"stale entry closes before action");eq(f.player.returned,1,"stale entry returns cursor");
  f.menu.clicked(0,0,action,f.player);eq(f.menu.delegateCalls,0,"reject before vanilla debit, "+action);eq(f.player.closes,1,"GUI closed");eq(f.player.returned,1,"cursor returned once");eq(old.fuel.getCount(),0,"no removed owner write");
  f.menu.quickMoveStack(f.player,0);eq(f.menu.delegateCalls,0,"direct stale shift guarded");eq(f.player.returned,1,"late click no double return");
  inventory.setItem(0,new ItemStack(com.gonzotech.core.registry.ModItems.UF_TVEL.get(),1));eq(old.fuel.getCount(),0,"raw container cannot mutate stale owner");
 }
 Fixture readers=new Fixture();var second=new ReactorMenu(2,readers.player.inventory,readers.panel,3);readers.panel.clearStructure();readers.bind();yes(!readers.menu.stillValid(readers.player)&&!second.stillValid(readers.player),"both viewers invalidated");var reopened=new ReactorMenu(3,readers.player.inventory,readers.panel,3);yes(reopened.stillValid(readers.player),"explicit reopen valid");
 Fixture raw=new Fixture();raw.sheath.fuel=new ItemStack(com.gonzotech.core.registry.ModItems.UF_TVEL.get(),1);var view=new ReactorFuelInventory(raw.panel,3);raw.sheath.removed=true;yes(view.getItem(0).isEmpty(),"no stale owner read");yes(view.removeItem(0,1).isEmpty(),"no stale extraction");yes(view.removeItemNoUpdate(0).isEmpty(),"no stale take");view.clearContent();eq(raw.sheath.fuel.getCount(),1,"no stale clear");
 Fixture foreign=new Fixture();foreign.panel.clearStructure();foreign.bind();foreign.player.containerMenu=new ReactorMenu(2,foreign.player.inventory,foreign.panel,3);foreign.menu.clicked(0,0,ClickType.PICKUP,foreign.player);eq(foreign.player.closes,0,"late stale menu must not close another GUI");eq(foreign.menu.delegateCalls,0,"late foreign click rejected");
 Fixture far=new Fixture();far.player.near=false;far.menu.clicked(0,0,ClickType.PICKUP,far.player);eq(far.player.returned,1,"distance invalidation preserves cursor");
}
static Item item=new Item();static ItemStack stack(int n,double rate){ItemStack s=new ItemStack(item,n);ExternalRadiation.write(s,rate,100);return s;}static double mass(ItemStack s){return s.getCount()*ExternalRadiation.read(s).rate();}
static class Box extends Base implements com.gonzotech.machines.inventory.SlotCapacity{int limit;boolean allowed=true;Box(int l){limit=l;}public int[]getSlotsForFace(Direction d){return new int[]{1,0};}public boolean canPlaceItemThroughFace(int s,ItemStack v,Direction d){return allowed;}public int slotCapacity(int s){return s==1?limit:64;}}
static void radiation(){
 for(boolean ordinary:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})for(int before:new int[]{0,1,4,60,64})for(int offered:new int[]{1,4,16,64})for(int limit:new int[]{8,64})for(boolean allow:new boolean[]{false,true}){
  Box box=new Box(limit);box.allowed=allow;if(before>0)box.items.set(1,stack(before,reverse?6:2));ItemStack source=stack(offered,reverse?2:6);double total=mass(source)+mass(box.getItem(1));var handler=ordinary?new InvWrapper(box):new SidedInvWrapper(box,Direction.NORTH);int slot=ordinary?1:0,dirty=box.changes;
  var simulated=handler.insertItem(slot,source,true);eq(box.getItem(1).getCount(),before,"simulate count");eq(mass(source)+mass(box.getItem(1)),total,"simulate mass");eq(box.changes,dirty,"simulate dirty");
  var left=handler.insertItem(slot,source,false);eq(left.getCount(),simulated.getCount(),"simulate prediction");eq(left.getCount()+box.getItem(1).getCount(),offered+before,"count conservation");eq(mass(left)+mass(box.getItem(1)),total,"induced mass conservation sided="+!ordinary+" reversed="+reverse);eq(source.getCount(),offered,"caller count");eq(ExternalRadiation.read(source).rate(),reverse?2:6,"caller dose");
 }
 for(boolean reverse:new boolean[]{false,true}){Box box=new Box(64);box.items.set(1,stack(4,reverse?6:2));Fixture f=new Fixture();var menu=f.menu;menu.slots.clear();menu.slots.add(new Slot(box,1,0,0));menu.setCarried(stack(4,reverse?2:6));try(var outer=new MenuDoseTransaction(menu,ClickType.PICKUP)){menu.setCarried(new SidedInvWrapper(box,Direction.NORTH).insertItem(0,menu.getCarried(),false));eq(mass(box.getItem(1)),32,"before outer close");}eq(mass(box.getItem(1)),32,"outer reconciliation no double apply");}
 Box incompatible=new Box(64);incompatible.items.set(1,stack(4,2));var offer=new ItemStack(new Item(),4);ExternalRadiation.write(offer,6,100);eq(new SidedInvWrapper(incompatible,Direction.NORTH).insertItem(0,offer,false).getCount(),4,"different type refused");eq(mass(incompatible.getItem(1)),8,"different type dose unchanged");
}
public static void main(String[]args){if(args[0].equals("reactor"))reactor();else radiation();System.out.println(args[0]+": "+checks+" assertions PASS");}}
'''


def sources(overrides=None, old=False):
    overrides = overrides or {}
    def read(path):
        if path in overrides:
            return overrides[path]
        if old and path in ('machines/reactor/ReactorFuelInventory.java', 'machines/menu/ReactorMenu.java',
                            'machines/block/entity/ReactorControlPanelBlockEntity.java'):
            return subprocess.check_output(['git', 'show', BASELINE + ':src/main/java/com/gonzotech/' + path], text=True)
        return (JAVA / path).read_text()
    # Reuse the documented slot-admission fixture integration, without executing its probe.
    out = capacity.sources()
    out.update(EXTRA)
    stack = out['net/minecraft/world/item/ItemStack.java']
    stack = stack.replace('return new ItemStack(item,n,component);', 'ItemStack c=new ItemStack(item,n,component);com.gonzotech.radiation.engine.ExternalRadiation.copy(this,c);return c;')
    stack = stack.replace('public static boolean isSameItemSameComponents', 'public static boolean matches(ItemStack a,ItemStack b){return a.count==b.count&&isSameItemSameComponents(a,b);}public static boolean isSameItemSameComponents')
    out['net/minecraft/world/item/ItemStack.java'] = stack
    for path in ['machines/reactor/ReactorFuelInventory.java', 'machines/menu/ReactorMenu.java',
                 'machines/menu/BaseMachineMenu.java', 'radiation/engine/ExternalRadiation.java',
                 'radiation/engine/DoseMath.java', 'radiation/engine/MenuDoseTransaction.java']:
        out['com/gonzotech/' + path] = read(path)
    panel = read('machines/block/entity/ReactorControlPanelBlockEntity.java')
    pieces = [method(panel, s) for s in ['public static int fuelSlotsForSheaths(int sheathCount)',
              'public static int fuelSlotsForSheaths(int sheathCount,', 'public boolean isFormedController(',
              'public void configureStructure(', 'public void clearStructure(', 'public void syncMenuFuelSlotCount(']]
    pieces += [method(panel, 'public long fuelInventoryRevision(')] if 'public long fuelInventoryRevision(' in panel else []
    out['com/gonzotech/machines/block/entity/ReactorControlPanelBlockEntity.java'] = '''package com.gonzotech.machines.block.entity;
import net.minecraft.core.BlockPos;import net.minecraft.world.inventory.*;
public class ReactorControlPanelBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity{
public static final int MAX_FUEL_SLOTS=18;private long fuelInventoryRevision;private boolean formed,validationPending;private BlockPos min,max;private int fuelSlotCount;
static class Runtime{void configure(int n,long[]a,long[]b,long[]c){}void deconfigure(){}}private Runtime runtime=new Runtime();public BlockPos min(){return min;}public BlockPos max(){return max;}public int fuelSlotCount(){return fuelSlotCount;}public ContainerData menuData(){return new ContainerData(){public int get(int i){return 0;}public void set(int i,int v){}public int getCount(){return 20;}};}
''' + '\n'.join(pieces) + '}'
    storage = read('machines/reactor/ReactorFuelStorage.java')
    out['com/gonzotech/machines/reactor/ReactorFuelStorage.java'] = '''package com.gonzotech.machines.reactor;import net.minecraft.server.level.ServerLevel;import net.minecraft.core.BlockPos;import com.gonzotech.machines.block.entity.*;public class ReactorFuelStorage{public static java.util.List<ReactorFuelSheathBlockEntity> sheathsCached(ServerLevel l,ReactorControlPanelBlockEntity p){return l.entities.values().stream().filter(e->e instanceof ReactorFuelSheathBlockEntity).map(e->(ReactorFuelSheathBlockEntity)e).toList();}''' + method(storage, 'public static boolean canOpenFuelInventory(') + method(storage, 'private static boolean chunksLoaded(') + '}'
    for name in ('InvWrapper', 'SidedInvWrapper'):
        key = 'net/neoforged/neoforge/items/wrapper/' + name + '.java'
        mixpath = 'mixin/' + name + 'RadiationMixin.java'
        if old and name == 'SidedInvWrapper':
            continue
        hook = read(mixpath)
        body = method(hook, 'private void gonzo$merge(')
        text = out[key]
        assert text.count('copy.grow(stackInSlot.getCount());') == 2
        text = text.replace('copy.grow(stackInSlot.getCount());', 'gonzo$merge(copy,stackInSlot.getCount(),slot,stack,simulate,stackInSlot);')
        text = text.replace('import net.minecraft.world.item.ItemStack;', 'import net.minecraft.world.item.ItemStack;import com.gonzotech.radiation.engine.ExternalRadiation;import com.llamalad7.mixinextras.sugar.Local;')
        out[key] = text[:text.rfind('}')] + body + '\n}'
        out['com/gonzotech/' + mixpath] = hook
    out['probe/CriticalProbe.java'] = PROBE
    return out


def run(case, overrides=None, old=False):
    home = os.environ.get('JAVA_HOME')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    if not java:
        raise SystemExit('Java21 required: JAVA_HOME and javac or ECJ_JAR')
    compiler = [java, '-jar', os.environ['ECJ_JAR'], '-21', '-proc:none', '-nowarn'] if os.environ.get('ECJ_JAR') else [javac, '--release', '21']
    with tempfile.TemporaryDirectory(prefix='gonzotech-critical-') as temp:
        root = Path(temp)
        for path, text in sources(overrides, old).items():
            p = root / path
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)
        compiled = subprocess.run(compiler + ['-d', str(root / 'classes')] + [str(p) for p in root.rglob('*.java')], text=True, capture_output=True)
        assert compiled.returncode == 0, 'compilation failure is NOT a negative kill:\n' + compiled.stdout + compiled.stderr
        return subprocess.run([java, '-ea', '-cp', str(root / 'classes'), 'probe.CriticalProbe', case], text=True, capture_output=True)


def native_menu_api_regression():
    """Compile the actual failing caller against pinned API shapes, then execute it.

    This is a signature/fixture regression, NOT native Minecraft. The old caller
    must fail compilation here; that is not a behavioral mutation kill.
    """
    fixture = ROOT / 'audit/fixtures/chest_menu_api'
    metadata = json.loads((fixture / 'manifest.json').read_text())
    for record in metadata['files']:
        assert hashlib.sha256((fixture / record['file']).read_bytes()).hexdigest() == record['sha256']
    api = json.loads((fixture / 'signatures.json').read_text())
    home = os.environ.get('JAVA_HOME')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    compiler = [java, '-jar', os.environ['ECJ_JAR'], '-21', '-proc:none', '-nowarn'] if os.environ.get('ECJ_JAR') else [javac, '--release', '21']
    current = (JAVA / 'machines/reactor/CriticalStateGameTests.java').read_text()
    old = subprocess.check_output(['git', 'show', '31fc7ed43d1dbb37bdd3ec38da427882cd63fd6d:src/main/java/com/gonzotech/machines/reactor/CriticalStateGameTests.java'], text=True)
    # Constructor parameter names are taken from the pinned declaration too.
    params = [part.strip().split()[-1] for part in api['constructor'].split('(', 1)[1].rstrip(')').split(',')]
    kind, identifier, inventory, container, rows = params
    chest = '''package net.minecraft.world.inventory;
import net.minecraft.world.Container;import net.minecraft.world.entity.player.*;import net.minecraft.world.item.ItemStack;
public class ChestMenu extends AbstractContainerMenu {
private final Container box;private final int rows;
CTOR {super(KIND,ID);box=BOX;rows=ROWS;if(box.getContainerSize()!=rows*9)throw new AssertionError("container/rows mismatch");for(int i=0;i<rows*9;i++)addSlot(new Slot(box,i,0,0));for(int i=0;i<36;i++)addSlot(new Slot(INV,i,0,0));}
FACTORY {throw new UnsupportedOperationException("factory has its own storage; must not be used by this test");}
public Container getContainer(){return box;}public int getRowCount(){return rows;}
public boolean stillValid(Player p){return true;}public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}}
'''.replace('FACTORY', api['one_row']).replace('CTOR', api['constructor'])
    for token, replacement in [('KIND', kind), ('ID', identifier), ('BOX', container), ('ROWS', rows), ('INV', inventory)]:
        chest = chest.replace(token, replacement)
    common = sources()
    common['net/minecraft/world/inventory/ChestMenu.java'] = chest
    common['net/minecraft/world/inventory/MenuType.java'] = 'package net.minecraft.world.inventory;public class MenuType<T>{public static final MenuType<?> GENERIC_9x1=new MenuType<>();}'
    common['net/minecraft/world/SimpleContainer.java'] = 'package net.minecraft.world;import net.minecraft.world.item.ItemStack;public class SimpleContainer extends probe.Base{public SimpleContainer(int n){items.clear();for(int i=0;i<n;i++)items.add(ItemStack.EMPTY);}}'
    common['net/minecraft/world/level/GameType.java'] = 'package net.minecraft.world.level;public enum GameType{SURVIVAL}'
    common['net/minecraft/gametest/framework/GameTestHelper.java'] = 'package net.minecraft.gametest.framework;import net.minecraft.world.entity.player.Player;import net.minecraft.world.level.GameType;public class GameTestHelper{public int assertions;public boolean done;public Player makeMockPlayer(GameType t){return new Player(new net.minecraft.server.level.ServerLevel());}public void assertTrue(boolean v,String message){assertions++;if(!v)throw new AssertionError(message);}public void succeed(){done=true;}}'
    key = 'net/minecraft/world/entity/player/Player.java'
    common[key] = common[key].replace('public Level level()', 'public Inventory getInventory(){return inventory;}public Level level()')
    key = 'net/minecraft/world/item/Items.java'
    common[key] = common[key].replace('TNT=new Item()', 'DIAMOND=new Item(),TNT=new Item()')
    common['com/gonzotech/radiation/RadUnits.java'] = (JAVA / 'radiation/RadUnits.java').read_text()
    prefix = '''package probe;
import com.gonzotech.radiation.engine.*;import com.gonzotech.radiation.RadUnits;import com.gonzotech.machines.inventory.SlotCapacity;
import net.minecraft.core.Direction;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.inventory.*;import net.minecraft.gametest.framework.GameTestHelper;import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
public class NativeMenuProbe {
'''
    helper_signatures = ['private static final class SidedBox', 'private static ItemStack stack(', 'private static double mass(', 'private static void eq(']
    helpers = '\n'.join(method(current, sig) for sig in helper_signatures)
    for negative, source in [(False, current), (True, old)]:
        test = method(source, 'public static void sidedInsertInsideMenuTransaction(')
        probe = prefix + helpers + test + '\npublic static void main(String[]args){var h=new GameTestHelper();sidedInsertInsideMenuTransaction(h);if(!h.done||h.assertions!=10)throw new AssertionError("binding/layout and both directions must execute");System.out.println("native caller API fixture:10 assertions PASS (MC doubles)");}}'
        with tempfile.TemporaryDirectory(prefix='gonzotech-menu-api-') as temp:
            root = Path(temp)
            for path, text in {**common, 'probe/NativeMenuProbe.java': probe}.items():
                p = root / path
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            compiled = subprocess.run(compiler + ['-d', str(root / 'classes')] + [str(p) for p in root.rglob('*.java')], text=True, capture_output=True)
            diagnostic = compiled.stdout + compiled.stderr
            if negative:
                assert 'ChestMenu.oneRow(40, player.getInventory(), box)' in test
                assert compiled.returncode and 'oneRow' in diagnostic and ('not applicable for the arguments' in diagnostic or 'cannot be applied' in diagnostic), diagnostic
                print('API compile-negative:exact333 oneRow(int,Inventory,Container) rejected1/1')
            else:
                assert compiled.returncode == 0, diagnostic
                result = subprocess.run([java, '-ea', '-cp', str(root / 'classes'), 'probe.NativeMenuProbe'], text=True, capture_output=True)
                assert result.returncode == 0, result.stdout + result.stderr
                print(result.stdout.strip())


def main():
    import _ver
    _ver.at_least("0.3.334", "CRIT-1/2 and native caller ChestMenu API regression")
    native_menu_api_regression()
    for record in json.loads((capacity.FIX / 'manifest.json').read_text())['files']:
        assert hashlib.sha256((capacity.FIX / record['file']).read_bytes()).hexdigest() == record['sha256']
    config = json.loads((ROOT / 'src/main/resources/gonzotech.mixins.json').read_text())['mixins']
    assert 'SidedInvWrapperRadiationMixin' in config
    hook = (JAVA / 'mixin/SidedInvWrapperRadiationMixin.java').read_text()
    assert '@Mixin(SidedInvWrapper.class)' in hook and 'require = 2' in hook and '@Local(ordinal = 1)' in hook
    panel = (JAVA / 'machines/block/entity/ReactorControlPanelBlockEntity.java').read_text()
    for sig in ['public void configureStructure(', 'public void clearStructure(', 'public void setRemoved(', 'protected void loadAdditional(']:
        assert 'fuelInventoryRevision++;' in method(panel, sig), sig
    for case in ('reactor', 'radiation'):
        result = run(case)
        assert result.returncode == 0, result.stdout + result.stderr
        print(result.stdout.strip())
    if '--mutation-check' in sys.argv:
        for case in ('reactor', 'radiation'):
            result = run(case, old=True)
            assert result.returncode and 'AssertionError' in result.stderr, result.stdout + result.stderr
            print('REJECTED exact462183e baseline:', case)
        mutations = [
            ('reactor', 'machines/menu/ReactorMenu.java', 'if (rejectStaleAction(player)) return;', 'if (false) return;'),
            ('reactor', 'machines/menu/ReactorMenu.java', 'if (rejectStaleAction(player)) return ItemStack.EMPTY;', 'if (false) return ItemStack.EMPTY;'),
            ('reactor', 'machines/reactor/ReactorFuelInventory.java', 'panel.fuelInventoryRevision() != revision', 'false'),
            ('reactor', 'machines/reactor/ReactorFuelInventory.java', 'panel.getLevel().getBlockEntity(sheath.getBlockPos()) == sheath', 'true'),
            ('reactor', 'machines/reactor/ReactorFuelInventory.java', 'panel.min().equals(sheath.reactorMin())', 'true'),
            ('radiation', 'mixin/SidedInvWrapperRadiationMixin.java', 'ExternalRadiation.merge(destination, existing, amount);', ''),
            ('radiation', 'mixin/SidedInvWrapperRadiationMixin.java', 'merge(destination, existing, amount)', 'merge(destination, source, amount)'),
        ]
        for case, path, before, after in mutations:
            text = (JAVA / path).read_text()
            assert text.count(before) == 1, (path, before)
            result = run(case, {path: text.replace(before, after)})
            assert result.returncode and 'AssertionError' in result.stderr, 'mutant survived/non-assertion failure: ' + path + '\n' + result.stdout + result.stderr
            print('REJECTED:', path, before)
        print('Baseline2/2; mutations7/7 assertion-killed (compilation failures never counted)')


if __name__ == '__main__':
    main()
