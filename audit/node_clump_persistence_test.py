#!/usr/bin/env python3
"""Execute production clump index + SavedData against observed world/NBT doubles."""
from pathlib import Path
import os,subprocess,tempfile,sys
ROOT=Path(__file__).resolve().parents[1]
NET=ROOT/'src/main/java/com/gonzotech/machines/network'
STUBS={
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core;public record BlockPos(int x,int y,int z){public long asLong(){return ((long)(x&0xFFFFF)<<40)|((long)(y&0xFFFFF)<<20)|(z&0xFFFFF);}public static BlockPos of(long n){return new BlockPos((int)(n>>40),(int)((n>>20)&0xFFFFF),(int)(n&0xFFFFF));}public BlockPos relative(Direction d){return new BlockPos(x+d.x,y+d.y,z+d.z);}}''',
'net/minecraft/core/Direction.java':'''package net.minecraft.core;public enum Direction{DOWN(0,-1,0),UP(0,1,0),NORTH(0,0,-1),SOUTH(0,0,1),WEST(-1,0,0),EAST(1,0,0);public final int x,y,z;Direction(int x,int y,int z){this.x=x;this.y=y;this.z=z;}}''',
'net/minecraft/core/HolderLookup.java':'package net.minecraft.core;public class HolderLookup{public interface Provider{}}',
'net/minecraft/world/level/block/Block.java':'package net.minecraft.world.level.block;public class Block{}',
'net/minecraft/world/level/block/state/BlockState.java':'package net.minecraft.world.level.block.state;import net.minecraft.world.level.block.Block;public record BlockState(Block block){public Block getBlock(){return block;}}',
'net/minecraft/world/level/chunk/LevelChunk.java':'package net.minecraft.world.level.chunk;public class LevelChunk{}',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;import java.util.*;import net.minecraft.core.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;public class Level{public final Map<BlockPos,BlockState> blocks=new HashMap<>();public int reads;public boolean loaded=true;public BlockState getBlockState(BlockPos p){reads++;if(!loaded)throw new AssertionError("unloaded world read");return blocks.getOrDefault(p,new BlockState(new Block()));}public boolean hasChunkAt(BlockPos p){return loaded;}public boolean isLoaded(BlockPos p){return loaded;}}''',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level;import net.minecraft.core.*;import net.minecraft.world.level.*;import net.minecraft.world.level.chunk.*;import net.minecraft.world.level.saveddata.*;public class ServerLevel extends Level{public LevelChunk chunk=new LevelChunk();public DimensionDataStorage storage=new DimensionDataStorage();public LevelChunk getChunkAt(BlockPos p){if(!loaded)throw new AssertionError("force load");return chunk;}public DimensionDataStorage getDataStorage(){return storage;}}''',
'net/minecraft/nbt/Tag.java':'package net.minecraft.nbt;public interface Tag{int TAG_COMPOUND=10;}',
'net/minecraft/nbt/LongArrayTag.java':'package net.minecraft.nbt;public record LongArrayTag(long[] values) implements Tag{}',
'net/minecraft/nbt/ListTag.java':'package net.minecraft.nbt;public class ListTag extends java.util.ArrayList<Tag> implements Tag{public CompoundTag getCompound(int i){return (CompoundTag)get(i);}}',
'net/minecraft/nbt/CompoundTag.java':'''package net.minecraft.nbt;import java.util.*;public class CompoundTag implements Tag{final Map<String,Object> values=new HashMap<>();public void putLong(String k,long v){values.put(k,v);}public void putString(String k,String v){values.put(k,v);}public void put(String k,Tag v){values.put(k,v);}public long getLong(String k){return (Long)values.get(k);}public String getString(String k){return (String)values.get(k);}public long[] getLongArray(String k){return ((LongArrayTag)values.get(k)).values().clone();}public ListTag getList(String k,int type){return (ListTag)values.getOrDefault(k,new ListTag());}}''',
'net/minecraft/world/level/saveddata/SavedData.java':'''package net.minecraft.world.level.saveddata;import net.minecraft.nbt.*;import net.minecraft.core.*;public abstract class SavedData{public boolean dirty;public void setDirty(){dirty=true;}public abstract CompoundTag save(CompoundTag t,HolderLookup.Provider p);public record Factory<T extends SavedData>(java.util.function.Supplier<T> constructor,java.util.function.BiFunction<CompoundTag,HolderLookup.Provider,T> deserializer,Object fix){}}''',
'net/minecraft/world/level/saveddata/DimensionDataStorage.java':'''package net.minecraft.world.level.saveddata;import net.minecraft.nbt.*;public class DimensionDataStorage{public CompoundTag disk;private SavedData live;@SuppressWarnings("unchecked")public <T extends SavedData>T computeIfAbsent(SavedData.Factory<T> f,String id){if(live==null)live=disk==null?f.constructor().get():f.deserializer().apply(disk,null);return (T)live;}}''',
'com/gonzotech/machines/network/PipeType.java':'package com.gonzotech.machines.network;public enum PipeType{HEAT,WIRE,ITEM}',
'com/gonzotech/machines/network/NodeBlock.java':'package com.gonzotech.machines.network;public class NodeBlock extends net.minecraft.world.level.block.Block{public PipeType pipeType(){return PipeType.HEAT;}}',
'com/gonzotech/machines/network/UniversalNodeBlock.java':'package com.gonzotech.machines.network;public class UniversalNodeBlock extends net.minecraft.world.level.block.Block{}',
'com/gonzotech/machines/network/SecondTierPipe.java':'package com.gonzotech.machines.network;public interface SecondTierPipe{}',
'com/gonzotech/machines/network/ThirdTierPipe.java':'package com.gonzotech.machines.network;public interface ThirdTierPipe{double STAT_FACTOR=.88;}',
'com/gonzotech/machines/network/PipeLoss.java':'package com.gonzotech.machines.network;public class PipeLoss{public static long perCell(boolean tier,boolean heat){return tier?220:100;}}',
'com/gonzotech/machines/network/PipeRoutingDiagnostics.java':'package com.gonzotech.machines.network;public class PipeRoutingDiagnostics{public static boolean isEnabled(){return false;}public static void clumpRebuilt(Object...args){}}',
}
for package,name in [('reactor','ReactorStructure'),('steamgen','SteamGenStructure'),('turbine','TurbineStructure')]:
 STUBS[f'com/gonzotech/machines/{package}/{name}.java']=f'''package com.gonzotech.machines.{package};import java.util.*;import net.minecraft.core.*;import net.minecraft.world.level.*;public class {name}{{public static final Set<BlockPos> PORTS=new HashSet<>();public static boolean isMember(Level l,BlockPos p){{return PORTS.contains(p);}}}}'''
PROBE='''package com.gonzotech.machines.network;
import net.minecraft.core.*;import net.minecraft.server.level.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.chunk.*;import net.minecraft.nbt.*;import com.gonzotech.machines.reactor.ReactorStructure;
public class ClumpProbe{
 static int checks;static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static class U2 extends UniversalNodeBlock implements SecondTierPipe{}static class U3 extends UniversalNodeBlock implements ThirdTierPipe{}
 static BlockPos p(int x){return new BlockPos(x,64,1);}
 static void roundTrip(Block block,String kind){
  NodeClumpIndex.clearAll();ServerLevel world=new ServerLevel();NodeClumpSavedData.restore(world);
  for(int i=1;i<=30;i++)world.blocks.put(p(i),new BlockState(block));NodeClumpIndex.onNodeChanged(world,p(1));
  ok(NodeClumpIndex.sizeAt(world,p(30))==30,"built30 "+kind);ok(NodeClumpSavedData.get(world).dirty,"write-through dirty");
  CompoundTag tag=NodeClumpSavedData.get(world).save(new CompoundTag(),null);
  ListTag list=tag.getList("Clumps",10);ok(list.size()==1,"saved group must not silently omit "+kind);
  ok(list.getCompound(0).getString("Kind").equals(kind),"correct family/tier");
  NodeClumpIndex.clearAll();ServerLevel reopened=new ServerLevel();reopened.blocks.putAll(world.blocks);reopened.storage.disk=tag;NodeClumpSavedData.restore(reopened);
  for(int i=1;i<=30;i++)ok(NodeClumpIndex.sizeAt(reopened,p(i))==30,"restored HUD/server membership "+kind);
  ok(NodeClumpIndex.membersOf(reopened,p(3)).size()==30,"members recovered");
  ok(NodeClumpIndex.lossMilliOfRoot(reopened,NodeClumpIndex.rootOf(reopened,p(3)))==(kind.startsWith("U")?0:3000),"loss preserved");
 }
 static void recovery(){
  NodeClumpIndex.clearAll();ServerLevel w=new ServerLevel();NodeClumpSavedData.restore(w);var state=new BlockState(new U2());
  for(int i=1;i<=30;i++)w.blocks.put(p(i),state);
  NodeClumpIndex.recoverUniversal(w,p(1),state);ok(NodeClumpIndex.sizeAt(w,p(30))==30,"recover existing unsaved group");
  int reads=w.reads;for(int i=0;i<100;i++)NodeClumpIndex.recoverUniversal(w,p(15),state);ok(w.reads==reads,"warm groups never rescan");
  w.blocks.put(p(100),state);NodeClumpIndex.recoverUniversal(w,p(100),state);reads=w.reads;
  for(int i=0;i<100;i++)NodeClumpIndex.recoverUniversal(w,p(100),state);ok(w.reads==reads,"singleton negative cached");
  w.chunk=new LevelChunk();NodeClumpIndex.recoverUniversal(w,p(100),state);ok(w.reads>reads,"reload allows new negative check");
  w.loaded=false;reads=w.reads;NodeClumpIndex.recoverUniversal(w,p(200),state);ok(w.reads==reads,"no forced load");
 }
 static void port(java.util.Set<BlockPos> ports){
  NodeClumpIndex.clearAll();ServerLevel w=new ServerLevel();NodeClumpSavedData.restore(w);var state=new BlockState(new U2());
  for(int i=1;i<=31;i++)w.blocks.put(p(i),state);ports.add(p(1));
  NodeClumpIndex.recoverUniversal(w,p(1),state);ok(!NodeClumpIndex.isMember(w,p(1)),"formed port is not healed into group");
  NodeClumpIndex.recoverUniversal(w,p(2),state);ok(NodeClumpIndex.sizeAt(w,p(2))==30&&!NodeClumpIndex.isMember(w,p(1)),"adjacent port does not steal external30");
  CompoundTag tag=NodeClumpSavedData.get(w).save(new CompoundTag(),null);NodeClumpIndex.clearAll();w.storage=new net.minecraft.world.level.saveddata.DimensionDataStorage();w.storage.disk=tag;NodeClumpSavedData.restore(w);
  ok(NodeClumpIndex.sizeAt(w,p(31))==30&&!NodeClumpIndex.isMember(w,p(1)),"external30 restored separately from port");ports.clear();
 }
 public static void main(String[]a){roundTrip(new UniversalNodeBlock(),"U1");roundTrip(new U2(),"U2");roundTrip(new U3(),"U3");roundTrip(new NodeBlock(),"N1:HEAT");recovery();port(ReactorStructure.PORTS);port(com.gonzotech.machines.steamgen.SteamGenStructure.PORTS);port(com.gonzotech.machines.turbine.TurbineStructure.PORTS);System.out.println("Clump persistence/recovery PASS "+checks);}
}'''
def run(index):
 with tempfile.TemporaryDirectory() as temp:
  d=Path(temp);files=dict(STUBS);files['com/gonzotech/machines/network/NodeClumpIndex.java']=index
  files['com/gonzotech/machines/network/NodeClumpSavedData.java']=(NET/'NodeClumpSavedData.java').read_text()
  files['com/gonzotech/machines/network/ClumpProbe.java']=PROBE
  for name,code in files.items():p=d/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf8')
  java=Path(os.environ['JAVA_HOME'])/'bin'
  c=subprocess.run([str(java/'javac'),'--release','21','-encoding','UTF-8','-d',str(d/'classes')]+[str(d/n) for n in files],capture_output=True,text=True)
  assert c.returncode==0,c.stderr
  return subprocess.run([str(java/'java'),'-cp',str(d/'classes'),'com.gonzotech.machines.network.ClumpProbe'],capture_output=True,text=True)
if __name__=='__main__':
 source=(NET/'NodeClumpIndex.java').read_text();result=run(source);print(result.stdout);assert result.returncode==0,result.stderr
 if '--mutation-check' in sys.argv:
  for name,changed in [('old-universal-omission',source.replace('String kind = kindOfBlock(state);','String kind = state.getBlock() instanceof NodeBlock ? kindOfBlock(state) : null;',1)),('no-recovery',source.replace('onNodeChanged(level, pos); // same-kind flood','return; // same-kind flood'))]:
   assert source!=changed;bad=run(changed);assert bad.returncode!=0 and 'AssertionError' in bad.stderr,(name,bad.stdout,bad.stderr);print('REJECTED',name)
