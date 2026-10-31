#!/usr/bin/env python3
"""PERF-001/5: compile/run the whole production ItemRouting against an observed
loaded-chunk world boundary. Every unloaded state/container/inventory read fails.
No native Minecraft/GameTest, chunk lifecycle, chest combiner or TPS claim.
--mutation-check requires AssertionError, never a compiler failure.
"""
from pathlib import Path
import os,shutil,subprocess,sys,tempfile,hashlib,json
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/com/gonzotech'
SOURCE=JAVA/'machines/network/ItemRouting.java'
FILTER=JAVA/'machines/network/ItemFilterRouting.java'
STUBS={
'net/minecraft/core/Direction.java':'''package net.minecraft.core;public enum Direction{DOWN(0,-1,0),UP(0,1,0),NORTH(0,0,-1),SOUTH(0,0,1),WEST(-1,0,0),EAST(1,0,0);final int x,y,z;Direction(int x,int y,int z){this.x=x;this.y=y;this.z=z;}public int getStepX(){return x;}public int getStepY(){return y;}public int getStepZ(){return z;}public Direction getOpposite(){return values()[ordinal()^1];}}''',
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core;public record BlockPos(int x,int y,int z){public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}public long asLong(){return (x&0x3ffffffL)<<38|(z&0x3ffffffL)<<12|(y&0xfffL);}public BlockPos relative(Direction d){return new BlockPos(x+d.getStepX(),y+d.getStepY(),z+d.getStepZ());}}''',
'net/minecraft/world/item/Item.java':'package net.minecraft.world.item;public class Item{}',
'net/minecraft/world/item/ItemStack.java':'''package net.minecraft.world.item;public class ItemStack{public static final ItemStack EMPTY=new ItemStack(null,0);Item item;int count;public ItemStack(Item item,int count){this.item=item;this.count=count;}public Item getItem(){return item;}public int getCount(){return count;}public int getMaxStackSize(){return 64;}public boolean isEmpty(){return count<=0||item==null;}public ItemStack copy(){return copyWithCount(count);}public ItemStack copyWithCount(int n){return new ItemStack(item,n);}public void setCount(int n){count=n;}public void grow(int n){count+=n;}public void shrink(int n){count-=n;}public net.minecraft.core.component.DataComponents.CustomData get(Object key){return null;}public static boolean isSameItem(ItemStack a,ItemStack b){return a.item==b.item;}public static boolean isSameItemSameComponents(ItemStack a,ItemStack b){return a.item==b.item;}}''',
'net/minecraft/world/entity/Entity.java':'package net.minecraft.world.entity;public class Entity{public java.util.UUID getUUID(){return new java.util.UUID(1,2);}}',
'com/gonzotech/machines/network/PipeRoutingDiagnostics.java':'package com.gonzotech.machines.network;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;public class PipeRoutingDiagnostics{public static boolean itemEnabled(Level l){return l.diagnostics&&l.tick%20==0;}public static void itemEvent(Level l,BlockPos p,BlockPos e,String s,String d){if(itemEnabled(l))l.debug.add(s+":"+d);}}',
'net/minecraft/world/Container.java':'''package net.minecraft.world;import net.minecraft.world.item.ItemStack;public interface Container{int getContainerSize();ItemStack getItem(int s);void setItem(int s,ItemStack v);void setChanged();boolean canPlaceItem(int s,ItemStack v);int getMaxStackSize();ItemStack removeItem(int s,int n);}''',
'net/minecraft/world/CompoundContainer.java':'''package net.minecraft.world;import net.minecraft.world.item.ItemStack;public class CompoundContainer implements Container{final Container a,b;public CompoundContainer(Container a,Container b){this.a=a;this.b=b;}public boolean contains(Container c){return a==c||b==c;}public int getContainerSize(){return a.getContainerSize()+b.getContainerSize();}Container part(int s){return s<a.getContainerSize()?a:b;}int slot(int s){return s<a.getContainerSize()?s:s-a.getContainerSize();}public ItemStack getItem(int s){return part(s).getItem(slot(s));}public void setItem(int s,ItemStack v){part(s).setItem(slot(s),v);}public ItemStack removeItem(int s,int n){return part(s).removeItem(slot(s),n);}public void setChanged(){a.setChanged();b.setChanged();}public boolean canPlaceItem(int s,ItemStack v){return part(s).canPlaceItem(slot(s),v);}public int getMaxStackSize(){return a.getMaxStackSize();}}''',
'net/minecraft/world/WorldlyContainer.java':'''package net.minecraft.world;import net.minecraft.core.Direction;import net.minecraft.world.item.ItemStack;public interface WorldlyContainer extends Container{int[] getSlotsForFace(Direction d);boolean canPlaceItemThroughFace(int s,ItemStack v,Direction d);boolean canTakeItemThroughFace(int s,ItemStack v,Direction d);}''',
'com/mojang/serialization/MapCodec.java':'package com.mojang.serialization;public class MapCodec<T>{}',
'net/minecraft/world/level/block/Block.java':'package net.minecraft.world.level.block;import com.mojang.serialization.MapCodec;import java.util.function.Function;public class Block{public static class Properties{}public Block(){}public Block(Properties p){}protected MapCodec<? extends Block> codec(){return null;}public static <B extends Block>MapCodec<B>simpleCodec(Function<Properties,B>f){return null;}}',
'net/minecraft/world/level/block/state/properties/ChestType.java':'package net.minecraft.world.level.block.state.properties;public enum ChestType{SINGLE,LEFT,RIGHT}',
'net/minecraft/world/level/block/state/properties/EnumProperty.java':'package net.minecraft.world.level.block.state.properties;public class EnumProperty<T extends Enum<T>>{}',
'net/minecraft/world/level/block/ChestBlock.java':'''package net.minecraft.world.level.block;import net.minecraft.core.Direction;import net.minecraft.world.level.block.state.BlockState;import net.minecraft.world.level.block.state.properties.*;public class ChestBlock extends Block{public static final EnumProperty<ChestType> TYPE=new EnumProperty<>();public static Direction getConnectedDirection(BlockState s){return s.partner;}}''',
'net/minecraft/world/level/block/state/BlockState.java':'''package net.minecraft.world.level.block.state;import net.minecraft.world.level.block.Block;import net.minecraft.world.level.block.state.properties.*;import net.minecraft.core.Direction;public class BlockState{public final Block block;public final ChestType type;public final Direction partner;public BlockState(Block b){this(b,ChestType.SINGLE,Direction.EAST);}public BlockState(Block b,ChestType t,Direction d){block=b;type=t;partner=d;}public Block getBlock(){return block;}@SuppressWarnings("unchecked")public <T extends Enum<T>>T getValue(EnumProperty<T>p){return (T)type;}}''',
'com/gonzotech/machines/network/PipeType.java':'package com.gonzotech.machines.network;public enum PipeType{ITEM,WIRE,HEAT,WATER,STEAM;public int maxThroughput(){return 5;}}',
'com/gonzotech/machines/network/PipeMode.java':'package com.gonzotech.machines.network;public enum PipeMode{AUTO,PULL,PUSH;public boolean acceptsFromMachine(){return this!=PUSH;}public boolean deliversToMachine(){return this!=PULL;}}',
'com/gonzotech/machines/network/PipeCarrier.java':'''package com.gonzotech.machines.network;import net.minecraft.core.Direction;import net.minecraft.world.level.block.state.BlockState;public interface PipeCarrier{boolean carries(BlockState s,PipeType t);boolean opensToward(BlockState s,PipeType t,Direction d);PipeMode modeFor(BlockState s,PipeType t);default double throughputFactor(BlockState s,PipeType t){return 1;}default int throughputLimit(BlockState s,PipeType t){return 5;}default int perItemThroughputLimit(BlockState s,PipeType t){return 1;}}''',
'com/gonzotech/machines/network/PipeFlowWarnings.java':'package com.gonzotech.machines.network;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;public class PipeFlowWarnings{public static final int HOLD_TICKS=40;public static void mark(Level l,BlockPos p,long t){}}',
'com/gonzotech/machines/network/ItemFlowTracker.java':'package com.gonzotech.machines.network;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;import net.minecraft.world.item.Item;public class ItemFlowTracker{public static java.util.List<Integer>get(Level l,BlockPos p){return l.flow>0?java.util.List.of(l.flow):java.util.List.of();}public static void record(Level l,BlockPos p,Item i,int n){if(!l.hasChunkAt(p))throw new AssertionError("flow through unloaded pipe");l.flow+=n;}}',
'com/gonzotech/radiation/engine/ExternalRadiation.java':'package com.gonzotech.radiation.engine;import net.minecraft.world.item.ItemStack;public class ExternalRadiation{public static void merge(ItemStack a,ItemStack b,int n){}}',
'net/minecraft/world/level/block/entity/HopperBlockEntity.java':'package net.minecraft.world.level.block.entity;import net.minecraft.world.Container;import net.minecraft.core.BlockPos;import net.minecraft.world.level.Level;public class HopperBlockEntity{public static Container getContainerAt(Level l,BlockPos p){return l.lookup(p);}}',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;import java.util.*;import net.minecraft.core.*;import net.minecraft.world.Container;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.ChestType;
public class Level{public static long clock;public long tick=++clock;public int reads,lookups,illegalReads,flow,powerQueries;public final java.util.List<String>debug=new java.util.ArrayList<>();public boolean diagnostics,signal,wireFootprint,assembleChests;public boolean client;public BlockPos hookAt;public int hookAfter=1;public Runnable hook;public final Set<Long> chunks=new HashSet<>();public final Map<BlockPos,BlockState> states=new HashMap<>();public final Map<BlockPos,Container> containers=new HashMap<>();public static final BlockState AIR=new BlockState(new Block());
public boolean hasNeighborSignal(BlockPos p){powerQueries++;for(Direction d:Direction.values())getBlockState(p.relative(d));if(wireFootprint)getBlockState(new BlockPos(p.getX()+3,p.getY(),p.getZ()));return signal;}
public boolean isClientSide(){return client;}public long getGameTime(){return tick;}public static long key(BlockPos p){return ((long)(p.getX()>>4)<<32)^((p.getZ()>>4)&0xffffffffL);}public boolean hasChunkAt(BlockPos p){return chunks.contains(key(p));}public void load(BlockPos p){chunks.add(key(p));}public void unload(BlockPos p){chunks.remove(key(p));}
public BlockState getBlockState(BlockPos p){if(!hasChunkAt(p)){illegalReads++;throw new AssertionError("unloaded block read: "+p);}reads++;BlockState s=states.getOrDefault(p,AIR);if(p.equals(hookAt)&&hook!=null&&--hookAfter==0){Runnable r=hook;hook=null;r.run();}return s;}
public Object getBlockEntity(BlockPos p){if(!hasChunkAt(p)){illegalReads++;throw new AssertionError("unloaded BE read");}return containers.get(p);}
public Container lookup(BlockPos p){lookups++;BlockState s=getBlockState(p);if(s.getBlock() instanceof ChestBlock&&s.type!=ChestType.SINGLE){BlockPos other=p.relative(ChestBlock.getConnectedDirection(s));BlockState o=getBlockState(other);if(assembleChests&&o.getBlock() instanceof ChestBlock&&o.type!=ChestType.SINGLE&&o.partner==s.partner.getOpposite()&&containers.get(p)!=null&&containers.get(other)!=null)return new net.minecraft.world.CompoundContainer(containers.get(p),containers.get(other));}return containers.get(p);}}
''',
}
STUBS.update({
'net/minecraft/server/level/ServerLevel.java':'package net.minecraft.server.level;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.Block;import net.minecraft.core.*;public class ServerLevel extends Level{public int notifications;public void updateNeighbourForOutputSignal(BlockPos p,Block b){notifications++;for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){BlockPos n=p.relative(d);if(hasChunkAt(n)){getBlockState(n);getBlockState(n.relative(d));}}}}',
'com/gonzotech/machines/network/FlowTracker.java':'package com.gonzotech.machines.network;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;public class FlowTracker{public static long[]get(Level l,BlockPos p,PipeType t){return new long[0];}}',
'com/gonzotech/machines/energy/SecondTierDefs.java':'package com.gonzotech.machines.energy;/** Tier constants only: not the behaviour under test. */public class SecondTierDefs{public static final long ITEM_THROUGHPUT=10;public static final int ITEM_PER_TYPE_THROUGHPUT=2;}',
'net/minecraft/core/component/DataComponents.java': 'package net.minecraft.core.component;public class DataComponents{public static final Object CUSTOM_DATA=new Object();public static class CustomData{public Tag copyTag(){return new Tag();}}public static class Tag{public boolean contains(String k){return false;}public String getString(String k){return "";}}}',
'com/gonzotech/core/item/WasteProtection.java': 'package com.gonzotech.core.item;import net.minecraft.world.item.ItemStack;public class WasteProtection{public static boolean isProtected(ItemStack s){return false;}}',
'com/gonzotech/machines/network/ItemFilterBlock.java': 'package com.gonzotech.machines.network;import net.minecraft.world.level.block.Block;public class ItemFilterBlock extends Block{public int itemThroughputLimit(){return 5;}public int perItemThroughputLimit(){return 1;}}',
'com/gonzotech/machines/block/entity/ItemFilterBlockEntity.java': 'package com.gonzotech.machines.block.entity;import com.gonzotech.machines.network.LoadedProbe;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;import net.minecraft.world.item.*;public class ItemFilterBlockEntity extends LoadedProbe.Box{public boolean matched=true;public Runnable beforeEmpty;public ItemFilterBlockEntity(Level l,BlockPos p){super(l,p,0);}public boolean isEmpty(){if(beforeEmpty!=null){Runnable r=beforeEmpty;beforeEmpty=null;r.run();}return stack.isEmpty();}public boolean isFilterEmpty(){return matched;}public int filterSize(){return 1;}public ItemStack getFilter(int n){return new ItemStack(new Item(),1);}}',
})
PROBE=r'''package com.gonzotech.machines.network;
import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;import net.minecraft.server.level.ServerLevel;import java.util.*;import java.lang.reflect.*;import net.minecraft.core.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.ChestType;
public class LoadedProbe{
static int checks;static final Item ITEM=new Item();static void eq(int a,int b,String why){checks++;if(a!=b)throw new AssertionError(why+": "+a+" != "+b);}static void yes(boolean v,String why){eq(1,v?1:0,why);}static BlockPos p(int x){return new BlockPos(x,64,8);}static void clean(Level l){eq(0,l.illegalReads,"zero unloaded world reads");}
static class Pipe extends Block implements PipeCarrier{public boolean carries(BlockState s,PipeType t){return true;}public boolean opensToward(BlockState s,PipeType t,Direction d){return d==Direction.EAST||d==Direction.WEST;}public PipeMode modeFor(BlockState s,PipeType t){return PipeMode.AUTO;}}
static final BlockState PIPE=new BlockState(new Pipe());
public static class Box implements Container{final Level world;final BlockPos pos;public ItemStack stack;int writes;public Box(Level l,BlockPos p,int count){world=l;pos=p;stack=new ItemStack(ITEM,count);l.containers.put(p,this);}void live(){if(!world.hasChunkAt(pos))throw new AssertionError("unloaded inventory touched");}public int getContainerSize(){live();return 1;}public ItemStack getItem(int s){live();return stack;}public void setItem(int s,ItemStack v){live();stack=v;writes++;}public void setChanged(){live();}public boolean canPlaceItem(int s,ItemStack v){live();return true;}public int getMaxStackSize(){return 64;}public ItemStack removeItem(int s,int n){live();int k=Math.min(n,stack.getCount());stack.shrink(k);return new ItemStack(ITEM,k);}}
static Object call(String name,Class<?>[]types,Object...args){return callIn(ItemRouting.class,name,types,args);}static Object callIn(Class<?>owner,String name,Class<?>[]types,Object...args){try{Method m=owner.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof AssertionError a)throw a;throw new AssertionError(e.getCause());}catch(Exception e){throw new AssertionError(e);}}
static List<?> cache(Level l,BlockPos start){return (List<?>)call("sinksCached",new Class<?>[]{Level.class,BlockPos.class},l,start);}
static boolean live(Level l,Object sink){return (boolean)call("sinkLive",new Class<?>[]{Level.class,sink.getClass()},l,sink);}
static int push(Level l,BlockPos root,Box src,BlockPos srcPos,List<?>sinks){return (int)call("pushItems",new Class<?>[]{Level.class,BlockPos.class,Container.class,Direction.class,List.class,BlockPos.class,int.class,int.class,long.class},l,root,src,Direction.EAST,sinks,srcPos,5,1,l.getGameTime());}
static List<?> filterSinks(Level l,BlockPos root){List<Object>out=new ArrayList<>();callIn(ItemFilterRouting.class,"collectSinksFromCarrier",new Class<?>[]{Level.class,BlockPos.class,List.class,List.class},l,root,List.of(),out);return out;}
public static void main(String[]args){
 // Baseline: only one chunk loaded, valid source -> pipe -> sink still transfers.
 Level l=new Level();l.load(p(8));l.states.put(p(8),PIPE);Box src=new Box(l,p(7),16),dst=new Box(l,p(9),0);
 eq(1,ItemRouting.tickExtract(l,p(8),PIPE),"loaded transfer unchanged");eq(15,src.stack.getCount(),"accepted-only debit");eq(1,dst.stack.getCount(),"one delivered");eq(16,src.stack.getCount()+dst.stack.getCount(),"conservation");clean(l);
 // No source access across an unloaded boundary.
 l=new Level();l.load(p(16));l.states.put(p(16),PIPE);src=new Box(l,p(15),16);dst=new Box(l,p(17),0);
 eq(0,ItemRouting.tickExtract(l,p(16),PIPE),"unloaded source not read");eq(16,src.stack.getCount(),"unloaded source untouched");eq(0,dst.stack.getCount(),"no phantom delivery");clean(l);
 // No endpoint probing, including an unloaded plain pipe; real filter/scavenger router below.
 for(int kind=0;kind<2;kind++){
  l=new Level();l.load(p(15));l.states.put(p(15),PIPE);l.states.put(p(16),kind==1?PIPE:new BlockState(new Block()));src=new Box(l,p(14),16);dst=new Box(l,p(16),0);
  eq(0,ItemRouting.tickExtract(l,p(15),PIPE),"unloaded neighbour never probed");eq(16,src.stack.getCount(),"no debit to unloaded sink");eq(0,dst.stack.getCount(),"unloaded target untouched");yes(!l.hasChunkAt(p(16)),"routing cannot make adjacent chunk loaded");clean(l);
 }
 // Unloaded root cannot run even if passed a stale scheduled-tick state.
 l=new Level();l.states.put(p(15),PIPE);l.load(p(16));src=new Box(l,p(16),16);eq(0,ItemRouting.tickExtract(l,p(15),PIPE),"unloaded root no-op");eq(0,l.reads,"no ghost root read");clean(l);
 // Positive cached endpoint expires in the live check immediately after unload.
 l=new Level();l.load(p(15));l.load(p(16));l.states.put(p(15),PIPE);src=new Box(l,p(14),16);dst=new Box(l,p(16),0);List<?>sinks=cache(l,p(15));yes(!sinks.isEmpty(),"warm positive cache");l.unload(p(16));
 eq(0,ItemRouting.tickExtract(l,p(15),PIPE),"cached unloaded endpoint rejected before insertion");eq(16,src.stack.getCount(),"cache is not permission to debit");eq(0,dst.writes,"no unloaded setter");clean(l);
 // Source captured earlier disappears before push; held reference is not permission.
 l=new Level();l.load(p(15));l.load(p(16));l.states.put(p(16),PIPE);src=new Box(l,p(15),16);dst=new Box(l,p(17),0);sinks=cache(l,p(16));l.unload(p(15));
 eq(0,push(l,p(16),src,p(15),sinks),"captured source rechecked");eq(16,src.stack.getCount(),"captured source no debit");eq(0,dst.stack.getCount(),"captured source no delivery");clean(l);
 // A captured double-chest source needs BOTH chunks even if its near half stays loaded.
 l=new Level();l.load(p(15));l.load(p(16));l.states.put(p(14),PIPE);l.states.put(p(15),new BlockState(new ChestBlock(),ChestType.RIGHT,Direction.EAST));src=new Box(l,p(15),16);dst=new Box(l,p(13),0);sinks=cache(l,p(14));l.unload(p(16));
 eq(0,push(l,p(14),src,p(15),sinks),"captured chest companion rechecked");eq(16,src.stack.getCount(),"half-unloaded chest donor retained");eq(0,dst.stack.getCount(),"half-unloaded chest no delivery");clean(l);
 // Entire saved route, not just its endpoints, must remain loaded.
 l=new Level();l.load(p(15));l.load(p(16));l.load(p(32));for(int x=15;x<=32;x++)l.states.put(p(x),PIPE);src=new Box(l,p(14),16);dst=new Box(l,p(33),0);sinks=cache(l,p(15));yes(!sinks.isEmpty(),"warm cross-chunk route");l.unload(p(20));
 eq(0,ItemRouting.tickExtract(l,p(15),PIPE),"unloaded middle cuts cached route");eq(16,src.stack.getCount(),"route cut keeps donor");eq(0,dst.stack.getCount(),"no teleport through unloaded middle");clean(l);
 // Single-node path still checks its last/root pipe, outside the path-pair loop.
 l=new Level();l.load(p(15));l.load(p(16));l.states.put(p(15),PIPE);dst=new Box(l,p(16),0);sinks=cache(l,p(15));yes(!sinks.isEmpty(),"one-node cached path");l.unload(p(15));
 yes(!live(l,sinks.get(0)),"single last pipe unload rejected");clean(l);
 // A queued transit node may become unavailable after its neighbour read.
 l=new Level();l.load(p(15));l.load(p(16));l.states.put(p(15),PIPE);l.states.put(p(16),PIPE);Level queued=l;l.hookAt=p(16);l.hook=()->queued.unload(p(16));cache(l,p(15));clean(l);
 // Vanilla compound-chest lookup may read the companion before returning.
 for(ChestType type:new ChestType[]{ChestType.LEFT,ChestType.RIGHT}){
  l=new Level();l.load(p(15));l.states.put(p(15),new BlockState(new ChestBlock(),type,Direction.EAST));dst=new Box(l,p(15),0);
  yes(ItemRouting.containerAt(l,p(15))==null,"unloaded chest companion blocks lookup");eq(0,l.lookups,"vanilla not entered before companion guard");clean(l);
  l.load(p(16));yes(ItemRouting.containerAt(l,p(15))==dst,"both chest chunks loaded keeps lookup");clean(l);
 }
 l=new Level();l.load(p(15));l.states.put(p(15),new BlockState(new ChestBlock(),ChestType.SINGLE,Direction.EAST));dst=new Box(l,p(15),0);yes(ItemRouting.containerAt(l,p(15))==dst,"single chest on boundary does not need other chunk");clean(l);
 // Long line + self-repeating extraction cannot extend the loaded frontier.
 l=new Level();l.load(p(15));for(int x=15;x<10000;x++)l.states.put(p(x),PIPE);src=new Box(l,p(14),16);dst=new Box(l,p(10000),0);
 for(int n=0;n<100;n++)eq(0,ItemRouting.tickExtract(l,p(15),PIPE),"long line remains inert beyond loaded edge");eq(1,l.chunks.size(),"no load cascade across 625 chunks");eq(16,src.stack.getCount(),"no long-line debit");clean(l);
 // Engine/external loading can restore a route; routing itself never performs it.
 l=new Level();l.load(p(15));l.states.put(p(15),PIPE);src=new Box(l,p(14),16);dst=new Box(l,p(16),0);eq(0,ItemRouting.tickExtract(l,p(15),PIPE),"initial unloaded destination");l.load(p(16));l.tick+=41;
 eq(1,ItemRouting.tickExtract(l,p(15),PIPE),"external loading and normal cache expiry restores transfer");eq(16,src.stack.getCount()+dst.stack.getCount(),"resume conserves items");clean(l);
 // Execute the WHOLE filter router, not a placeholder pretending to be a filter.
 for(int root:new int[]{8,15,16}){
  l=new Level();l.load(p(root));BlockState filter=new BlockState(new ItemFilterBlock());l.states.put(p(root),filter);ItemFilterBlockEntity be=new ItemFilterBlockEntity(l,p(root));src=new Box(l,p(root-1),16);l.states.put(p(root+1),PIPE);dst=new Box(l,p(root+2),0);
  ItemFilterRouting.tick(l,p(root),filter,be);int accepted=root==8?1:0;eq(accepted,dst.stack.getCount(),"filter loaded route/boundary");eq(16,src.stack.getCount()+dst.stack.getCount(),"filter conservation");clean(l);
 }
 // A stale filter tick must not scan loaded neighbours when its own chunk is gone.
 l=new Level();l.load(p(16));src=new Box(l,p(16),16);ItemFilterBlockEntity ghost=new ItemFilterBlockEntity(l,p(15));ItemFilterRouting.tick(l,p(15),new BlockState(new ItemFilterBlock()),ghost);eq(0,l.reads,"unloaded filter root inert");clean(l);
 // Reject route and powered deletion endpoint both obey the loaded boundary.
 for(boolean powered:new boolean[]{false,true})for(boolean adjacentLoaded:new boolean[]{false,true}){
  l=new Level();l.load(p(15));if(adjacentLoaded)l.load(p(16));BlockState filter=new BlockState(new ItemFilterBlock());l.states.put(p(15),filter);ItemFilterBlockEntity be=new ItemFilterBlockEntity(l,p(15));be.matched=false;src=new Box(l,p(14),16);
  ItemScavengerBlock scav=powered?new SecondItemScavengerBlock(new Block.Properties()):new ItemScavengerBlock(new Block.Properties());l.signal=powered;l.states.put(p(16),new BlockState(scav));l.states.put(p(17),PIPE);dst=new Box(l,p(18),0);ItemFilterRouting.tick(l,p(15),filter,be);
  int removed=adjacentLoaded?(powered?2:1):0;eq(16-removed,src.stack.getCount(),"reject only reaches loaded scavenger");eq(adjacentLoaded&&!powered?1:0,dst.stack.getCount(),"reject network vs intentional deletion");clean(l);
 }
 // Both filter lookups share the companion guard, not just direct pipe lookup.
 l=new Level();l.load(p(15));BlockState filter=new BlockState(new ItemFilterBlock());l.states.put(p(14),filter);ItemFilterBlockEntity be=new ItemFilterBlockEntity(l,p(14));l.states.put(p(15),new BlockState(new ChestBlock(),ChestType.LEFT,Direction.EAST));src=new Box(l,p(15),16);l.states.put(p(13),PIPE);dst=new Box(l,p(12),0);ItemFilterRouting.tick(l,p(14),filter,be);eq(16,src.stack.getCount(),"filter cannot extract half-unloaded chest");eq(0,dst.stack.getCount(),"filter no half-chest delivery");clean(l);
 // Queued filter transit and its carrier-root frontier are guarded separately.
 l=new Level();l.load(p(15));l.load(p(16));filter=new BlockState(new ItemFilterBlock());l.states.put(p(15),filter);be=new ItemFilterBlockEntity(l,p(15));src=new Box(l,p(14),16);l.states.put(p(16),PIPE);Level fq=l;l.hookAt=p(16);l.hookAfter=2;l.hook=()->fq.unload(p(16));ItemFilterRouting.tick(l,p(15),filter,be);eq(16,src.stack.getCount(),"filter queued chunk unload retains donor");clean(l);
 // Long pass line and reject line cannot bootstrap more chunks.
 l=new Level();l.load(p(14));filter=new BlockState(new ItemFilterBlock());l.states.put(p(14),filter);be=new ItemFilterBlockEntity(l,p(14));src=new Box(l,p(13),16);for(int x=15;x<10000;x++)l.states.put(p(x),PIPE);ItemFilterRouting.tick(l,p(14),filter,be);eq(1,l.chunks.size(),"filter no long line load cascade");eq(16,src.stack.getCount(),"filter frontier retains items");clean(l);
 // Explicit carrier-root entry is loaded-only even outside the tick caller.
 l=new Level();filterSinks(l,p(15));eq(0,l.reads,"unloaded filter discovery root inert");clean(l);
 // Saved filter paths also stop if unavailable before the insertion phase.
 for(int cut:new int[]{16,33}){
  l=new Level();l.load(p(14));l.load(p(16));l.load(p(32));filter=new BlockState(new ItemFilterBlock());l.states.put(p(14),filter);be=new ItemFilterBlockEntity(l,p(14));src=new Box(l,p(13),16);for(int x=15;x<=32;x++)l.states.put(p(x),PIPE);dst=new Box(l,p(33),0);Level saved=l;int boundary=cut;be.beforeEmpty=()->saved.unload(p(boundary));
  ItemFilterRouting.tick(l,p(14),filter,be);eq(16,src.stack.getCount(),"filter path/endpoint unavailable before commit");eq(0,dst.writes,"filter no setter on unavailable route");clean(l);
 }
 l=new Level();l.load(p(15));l.load(p(16));l.states.put(p(15),new BlockState(new ItemFilterBlock()));l.states.put(p(16),PIPE);dst=new Box(l,p(17),0);sinks=filterSinks(l,p(15));yes(!sinks.isEmpty(),"filter saved carrier root");l.unload(p(15));yes(!(boolean)callIn(ItemFilterRouting.class,"sinkLoaded",new Class<?>[]{Level.class,sinks.get(0).getClass()},l,sinks.get(0)),"filter root is part of availability contract");clean(l);
 // A selected powered endpoint that goes away cannot consume the source.
 l=new Level();l.load(p(15));l.load(p(16));filter=new BlockState(new ItemFilterBlock());l.states.put(p(15),filter);be=new ItemFilterBlockEntity(l,p(15));be.matched=false;src=new Box(l,p(14),16);ItemScavengerBlock powered=new SecondItemScavengerBlock(new Block.Properties());l.signal=true;l.states.put(p(16),new BlockState(powered));Level deleted=l;be.beforeEmpty=()->deleted.unload(p(16));ItemFilterRouting.tick(l,p(15),filter,be);eq(16,src.stack.getCount(),"unloaded powered endpoint cannot delete");clean(l);
 // Execute actual I and II power methods. Unknown II power excludes the WHOLE
 // endpoint (even its non-deleting pipe branch), while I has no redstone reads.
 for(boolean second:new boolean[]{false,true}){
  l=new Level();l.load(p(12));l.wireFootprint=true;filter=new BlockState(new ItemFilterBlock());l.states.put(p(12),filter);be=new ItemFilterBlockEntity(l,p(12));be.matched=false;src=new Box(l,p(11),16);
  ItemScavengerBlock edge=second?new SecondItemScavengerBlock(new Block.Properties()):new ItemScavengerBlock(new Block.Properties());l.states.put(p(13),new BlockState(edge));BlockPos outlet=p(14);l.states.put(outlet,PIPE);dst=new Box(l,p(15),0);
  ItemFilterRouting.tick(l,p(12),filter,be);eq(second?16:15,src.stack.getCount(),"unknown II power cannot fall back to reject routing");eq(second?0:1,dst.stack.getCount(),"I remains passive at border");eq(0,l.powerQueries,"do not enter indirect unloaded signal lookup");clean(l);
 }
 // Loaded II without power still routes rejects, and external loading restores
 // a previously unavailable endpoint without new pipe tickets.
 l=new Level();l.load(p(12));l.load(p(16));l.wireFootprint=true;filter=new BlockState(new ItemFilterBlock());l.states.put(p(12),filter);be=new ItemFilterBlockEntity(l,p(12));be.matched=false;src=new Box(l,p(11),16);l.states.put(p(13),new BlockState(new SecondItemScavengerBlock(new Block.Properties())));l.states.put(p(14),PIPE);dst=new Box(l,p(15),0);ItemFilterRouting.tick(l,p(12),filter,be);eq(14,src.stack.getCount(),"loaded unpowered II uses tier limit");eq(2,dst.stack.getCount(),"loaded unpowered II delivers rejects");eq(1,l.powerQueries,"loaded vanilla signal lookup preserved");clean(l);
 // The power envelope can disappear after discovery while the endpoint and
 // its receiver stay loaded: neither captured ON nor captured OFF is permission.
 for(boolean power:new boolean[]{false,true}){
  l=new Level();l.load(p(12));l.load(p(16));l.signal=power;filter=new BlockState(new ItemFilterBlock());l.states.put(p(12),filter);be=new ItemFilterBlockEntity(l,p(12));be.matched=false;src=new Box(l,p(11),16);l.states.put(p(13),new BlockState(new SecondItemScavengerBlock(new Block.Properties())));l.states.put(p(14),PIPE);dst=new Box(l,p(15),0);Level powerCut=l;be.beforeEmpty=()->powerCut.unload(p(16));ItemFilterRouting.tick(l,p(12),filter,be);eq(16,src.stack.getCount(),"captured II power cannot bypass unloaded envelope");eq(0,dst.stack.getCount(),"unavailable II cannot route on captured OFF");clean(l);
 }
 // Guard the entire horizontal read envelope, including negative coordinates
 // and Z boundaries; the envelope helper itself performs no block lookup.
 for(int x:new int[]{-17,-16,-3,-1,0,2,3,12,13,15,16,17})for(int z:new int[]{-17,-1,0,2,13,15,16}){
  l=new Level();BlockPos center=new BlockPos(x,64,z);l.load(center);boolean expected=true;for(int dx:new int[]{-3,3})for(int dz:new int[]{-3,3})expected&=l.hasChunkAt(new BlockPos(x+dx,64,z+dz));yes(LoadedItemChunks.redstoneAreaLoaded(l,center)==expected,"redstone read envelope");eq(0,l.reads,"envelope never reads blocks");
 }
 // Universal-node analog notifications have a second-neighbour vanilla read.
 // Delay publication, not signal calculation/items, then retry on ordinary load.
 ServerLevel analog=new ServerLevel();analog.load(p(14));analog.flow=1;UniversalNodeComparator.update(analog,p(14));eq(0,analog.notifications,"no unsafe comparator notification");eq(1,UniversalNodeComparator.signal(analog,p(14)),"signal calculation unaffected");analog.load(p(16));UniversalNodeComparator.update(analog,p(14));eq(1,analog.notifications,"retry delayed publication after external loading");UniversalNodeComparator.update(analog,p(14));eq(1,analog.notifications,"stable analog signal not republished");analog.flow=0;UniversalNodeComparator.update(analog,p(14));eq(2,analog.notifications,"loaded falling analog edge unchanged");clean(analog);
 System.out.println("Loaded chunks PASS: "+checks+" assertions; whole routers + scavengers + comparator + loaded guard, observed world/MC boundaries only");
}}
'''

