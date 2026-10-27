#!/usr/bin/env python3
"""317: actual insideDose/owner collector bodies + actual contour/cluster core.
MC/world/entity APIs are boundary doubles, NOT Minecraft lifecycle/codec evidence.
No production ticking, persistent cache or GPU is simulated. --mutation-check
requires assertion kills, not compiler failures. Temporary classes only.
"""
from pathlib import Path
import os,shutil,subprocess,tempfile,sys
import radiation_engine_test as engine
from _radiation_contracts import method,read

# Both dose delivery and diagnostic use the same observer-excluding path.
for caller in ['radiation/RadiationSystem.java','radiation/RadiationDebugCommand.java']:
    assert 'BlockPos.containing(player.getEyePosition()), player)' in read(caller),caller
ROOT=Path(__file__).resolve().parents[1]
for lang in ['ru_ru','en_us']:
    import json
    label=json.loads((ROOT/f'src/main/resources/assets/gonzotech/lang/{lang}.json').read_text())['command.gonzotech.radiation.inside_dose']
    assert 'от блоков' not in label and 'block dose' not in label

EXTRA={
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core;
public record BlockPos(int x,int y,int z){
 public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}
 public static BlockPos containing(net.minecraft.world.phys.Vec3 v){return new BlockPos((int)Math.floor(v.x()),(int)Math.floor(v.y()),(int)Math.floor(v.z()));}
}''',
'net/minecraft/world/phys/Vec3.java':'''package net.minecraft.world.phys;public record Vec3(double x,double y,double z){}''',
'net/minecraft/world/phys/AABB.java':'''package net.minecraft.world.phys;public record AABB(double x,double y,double z,double maxX,double maxY,double maxZ){}''',
'net/minecraft/world/Container.java':'''package net.minecraft.world;public interface Container{int getContainerSize();net.minecraft.world.item.ItemStack getItem(int i);}''',
'net/minecraft/world/level/block/state/BlockState.java':'''package net.minecraft.world.level.block.state;public class BlockState{public Object getBlock(){return this;}}''',
'net/minecraft/world/level/block/entity/BlockEntity.java':'''package net.minecraft.world.level.block.entity;
public class BlockEntity{private final net.minecraft.core.BlockPos pos;public BlockEntity(net.minecraft.core.BlockPos p){pos=p;}public net.minecraft.core.BlockPos getBlockPos(){return pos;}}''',
'net/minecraft/world/level/chunk/LevelChunk.java':'''package net.minecraft.world.level.chunk;
public class LevelChunk{public final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity> owners=new java.util.HashMap<>();public java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity> getBlockEntities(){return owners;}}''',
'net/minecraft/server/level/ServerChunkCache.java':'''package net.minecraft.server.level;
public class ServerChunkCache{public final java.util.Map<String,net.minecraft.world.level.chunk.LevelChunk> chunks=new java.util.HashMap<>();public int reads;public net.minecraft.world.level.chunk.LevelChunk getChunkNow(int x,int z){reads++;return chunks.get(x+","+z);}}''',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level;
import java.util.*;import net.minecraft.world.entity.*;import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.state.BlockState;
public class ServerLevel{
 public final ServerChunkCache chunks=new ServerChunkCache();public final List<Entity> entities=new ArrayList<>();
 public com.gonzotech.radiation.RadiationContour.Result room;public boolean unloaded;
 public boolean isOutsideBuildHeight(BlockPos p){return false;}public boolean hasChunkAt(BlockPos p){return !unloaded;}
 public ServerChunkCache getChunkSource(){return chunks;}
 public BlockState getBlockState(BlockPos p){return new BlockState();}
 public List<Entity> getEntities(Entity ignored,net.minecraft.world.phys.AABB box,java.util.function.Predicate<? super Entity> filter){return entities.stream().filter(filter).toList();}
}''',
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity;
import net.minecraft.core.BlockPos;import net.minecraft.world.phys.Vec3;
public class Entity{public BlockPos pos=new BlockPos(0,0,0);public boolean alive=true;public boolean isAlive(){return alive;}public BlockPos blockPosition(){return pos;}public Vec3 getEyePosition(){return new Vec3(pos.x(),pos.y(),pos.z());}}''',
'net/minecraft/world/entity/EquipmentSlot.java':'''package net.minecraft.world.entity;public enum EquipmentSlot{MAINHAND,OFFHAND,FEET,LEGS,CHEST,HEAD,BODY}''',
'net/minecraft/world/entity/LivingEntity.java':'''package net.minecraft.world.entity;
public class LivingEntity extends Entity{public final java.util.Map<EquipmentSlot,net.minecraft.world.item.ItemStack> gear=new java.util.EnumMap<>(EquipmentSlot.class);public net.minecraft.world.item.ItemStack getItemBySlot(EquipmentSlot s){return gear.getOrDefault(s,net.minecraft.world.item.ItemStack.EMPTY);}}''',
'net/minecraft/world/entity/player/Inventory.java':'''package net.minecraft.world.entity.player;
public class Inventory implements net.minecraft.world.Container{public final java.util.List<net.minecraft.world.item.ItemStack> items=new java.util.ArrayList<>();public int getContainerSize(){return items.size();}public net.minecraft.world.item.ItemStack getItem(int i){return items.get(i);}}''',
'net/minecraft/world/entity/player/Player.java':'''package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.LivingEntity{public final Inventory inventory=new Inventory(),ender=new Inventory();public Inventory getInventory(){return inventory;}public Inventory getEnderChestInventory(){return ender;}}''',
'net/minecraft/world/entity/item/ItemEntity.java':'''package net.minecraft.world.entity.item;
public class ItemEntity extends net.minecraft.world.entity.Entity{public net.minecraft.world.item.ItemStack stack;public ItemEntity(net.minecraft.world.item.ItemStack s){stack=s;}public net.minecraft.world.item.ItemStack getItem(){return stack;}}''',
'net/minecraft/world/entity/decoration/ItemFrame.java':'''package net.minecraft.world.entity.decoration;
public class ItemFrame extends net.minecraft.world.entity.Entity{public net.minecraft.world.item.ItemStack stack;public ItemFrame(net.minecraft.world.item.ItemStack s){stack=s;}public net.minecraft.world.item.ItemStack getItem(){return stack;}}''',
'com/gonzotech/swag/PetBowlBlockEntity.java':'''package com.gonzotech.swag;
public class PetBowlBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity{public net.minecraft.world.item.ItemStack food=net.minecraft.world.item.ItemStack.EMPTY;public PetBowlBlockEntity(net.minecraft.core.BlockPos p){super(p);}public net.minecraft.world.item.ItemStack peekFood(){return food;}}''',
'com/gonzotech/radiation/RadiationTrace.java':'''package com.gonzotech.radiation;
public class RadiationTrace{public static boolean isActive(net.minecraft.server.level.ServerLevel l,net.minecraft.core.BlockPos p){return false;}public static String rate(double n){return "";}public static void log(net.minecraft.server.level.ServerLevel l,net.minecraft.core.BlockPos p,String title,String text){}}''',
}
TEST='''import java.util.*;
import net.minecraft.core.BlockPos;import net.minecraft.server.level.*;import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;import net.minecraft.world.entity.player.*;import net.minecraft.world.entity.item.*;import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.level.block.entity.*;import net.minecraft.world.level.chunk.*;
import com.gonzotech.radiation.*;import com.gonzotech.radiation.engine.*;
public class InsideExposureSelfTest {
 static int checks;static void eq(double want,double got,String label){checks++;if(Math.abs(want-got)>Math.max(1e-8,Math.abs(want)*1e-10))throw new AssertionError(label+": "+got+" expected "+want);}
 static ItemStack item(double preset,int count){return new ItemStack(new Item("hot",preset,1),count);}
 static class Chest extends BlockEntity implements net.minecraft.world.Container{
  final List<ItemStack> items=new ArrayList<>();Chest(int x,int y,int z,ItemStack... stacks){super(new BlockPos(x,y,z));items.addAll(List.of(stacks));}
  public int getContainerSize(){return items.size();}public ItemStack getItem(int i){return items.get(i);}
 }
 static class Cart extends LivingEntity implements net.minecraft.world.Container{
  final ItemStack stack=item(200,1);Cart(){gear.put(EquipmentSlot.MAINHAND,stack);}
  public int getContainerSize(){return 1;}public ItemStack getItem(int i){return stack;}
 }
 static RadiationContour.Result room(double factor,double emission){
  var points=new HashSet<RadiationContour.Pos>();
  for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)points.add(new RadiationContour.Pos(x,y,z));
  return new RadiationContour.Result(true,factor,emission,Set.copyOf(points));
 }
 static void add(ServerLevel l,BlockEntity be){BlockPos p=be.getBlockPos();l.chunks.chunks.computeIfAbsent((p.x()>>4)+","+(p.z()>>4),k->new LevelChunk()).owners.put(p,be);}
 static double dose(ServerLevel l,Player p){return InsideDoseProbe.insideDose(l,new BlockPos(0,0,0),p);}
 public static void main(String[] args){
  var l=new ServerLevel();var observer=new Player();l.entities.add(observer);observer.inventory.items.add(item(1e10,64));
  l.room=room(0,675000000);eq(33750000,dose(l,observer),"block at author 5%, zero leakage");
  var c=new Chest(1,0,0,item(1000,64));add(l,c);eq(33753200,dose(l,observer),"closed chest count64, not strongest-item target");
  l.room=room(.8,675000000);eq(33753200,dose(l,observer),"outgoing F cannot attenuate occupant");
  l.room=room(0,0);eq(3200,dose(l,observer),"observer inventory is not counted again");
  c.items.clear();eq(0,dose(l,observer),"live contents, not TTL snapshot");
  ItemStack cargo=item(1200,3);ExternalRadiation.write(cargo,100,10);
  ItemStack bag=item(0,1);bag.set(net.minecraft.core.component.DataComponents.CONTAINER,new net.minecraft.world.item.component.ItemContainerContents(List.of(cargo)));
  c.items.add(bag);eq(195,dose(l,observer),"nested preset and full induced emission");
  for(int i=0;i<30;i++)eq(195,dose(l,observer),"read-only repeat");
  eq(100,ExternalRadiation.read(cargo).rate(),"inspection cannot grow/discharge source");eq(10,ExternalRadiation.read(cargo).tick(),"inspection cannot claim source timestamp");eq(3,cargo.getCount(),"inspection cannot change count");
  c.items.clear();ItemStack held=item(500,2);ItemEntity drop=new ItemEntity(held);l.entities.add(drop);eq(50,dose(l,observer),"floor dose5%");
  ItemFrame frame=new ItemFrame(item(700,2));l.entities.add(frame);eq(120,dose(l,observer),"frame dose5%");
  var other=new Player();other.inventory.items.add(item(1000,2));other.ender.items.add(item(1e12,64));l.entities.add(other);eq(220,dose(l,observer),"other player physical inventory, NO remote ender chest");
  var mob=new LivingEntity();mob.gear.put(EquipmentSlot.HEAD,item(300,1));l.entities.add(mob);eq(235,dose(l,observer),"mob equipment");
  var cart=new Cart();l.entities.add(cart);eq(245,dose(l,observer),"container living entity shared hand identity counted once");
  var bowl=new com.gonzotech.swag.PetBowlBlockEntity(new BlockPos(0,1,0));bowl.food=item(400,1);add(l,bowl);eq(265,dose(l,observer),"pet bowl physical source");
  add(l,new Chest(-1,0,0,item(100,1)));eq(270,dose(l,observer),"second physical chest half once");
  ItemFrame wallFrame=new ItemFrame(item(100,1));wallFrame.pos=new BlockPos(3,0,0);l.entities.add(wallFrame);eq(275,dose(l,observer),"frame on room boundary");
  add(l,new Chest(3,1,0,item(200,1)));eq(285,dose(l,observer),"solid machine/container boundary itself");
  add(l,new Chest(4,1,0,item(1e9,64)));var outside=new ItemEntity(item(1e10,64));outside.pos=new BlockPos(4,0,0);l.entities.add(outside);eq(285,dose(l,observer),"not through separating wall");
  held.shrink(1);eq(260,dose(l,observer),"source changes immediately visible");drop.alive=false;eq(235,dose(l,observer),"dead drop not source");
  other.pos=new BlockPos(9,0,0);eq(135,dose(l,observer),"different room player excluded");
  l.room=new RadiationContour.Result(false,1,0,Set.of());eq(0,dose(l,observer),"open/unknown enclosure no direct room dose");
  java.util.function.Function<RadiationContour.Pos,RadiationContour.Cell> shell=p->{int m=Math.max(Math.abs(p.x()),Math.max(Math.abs(p.y()),Math.abs(p.z())));return m==3||m==4?new RadiationContour.Cell(RadiationContour.Kind.WALL,.02,0):RadiationContour.AIR;};
  l.room=RadiationContour.probe(shell,new RadiationContour.Pos(0,0,0));eq(0,l.room.factor(),"real two-layer closed geometry, no exterior leakage");eq(135,dose(l,observer),"real geometry joined to actual owner collector");
  l.room=RadiationContour.probe(p->p.y()==0&&p.z()==0&&p.x()>=3?shell.apply(new RadiationContour.Pos(0,0,0)):shell.apply(p),new RadiationContour.Pos(0,0,0));eq(0,dose(l,observer),"real open passage cancels direct room dose");
  l.room=room(0,1e8);l.unloaded=true;eq(0,dose(l,observer),"unloaded observer cannot prove room");
  if(!l.chunks.chunks.keySet().equals(Set.of("0,0","-1,0")))throw new AssertionError("collector must not generate chunks");
  System.out.println("Inside exposure PASS: "+checks+" assertions; actual methods + contour/cluster core, world boundaries doubled");
 }
}'''

def prepare(root,mutation=None):
    engine.prepare(root)
    for path,code in EXTRA.items():
        p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code)
    owners=read('radiation/engine/RadiationOwners.java')
    (root/'com/gonzotech/radiation/engine/RadiationOwners.java').write_text('''package com.gonzotech.radiation.engine;
