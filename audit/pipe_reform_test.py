#!/usr/bin/env python3
"""Pipe II production search/routers at observed world boundaries; not native TPS.
Includes >2048 progress, 73728 pipes, same-node cap, generation cancellation,
negative-cache chunk load, unrelated-cell isolation and shutdown cleanup.
"""
from item_loaded_chunks_test import run, JAVA, SOURCE
import sys
from double_chest_routing_test import PROBE as CHEST
PROBE=r'''package com.gonzotech.machines.network;
import java.util.*;import net.minecraft.core.*;import net.minecraft.world.item.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.state.*;
public class ReformProbe {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static BlockPos p(int x,int y,int z){return new BlockPos(x,y,z);}
 static BlockState pipe(){return new BlockState(new ChestProbe.Pipe(PipeMode.AUTO));}
 static PipeNetworkService.Plan request(Level l,BlockPos p){return PipeNetworkService.requestItems(l,p,false);}
 static PipeNetworkService.Plan await(Level l,BlockPos p){for(int i=0;i<3000;i++){l.tick++;PipeNetworkService.beginTick();var plan=request(l,p);PipeNetworkService.endTick();if(plan!=null)return plan;}throw new AssertionError("no eventual progress");}
 static void line(){PipeNetworkService.clearAll();Level l=new Level();for(int x=0;x<=4097;x++){l.load(p(x,64,0));if(x<4096)l.states.put(p(x,64,0),pipe());}var root=p(0,64,0);new ChestProbe.Box(l,p(4096,64,0),1);var first=request(l,root);ok(first==null,"no truncated 4096-node plan");var plan=await(l,root);ok(plan.visited()==4096,"resumable search reaches beyond old 2048 ceiling");ok(plan.pathTo(p(4095,64,0)).size()==4096,"physical path preserved");ok(plan.outlets().stream().anyMatch(o->o.target().equals(p(4096,64,0))),"far outlet discovered");
 BlockPos leaf=p(10,64,1);BlockState empty=Level.AIR;BlockState added=pipe();int before=l.reads;l.states.put(leaf,added);ok(plan.current(),"empty dead-end extension preserves complete plan");ok(plan.visited()==4097,"leaf appended locally");ok(l.reads-before<40,"leaf edit performs only local world reads");l.states.remove(leaf);ok(plan.current()&&plan.visited()==4096,"empty leaf removed without component BFS");
 Level other=new Level();other.load(p(0,64,0));other.states.put(root,pipe());var independent=await(other,root);
 for(int i=0;i<10000;i++)PipeNetworkService.changed(l,p(2048,64,0));ok(!plan.current(),"immediate lease revocation");ok(PipeNetworkService.pendingJobs()==1,"10000 changes coalesce into ONE pending job");ok(independent.current(),"other world unaffected");
 l.states.remove(p(2048,64,0));var split=await(l,root);ok(split.visited()==2048,"split cannot use old frontier");ok(split.outlets().stream().noneMatch(o->o.target().equals(p(4096,64,0))),"no transfer across severed bridge");
 PipeNetworkService.changed(l,p(16000,64,0));ok(split.current(),"unobserved cell does not invalidate plan");
 l.unload(p(16,64,0));ok(!split.current(),"chunk unload revokes immediately");var unloaded=await(l,root);ok(unloaded.visited()<=17,"unknown chunk not traversed");l.load(p(16,64,0));ok(!unloaded.current(),"loading boundary invalidates negative plan");ok(await(l,root).visited()==2048,"external loading resumes full loaded area");ok(l.illegalReads==0,"zero unloaded world reads");PipeNetworkService.closeLevel(l);ok(!split.current(),"closed world cannot retain valid leases");PipeNetworkService.clearAll();ok(PipeNetworkService.pendingJobs()==0,"shutdown clears jobs"); }
 static void cap(){PipeNetworkService.clearAll();ItemRouting.clearAll();Level l=new Level();BlockPos root=p(8,64,8);l.load(root);l.states.put(root,pipe());var a=new ChestProbe.Box(l,root.relative(Direction.WEST),1);var b=new ChestProbe.Box(l,root.relative(Direction.NORTH),1);var out=new ChestProbe.Box(l,root.relative(Direction.EAST),1);a.slots[0]=new ItemStack(ChestProbe.GOODS,64);b.slots[0]=new ItemStack(ChestProbe.GOODS,64);await(l,root);int moved=ItemRouting.tickExtract(l,root,l.states.get(root));ok(moved==1,"one item type per WHOLE node, not per face");ok(ItemRouting.tickExtract(l,root,l.states.get(root))==0,"duplicate same-tick invocation cannot reset node cap");ok(a.total()+b.total()+out.total()==128,"multiface conservation");l.tick++;ok(ItemRouting.tickExtract(l,root,l.states.get(root))==1,"node allowance renews next tick");}
 static void resourcePolicyAndBudget(){PipeNetworkService.clearAll();Level l=new Level();for(int x=0;x<1300;x++){l.load(p(x,64,0));l.states.put(p(x,64,0),pipe());}BlockPos root=p(0,64,0);PipeNetworkService.beginTick();try{var f=PipeNetworkService.class.getDeclaredField("remaining");f.setAccessible(true);f.setInt(null,2);}catch(Exception e){throw new AssertionError(e);}var partial=PipeNetworkService.request(l,root,PipeType.WIRE,PipeNetworkService.Kind.RESOURCE,p->false,p->true);ok(partial==null,"quota cannot publish partial resource recipients");ok(PipeNetworkService.progress(l,root,PipeType.WIRE,PipeNetworkService.Kind.RESOURCE)==2,"exact vertex work charge");l.tick+=999;PipeNetworkService.request(l,root,PipeType.WIRE,PipeNetworkService.Kind.RESOURCE,p->false,p->true);ok(PipeNetworkService.progress(l,root,PipeType.WIRE,PipeNetworkService.Kind.RESOURCE)==2,"world clock cannot renew SERVER allowance");PipeNetworkService.Plan complete=null;for(int i=0;i<1000&&complete==null;i++){PipeNetworkService.beginTick();complete=PipeNetworkService.request(l,root,PipeType.WIRE,PipeNetworkService.Kind.RESOURCE,p->false,p->true);PipeNetworkService.endTick();}ok(complete!=null&&complete.outlets().size()==1300,"more than 1024 resource endpoints survive");PipeNetworkService.Plan port=null;for(int i=0;i<1000&&port==null;i++){PipeNetworkService.beginTick();port=PipeNetworkService.request(l,root,PipeType.WIRE,PipeNetworkService.Kind.PORT,p->p.getX()>10,p->true);PipeNetworkService.endTick();}ok(port!=null&&port.visited()==11,"source structure membership exclusion retained");ok(complete.current(),"distinct port policy does not overwrite ordinary query");}
 static void dynamicDiscovery(){PipeNetworkService.clearAll();Level l=new Level();l.load(p(8,64,8));var root=p(8,64,8);l.states.put(root,pipe());var cart=new ChestProbe.Box(l,p(13,64,13),1);l.containers.remove(p(13,64,13));var old=await(l,root);ok(old.outlets().isEmpty(),"complete negative endpoint result");var target=root.relative(Direction.EAST);l.carts.put(target,cart);ok(old.current(),"moving entity does not emit a block edit");l.tick+=40;PipeNetworkService.beginTick();ok(request(l,root)==old,"entity refresh retains previous COMPLETE snapshot");for(int i=0;i<100&&old.current();i++){PipeNetworkService.endTick();PipeNetworkService.beginTick();request(l,root);}var fresh=request(l,root);ok(!old.current()&&fresh!=null,"discovery atomically publishes replacement");ok(fresh.outlets().stream().filter(o->o.target().equals(target)).count()==1,"entity endpoint found once without geometry BFS");ok(fresh.visited()==1,"discovery did not expand pipe graph");}
 static void scale(){PipeNetworkService.clearAll();Level l=new Level();for(int x=-1;x<=192;x+=16)for(int z=-1;z<=192;z+=16)l.load(p(x,64,z));for(int x=0;x<192;x++)for(int z=0;z<192;z++)for(int y=64;y<66;y++)l.states.put(p(x,y,z),pipe());l.load(p(192,65,191));var source=new ChestProbe.Box(l,p(-1,64,0),1);source.slots[0]=new ItemStack(ChestProbe.GOODS,64);var target=new ChestProbe.Box(l,p(192,65,191),1);long begin=System.nanoTime();var plan=await(l,p(0,64,0));long cold=System.nanoTime()-begin;ok(plan.visited()==73728,"all 73728 pipes reachable, no size truncation");int reads=l.reads;begin=System.nanoTime();for(int i=0;i<10000;i++)ok(request(l,p(0,64,0))==plan,"stable cache identity");long warm=System.nanoTime()-begin;ok(l.reads==reads,"10000 warm plan requests do not read world geometry");System.out.printf(Locale.ROOT,"SCALE nodes=73728 complete_ms=%.3f warm_10000_ms=%.3f geometry_reads=%d ticks=%d%n",cold/1e6,warm/1e6,reads,l.tick);int before=l.reads;long[] transferTimes=new long[32];for(int i=0;i<32;i++){l.tick++;PipeNetworkService.beginTick();long start=System.nanoTime();ok(ItemRouting.tickExtract(l,p(0,64,0),l.states.get(p(0,64,0)))==1,"warm full-network transfer");transferTimes[i]=System.nanoTime()-start;PipeNetworkService.endTick();}Arrays.sort(transferTimes);ok(source.total()==32&&target.total()==32,"large-network item conservation");ok(l.reads-before<32*80,"warm transfers do not reread every physical route segment");System.out.printf(Locale.ROOT,"TRANSFER nodes=73728 calls=32 p50_ms=%.3f p95_ms=%.3f world_reads=%d%n",transferTimes[16]/1e6,transferTimes[30]/1e6,l.reads-before);PipeNetworkService.clearAll();}
 static void endpointLeaf(){
  PipeNetworkService.clearAll();Level l=new Level();var root=p(8,64,8);var leaf=root.relative(Direction.EAST);l.load(root);l.states.put(root,pipe());l.states.put(leaf,pipe());
  var cart=new ChestProbe.Box(l,p(13,64,13),1);l.containers.remove(p(13,64,13));l.carts.put(leaf.relative(Direction.EAST),cart);
  var plan=await(l,root);ok(plan.outlets().stream().anyMatch(o->o.pipe().equals(leaf)),"leaf has an actual entity endpoint");
  l.carts.clear();l.states.remove(leaf);ok(!plan.current(),"formerly used leaf cannot keep an old endpoint lease when its entity just moved away");
  var fresh=await(l,root);ok(fresh.visited()==1&&fresh.outlets().isEmpty(),"removed endpoint leaf is absent from replacement plan");
 }
 static void oneUnit(){try{var f=PipeNetworkService.class.getDeclaredField("remaining");f.setAccessible(true);f.setInt(null,1);}catch(Exception e){throw new AssertionError(e);}}
 static void schedulerFairness(){
  PipeNetworkService.clearAll();Level l=new Level();for(int x=0;x<512;x++){l.load(p(x,64,0));l.states.put(p(x,64,0),pipe());}
  var early=p(0,64,0);var late=p(8,64,8);l.load(late);l.states.put(late,pipe());PipeNetworkService.beginTick();oneUnit();request(l,early);request(l,late);
  PipeNetworkService.Plan ready=null;for(int tick=0;tick<4&&ready==null;tick++){PipeNetworkService.beginTick();oneUnit();request(l,early);ready=request(l,late);PipeNetworkService.endTick();}
  ok(ready!=null&&ready.visited()==1,"fixed source tick order cannot starve a later query under a one-unit server budget");
 }
 public static void main(String[]args){line();cap();resourcePolicyAndBudget();dynamicDiscovery();endpointLeaf();schedulerFairness();scale();System.out.println("Pipe reform PASS: "+checks+" assertions");}
}'''
if __name__=='__main__':
 result=run(extra_files={'com/gonzotech/machines/network/ChestProbe.java':CHEST,'com/gonzotech/machines/network/ReformProbe.java':PROBE},main_class='com.gonzotech.machines.network.ReformProbe')
 print(result.stdout.strip());assert result.returncode==0,result.stderr

 if '--mutation-check' in sys.argv:
  service=(JAVA/'machines/network/PipeNetworkService.java').read_text()
  mutants=[
   ('free-search',service.replace('remaining--;','remaining -= 0;')),
   ('partial-plan-published',service.replace('if (!q.dirty && q.frontier.isEmpty())','if (!q.dirty)')),
   ('stale-lease-usable',service.replace('if (plan != null) plan.current = false;','if (false) plan.current = false;',1)),
   ('restart-every-slice',service.replace('if (q.dirty) q.restart();','q.restart();')),
   ('fifo-event-explosion',service.replace('LinkedHashSet<Query> RUNNABLE = new LinkedHashSet<>()','List<Query> RUNNABLE = new ArrayList<>()')),
   ('read-unloaded',service.replace('if (!world.level.hasChunkAt(pos)) return null;','if (false) return null;')),
   ('source-order-starvation',service.replace('RUNNABLE.iterator().next() == q && remaining > 0', 'remaining > 0')),
   ('stale-endpoint-leaf',service.replace('if (q.endpointPipes.contains(pos)) return false;', 'if (false) return false;')),
  ]
  extras={'com/gonzotech/machines/network/ChestProbe.java':CHEST,'com/gonzotech/machines/network/ReformProbe.java':PROBE}
  for name,changed in mutants:
   assert changed!=service,name
   result=run(service_source=changed,extra_files=extras,main_class='com.gonzotech.machines.network.ReformProbe')
   assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived: '+name+'\n'+result.stdout+result.stderr
   print('REJECTED:',name,flush=True)
  original=SOURCE.read_text();changed=original.replace('NODE_BUDGETS.computeIfAbsent(level, k -> new NodeTickBudget()).at(now, pipePos)','new java.util.HashMap<>()')
  assert original!=changed
  result=run(source=changed,extra_files=extras,main_class='com.gonzotech.machines.network.ReformProbe')
  assert result.returncode!=0 and 'AssertionError' in result.stderr,'per-face allowance mutation survived'
  print(f'REJECTED: per-face allowance; {len(mutants)+1}/{len(mutants)+1} assertion-killed')