def run(source=None,filter_source=None,chunks_source=None,comparator_source=None,extra_files=None,guard_source=None,main_class="com.gonzotech.machines.network.LoadedProbe"):
 home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else shutil.which('java');javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
 if not java or not Path(java).is_file():raise SystemExit('Java 21 required: set JAVA_HOME or PATH')
 if not os.environ.get('ECJ_JAR') and not javac:raise SystemExit('javac required (or ECJ_JAR with Java 21)')
 compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-nowarn'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
 with tempfile.TemporaryDirectory(prefix='gonzo-loaded-chunks-') as tmp:
  root=Path(tmp);files=dict(STUBS);
  for name in ('ItemScavengerBlock','SecondItemScavengerBlock','LoadedItemChunks','UniversalNodeComparator','ItemTransferGuard'):files[f'com/gonzotech/machines/network/{name}.java']=(JAVA/f'machines/network/{name}.java').read_text()
  if guard_source:files['com/gonzotech/machines/network/ItemTransferGuard.java']=guard_source
  if chunks_source:files['com/gonzotech/machines/network/LoadedItemChunks.java']=chunks_source
  if comparator_source:files['com/gonzotech/machines/network/UniversalNodeComparator.java']=comparator_source
  files['com/gonzotech/machines/network/ItemRouting.java']=source or SOURCE.read_text();files['com/gonzotech/machines/network/ItemFilterRouting.java']=filter_source or FILTER.read_text();files['com/gonzotech/machines/inventory/SlotCapacity.java']=(JAVA/'machines/inventory/SlotCapacity.java').read_text();files['com/gonzotech/machines/network/LoadedProbe.java']=PROBE;paths=[]
  for name,code in files.items():
   p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);paths.append(str(p))
  if extra_files:
   for name,code in extra_files.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);paths.append(str(p))
  built=subprocess.run(compiler+['-d',str(root/'classes')]+paths,capture_output=True,text=True)
  assert built.returncode==0,'compiler failure is not a mutation kill:\n'+built.stdout+built.stderr
  return subprocess.run([java,'-cp',str(root/'classes'),main_class],capture_output=True,text=True)

