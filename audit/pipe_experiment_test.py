#!/usr/bin/env python3
"""Both snapshots against the same topology oracle; real service/routers, MC boundary doubles."""
from item_loaded_chunks_test import run, JAVA
from double_chest_routing_test import PROBE as CHEST
from pipe_reform_test import PROBE as REFORM
import sys

PROBE=r'''package com.gonzotech.machines.network;
import java.util.*;import net.minecraft.core.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.*;
public class ExperimentProbe {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static BlockPos p(int x,int z){return new BlockPos(x,64,z);}
 static PipeNetworkService.Plan ready(Level l,BlockPos p){for(int i=0;i<3000;i++){l.tick++;PipeNetworkService.beginTick();var a=PipeNetworkService.requestItems(l,p,false);PipeNetworkService.endTick();if(a!=null)return a;}throw new AssertionError("no progress");}
 static long metric(PipeBenchmark.Metric m){try{var f=PipeBenchmark.class.getDeclaredField("COUNTERS");f.setAccessible(true);return ((long[])f.get(null))[m.ordinal()];}catch(Exception e){throw new AssertionError(e);}}
 static void corridor(){
  PipeNetworkService.clearAll();PipeBenchmark.reset();Level l=new Level();for(int x=-1;x<=160;x++){l.load(p(x,8));if(x>=0&&x<160)l.states.put(p(x,8),LoadedProbe.PIPE);}new ChestProbe.Box(l,p(160,8),1);
  var first=ready(l,p(0,8));var path=first.pathTo(p(159,8));ok(path.size()==160,"physical path retained");
  PipeNetworkService.changed(l,p(0,8));var second=ready(l,p(0,8));ok(second.pathTo(p(159,8)).equals(path),"weld replay preserves exact path");
  ok(!PipeExperiment.ENABLED||metric(PipeBenchmark.Metric.WELD_STEPS)>0,"experimental route reuses corridors");
  ok(PipeExperiment.ENABLED||metric(PipeBenchmark.Metric.WELD_STEPS)==0,"baseline never welds");
  l.states.remove(p(100,8));ok(!second.current(),"edit inside a cached corridor revokes lease");var cut=ready(l,p(0,8));ok(cut.visited()==100&&cut.outlets().isEmpty(),"corridor cannot jump a broken segment");
  l.states.put(p(100,8),LoadedProbe.PIPE);var restored=ready(l,p(0,8));ok(restored.visited()==160,"rebuild after corridor split");l.unload(p(80,8));ok(!restored.current(),"unload revokes welded route");ok(ready(l,p(0,8)).visited()==80,"no corridor crosses unloaded chunk");ok(l.illegalReads==0,"no unloaded reads");
 }
 static void threeEntries(){
  PipeNetworkService.clearAll();Level l=new Level();l.load(p(0,0));List<PipeNetworkService.Plan> plans=new ArrayList<>();
  for(int z:new int[]{0,2,4,6}){for(int x=0;x<8;x++)l.states.put(p(x,z),LoadedProbe.PIPE);plans.add(ready(l,p(0,z)));}
  ok(!PipeExperiment.ENABLED||PipeNetworkService.cacheStats().contains("corridors=3 "),"only three cached corridors per owner cell");
  for(var a:plans)ok(a.current()&&a.visited()==8,"eviction does not limit network functionality");
 }
 static void precise(){
  PipeNetworkService.clearAll();Level l=new Level();l.load(p(1,1));var a=p(1,1);var b=p(6,6);l.states.put(a,LoadedProbe.PIPE);l.states.put(b,LoadedProbe.PIPE);
  var pa=ready(l,a);var pb=ready(l,b);PipeNetworkService.changed(l,a);ok(!pa.current(),"own observed position invalidates");ok(pb.current()==PipeExperiment.ENABLED,"B isolates disjoint observations within same 8-cube; A retains 338 behavior");
 }
 static PipeNetworkService.Plan kindReady(Level l,BlockPos root,PipeNetworkService.Kind kind){
  for(int t=0;t<3000;t++){l.tick++;PipeNetworkService.beginTick();
   var plan=PipeNetworkService.request(l,root,PipeType.ITEM,kind,p->false,p->false);
   PipeNetworkService.endTick();if(plan!=null)return plan;
  }throw new AssertionError("kind search stuck");
 }
 static void kinds(){
  for(var kind:List.of(PipeNetworkService.Kind.FILTER,PipeNetworkService.Kind.RESOURCE,PipeNetworkService.Kind.PORT)){
   PipeNetworkService.clearAll();PipeBenchmark.reset();Level l=new Level();
   for(int x=-1;x<=160;x++){l.load(p(x,8));if(x>=0&&x<160)l.states.put(p(x,8),LoadedProbe.PIPE);}
   new ChestProbe.Box(l,p(160,8),1);var root=p(0,8);
   var first=kindReady(l,root,kind);var parents=new HashMap<>(first.parents());var outlets=List.copyOf(first.outlets());
   PipeNetworkService.changed(l,root);var second=kindReady(l,root,kind);
   ok(second.parents().equals(parents)&&second.outlets().equals(outlets),"cached kind preserves parents/outlets: "+kind);
   if(PipeExperiment.ENABLED&&kind!=PipeNetworkService.Kind.PORT)ok(metric(PipeBenchmark.Metric.WELD_STEPS)>0,"kind actually replays: "+kind);
   if(kind==PipeNetworkService.Kind.PORT)ok(metric(PipeBenchmark.Metric.WELD_STEPS)==0,"PORT policy never replayed");
   l.states.remove(p(70,8));ok(!second.current(),"kind cut revokes lease: "+kind);
   var cut=kindReady(l,root,kind);ok(cut.visited()==70&&cut.outlets().isEmpty(),"kind cut cannot jump gap: "+kind);
  }
 }
 static class Clip extends Block implements PipeCarrier {
  public boolean carries(BlockState s,PipeType t){return true;}
  public boolean opensToward(BlockState s,PipeType t,Direction d){return s.type==ChestType.SINGLE||d!=s.partner;}
  public PipeMode modeFor(BlockState s,PipeType t){return PipeMode.AUTO;}
 }
 static void unusedEdge(){
  PipeNetworkService.clearAll();Level l=new Level();l.load(p(8,8));Clip block=new Clip();
  for(int x=8;x<=9;x++)for(int z=8;z<=9;z++)l.states.put(p(x,z),new BlockState(block));
  var root=p(8,8);var plan=ready(l,root);BlockPos at=null;Direction side=null;
  outer:for(var pos:List.of(p(8,8),p(8,9),p(9,8),p(9,9)))for(Direction d:Direction.values()){
   var n=pos.relative(d);if(plan.parents().containsKey(n.asLong())&&!n.equals(plan.parents().get(pos.asLong()))&&!pos.equals(plan.parents().get(n.asLong()))){at=pos;side=d;break outer;}}
  ok(at!=null,"cycle has non-tree edge");var parents=new HashMap<>(plan.parents());l.states.put(at,new BlockState(block,ChestType.LEFT,side));
  ok(plan.current()==PipeExperiment.ENABLED,"B keeps routes on unused edge closure");var fresh=ready(l,root);ok(fresh.parents().equals(parents),"chosen shortest paths unchanged after unused edge deletion");
  l.states.put(at,new BlockState(block));ok(!fresh.current(),"opening a possible shortcut requires revalidation");
 }
 static Map<Long,BlockPos> oracle(Level l,BlockPos root){
  Map<Long,BlockPos> parents=new HashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();parents.put(root.asLong(),null);queue.add(root);
  while(!queue.isEmpty()){var pos=queue.removeFirst();var state=l.states.getOrDefault(pos,Level.AIR);if(!(state.getBlock() instanceof PipeCarrier c))continue;
   for(Direction d:Direction.values()){var n=pos.relative(d);if(!l.hasChunkAt(n))continue;var other=l.states.getOrDefault(n,Level.AIR);
    if(other.getBlock() instanceof PipeCarrier nc&&c.opensToward(state,PipeType.ITEM,d)&&nc.opensToward(other,PipeType.ITEM,d.getOpposite())&&!parents.containsKey(n.asLong())){parents.put(n.asLong(),pos);queue.add(n);}}}return parents;
 }
 static void edits(){
  PipeNetworkService.clearAll();Level l=new Level();l.load(p(8,8));Random r=new Random(339);var root=p(8,8);var pipe=new BlockState(new ChestProbe.Pipe(PipeMode.AUTO));
  for(int x=1;x<=12;x++)for(int z=1;z<=12;z++)if(r.nextBoolean())l.states.put(p(x,z),pipe);l.states.put(root,pipe);
  for(int i=0;i<200;i++){var pos=p(1+r.nextInt(12),1+r.nextInt(12));if(!pos.equals(root)){if(r.nextBoolean())l.states.put(pos,pipe);else l.states.remove(pos);}var plan=ready(l,root);ok(plan.parents().equals(oracle(l,root)),"exact BFS parents after edit "+i);}
 }
 public static void main(String[]args){corridor();threeEntries();precise();kinds();unusedEdge();edits();System.out.println("EXPERIMENT "+PipeExperiment.VARIANT+" PASS "+checks);}
}'''