import java.util.*;import net.minecraft.world.item.ItemStack;import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;import net.minecraft.world.entity.player.Player;import net.minecraft.world.entity.item.ItemEntity;import net.minecraft.world.entity.decoration.ItemFrame;import net.minecraft.world.Container;
public class RadiationOwners {'''+method(owners,'public static List<ItemStack> slots(')+method(owners,'public static List<ItemStack> children(')+'}')
    contour=read('radiation/RadiationContour.java')
    source=read('radiation/Containment.java')
    if mutation:
        old,new=mutation
        target=contour if old in contour else source
        assert old in target,old
        if target==contour:contour=contour.replace(old,new,1)
        else:source=source.replace(old,new,1)
    (root/'com/gonzotech/radiation/RadiationContour.java').write_text(contour)
    probe='''import java.util.*;import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerLevel;
import com.gonzotech.radiation.*;
public class InsideDoseProbe {
 static RadiationContour.Result result(ServerLevel level,BlockPos p){return level.room;}
 static RadiationContour.Cell cell(ServerLevel level,BlockPos p){return RadiationContour.AIR;}
'''+method(source,'public static double insideDose(')+method(source,'private static double contentsEmission(')+method(source,'private static boolean containsSource(')+'}'
    (root/'InsideDoseProbe.java').write_text(probe)
    path=root/'com/gonzotech/radiation/RadSources.java';s=path.read_text().replace('public class RadSources {','public class RadSources {public static double blockEmission(net.minecraft.world.level.block.state.BlockState s){return 0;}');path.write_text(s)
    path=root/'net/minecraft/core/registries/BuiltInRegistries.java';s=path.read_text().replace('public static final BuiltInRegistries ITEM=', 'public static final BlockRegistry BLOCK=new BlockRegistry();public static class BlockRegistry{public String getKey(Object b){return "block";}}\n public static final BuiltInRegistries ITEM=');path.write_text(s)
    (root/'InsideExposureSelfTest.java').write_text(TEST)

def run(compiler,java,mutation=None):
    with tempfile.TemporaryDirectory(prefix='gonzo-inside-dose-') as temp:
        root=Path(temp);prepare(root,mutation)
        result=subprocess.run(compiler+['-proc:none','-d',str(root/'classes')]+[str(p) for p in root.rglob('*.java')],capture_output=True,text=True)
        if result.returncode:raise AssertionError('Compile failure is NOT a mutation kill:\n'+result.stdout+result.stderr)
        return subprocess.run([java,'-ea','-cp',str(root/'classes'),'InsideExposureSelfTest'],capture_output=True,text=True)

home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else shutil.which('java');javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file():raise SystemExit('Java 21 required')
compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
result=run(compiler,java)
assert result.returncode==0,result.stdout+result.stderr
print(result.stdout.strip())
if '--mutation-check' in sys.argv:
    mutations=[
      ('INSIDE_DOSE_WEIGHT = 0.05','INSIDE_DOSE_WEIGHT = 0.40'),
      ('RadiationCluster.inspect(roots).total()','RadiationCluster.inspect(roots).chunkTarget()'),
      ('entity != observer && entity.isAlive()','entity.isAlive()'),
      ('return com.gonzotech.radiation.engine.RadiationCluster.inspect(roots).total();','return room.factor() * com.gonzotech.radiation.engine.RadiationCluster.inspect(roots).total();'),
      ('if (!enclosed) return false;','if (!enclosed) return false;\n            if (true) return true;'),
      ('roots.addAll(com.gonzotech.radiation.engine.RadiationOwners.slots(owner));','roots.addAll(java.util.List.of());'),
      ('for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)\n                    roots.add(player.getInventory().getItem(slot));','roots.addAll(com.gonzotech.radiation.engine.RadiationOwners.slots(player));'),
    ]
    for mutation in mutations:
        result=run(compiler,java,mutation)
        assert result.returncode!=0 and 'AssertionError' in result.stderr,(mutation,result.stdout,result.stderr)
        print('KILLED:',mutation[0])
    print(f'Inside exposure mutation sensitivity: {len(mutations)}/{len(mutations)} rejected')