if __name__=='__main__':
 text=SOURCE.read_text();result=run();assert result.returncode==0,result.stdout+result.stderr;print(result.stdout.strip())
 fixtures=ROOT/'audit/fixtures/item_loaded_chunks'
 originals={}
 for record in json.loads((fixtures/'manifest.json').read_text()):
  data=(fixtures/record['file']).read_bytes();assert len(data)==record['bytes'] and hashlib.sha256(data).hexdigest()==record['sha256']
  assert subprocess.check_output(['git','show',f"{record['source_commit']}:{record['source']}"],cwd=ROOT)==data
  originals[Path(record['source']).name]=data.decode()
 old=run(originals['ItemRouting.java'],originals['ItemFilterRouting.java'])
 assert old.returncode!=0 and 'AssertionError' in old.stderr,'exact old implementation must fail behavior, not compilation'
 print('REJECTED: exact original 0.3.324 routers (Git/hash/bytes verified)')
 old_filter=run(filter_source=originals['ItemFilterRouting.java'])
 assert old_filter.returncode!=0 and 'AssertionError' in old_filter.stderr,'old filter must fail independently of pipe fix'
 print('REJECTED: exact old filter with fixed pipe router')
 old_comparator=run(comparator_source=originals['UniversalNodeComparator.java'])
 assert old_comparator.returncode!=0 and 'AssertionError' in old_comparator.stderr,'old comparator must fail independently'
 print('REJECTED: exact old comparator (Git/hash/bytes verified)')
 if '--mutation-check' in sys.argv:
  mutants=[
   ('root','level.isClientSide() || !level.hasChunkAt(pos)','level.isClientSide()'),
   ('captured-source','if (!sameContainerAt(level, excludeSrc, src)) {','if (false) {'),
   ('queued-node','if (!level.hasChunkAt(pipe)) continue;',''),
   ('BFS-frontier','if (!level.hasChunkAt(npos)) continue;',''),
   ('cached-path','if (!level.hasChunkAt(path.get(i)) || !level.hasChunkAt(path.get(i + 1))) return false;',''),
   ('last-pipe','if (!level.hasChunkAt(lastPipe)) return false;',''),
   ('endpoint-entry','if (!containerLookupLoaded(level, pos)) return null;',''),
   ('chest-companion','|| level.hasChunkAt(pos.relative(ChestBlock.getConnectedDirection(state)))','|| true'),
  ]
  for label,old,new in mutants:
   assert text.count(old)==1,(label,old);result=run(text.replace(old,new))
   assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived or non-assertion failure: '+label+'\n'+result.stdout+result.stderr
   print('REJECTED:',label)
  filter_text=FILTER.read_text()
  filter_mutants=[
   ('filter-root','level.isClientSide() || !level.hasChunkAt(pos)','level.isClientSide()'),
   ('filter-discovery-root','if (!level.hasChunkAt(root)) return;',''),
   ('filter-root-frontier','if (!level.hasChunkAt(npos)) continue;','',1),
   ('filter-BFS-frontier','if (!level.hasChunkAt(npos)) continue;','',2),
   ('filter-queued-node','if (!level.hasChunkAt(pipe)) continue;',''),
   ('filter-commit-path','if (!sinkLoaded(level, sink)) continue;',''),
   ('filter-saved-root','if (!level.hasChunkAt(sink.root())) return false;',''),
   ('filter-saved-segments','if (!level.hasChunkAt(pipe)) return false;',''),
   ('filter-powered-delete','if (!level.hasChunkAt(poweredScavenger) || !LoadedItemChunks.redstoneAreaLoaded(level, poweredScavenger)) break;',''),
   ('filter-powered-envelope',' || !LoadedItemChunks.redstoneAreaLoaded(level, poweredScavenger)',''),
   ('filter-reject-envelope','if (rootState.getBlock() instanceof ItemScavengerBlock scavenger\n            && !scavenger.canCheckPower(level, sink.root())) return false;',''),
   ('filter-power-availability','if (!scavenger.canCheckPower(level, npos)) continue;',''),
   ('filter-vanilla-lookup','ItemRouting.containerAt(level,','net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level,'),
  ]
  for mutation in filter_mutants:
   label,old,new,*which=mutation;assert old in filter_text,label
   if which:
    start=0
    for _ in range(which[0]+1):start=filter_text.index(old,start)+len(old)
    start-=len(old);changed=filter_text[:start]+new+filter_text[start+len(old):]
   else:changed=filter_text.replace(old,new)
   result=run(filter_source=changed)
   assert result.returncode!=0 and 'AssertionError' in result.stderr,'filter mutation survived or non-assertion failure: '+label+'\n'+result.stdout+result.stderr
   print('REJECTED:',label)
  extra=[('redstone-envelope-too-short','chunks_source',(JAVA/'machines/network/LoadedItemChunks.java').read_text().replace('int reach = 3;','int reach = 1;')),
         ('comparator-indirect-read','comparator_source',(JAVA/'machines/network/UniversalNodeComparator.java').read_text().replace('if (!level.hasChunkAt(pos) || !LoadedItemChunks.redstoneAreaLoaded(level, pos)) return;',''))]
  for label,argument,changed in extra:
   result=run(**{argument:changed});assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived: '+label+'\n'+result.stdout+result.stderr;print('REJECTED:',label)
  total=len(mutants)+len(filter_mutants)+len(extra)
  print(f'Mutation sensitivity: {total}/{total} assertion-killed')