if __name__=='__main__':
 extras={'com/gonzotech/machines/network/ChestProbe.java':CHEST,'com/gonzotech/machines/network/ExperimentProbe.java':PROBE}
 for variant in ('baseline','experimental'):
  result=run(extra_files=extras,main_class='com.gonzotech.machines.network.ExperimentProbe',java_args=[f'-Dgonzotech.pipe.variant={variant}','-Dgonzotech.pipe.profile=true'])
  print(result.stdout.strip(),flush=True);assert result.returncode==0,result.stderr
 result=run(extra_files={'com/gonzotech/machines/network/ChestProbe.java':CHEST,'com/gonzotech/machines/network/ReformProbe.java':REFORM},main_class='com.gonzotech.machines.network.ReformProbe',java_args=['-Dgonzotech.pipe.variant=experimental'])
 print(result.stdout.strip(),flush=True);assert result.returncode==0,result.stderr
 if '--mutation-check' in sys.argv:
  source=(JAVA/'machines/network/PipeNetworkService.java').read_text()
  mutations=[('weld-stale',source.replace('invalidateCorridors(world, pos, old, actual);','')),
             ('exact-observation',source.replace('boolean result = bits != null && bits.get(cellIndex(pos));','boolean result = true;')),
             ('fourth-cache-slot',source.replace('if (cache.size() == 3)','if (cache.size() == 4)')),
             ('accept-shortcut',source.replace('if (after) return false;','if (after) return true;'))]
  for name,changed in mutations:
   assert changed!=source
   result=run(service_source=changed,extra_files=extras,main_class='com.gonzotech.machines.network.ExperimentProbe',java_args=['-Dgonzotech.pipe.variant=experimental','-Dgonzotech.pipe.profile=true'])
   assert result.returncode!=0 and 'AssertionError' in result.stderr,(name,result.stdout,result.stderr)
   print('REJECTED:',name,flush=True)
