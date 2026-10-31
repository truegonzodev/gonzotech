#!/usr/bin/env python3
"""Execute whole routing: two-node BFS, independent lanes, transit and truncation.
Observed world/container boundaries, NOT native geometry/scheduler/TPS proof.
"""
import hashlib,json,subprocess,sys
from pathlib import Path
from item_loaded_chunks_test import run,SOURCE
from double_chest_routing_test import PROBE as CHEST
ROOT=Path(__file__).resolve().parents[1]
PROBE=r'''package com.gonzotech.machines.network;
import java.util.*;import java.lang.reflect.*;import net.minecraft.core.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.state.*;
public class BudgetProbe{
static int checks;static final Item GOODS=new Item();static BlockPos p(int x,int z){return ChestProbe.p(x,z);}static void eq(int a,int b,String s){checks++;if(a!=b)throw new AssertionError(s+": expected "+a+", got "+b);}static void ok(boolean b,String s){eq(1,b?1:0,s);}
static class Pipe extends ChestProbe.Pipe{final int cap;Pipe(PipeMode m,int n){super(m);cap=n;}public boolean opensToward(BlockState s,PipeType t,Direction d){return d==Direction.EAST||d==Direction.WEST;}public int throughputLimit(BlockState s,PipeType t){return 5*cap;}public int perItemThroughputLimit(BlockState s,PipeType t){return cap;}}
static BlockState pipe(PipeMode m,int c){return new BlockState(new Pipe(m,c));}static Level world(){Level l=new Level();l.assembleChests=true;l.load(p(4,4));return l;}
static ChestProbe.Box[] box(Level l,int x,int z,boolean dbl){return dbl?ChestProbe.pair(l,p(x,z),Direction.SOUTH,false):new ChestProbe.Box[]{new ChestProbe.Box(l,p(x,z),27)};}static int total(ChestProbe.Box[] b){return ChestProbe.sum(b);}static void stock(ChestProbe.Box[] b){b[b.length-1].slots[26]=new ItemStack(GOODS,64);}static void lane(Level l,int x,int z,boolean left,int cap){l.states.put(p(x,z),pipe(left?PipeMode.PULL:PipeMode.PUSH,cap));l.states.put(p(x+1,z),pipe(left?PipeMode.PUSH:PipeMode.PULL,cap));}static int tick(Level l,int x,int z){return ItemRouting.tickExtract(l,p(x,z),l.states.get(p(x,z)));}
static Object call(String n,Class<?>[] types,Object... args){return ChestProbe.call(n,types,args);}static int budget(){try{Field f=ItemRouting.class.getDeclaredField("topoBudget");f.setAccessible(true);return f.getInt(null);}catch(Exception e){throw new AssertionError(e);}}static Object field(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
static void networks(){
for(int cap:new int[]{1,2})for(boolean dbl:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){
// Opposite independent inputs, each route exactly TWO pipes.
Level l=world();var a=box(l,4,4,dbl);var b=box(l,7,4,dbl);var c=box(l,10,4,dbl);stock(a);stock(c);lane(l,5,4,true,cap);lane(l,8,4,false,cap);
for(int t=0;t<4;t++){ItemRouting.onWorldEdit(l);eq(cap,tick(l,reverse?9:5,4),"first independent input");eq(2046,budget(),"complete two-node search charges TWO, not all budget");eq(cap,tick(l,reverse?5:9,4),"second independent input same tick");eq(2044,budget(),"second search leaves budget for other lanes");eq(2*cap*(t+1),total(b),"receiver sums independent inputs");eq(128,total(a)+total(b)+total(c),"two-input conservation");l.tick++;}eq(0,l.illegalReads,"loaded-only inputs");
// Empty B receives then forwards via the opposite half when double.
l=world();a=box(l,4,4,dbl);b=box(l,7,4,dbl);c=box(l,10,4,dbl);stock(a);lane(l,5,4,true,cap);lane(l,8,dbl?5:4,true,cap);
eq(cap,tick(l,5,4),"A -> B first short route");eq(cap,tick(l,8,dbl?5:4),"B -> C second short route same tick");eq(0,total(b),"B can forward incoming stock");eq(cap,total(c),"C receives");eq(64,total(a)+total(b)+total(c),"transit conservation");eq(0,l.illegalReads,"loaded-only transit");
}
for(int cap:new int[]{1,2})for(boolean reverse:new boolean[]{false,true}){
// Screenshot: TWO separate parallel lanes, not a split from one input pipe.
Level l=world();var a=box(l,4,4,true);var b=box(l,7,4,true);stock(a);lane(l,5,4,true,cap);lane(l,5,5,true,cap);
for(int t=0;t<4;t++){eq(cap,tick(l,5,reverse?5:4),"first of parallel lanes");eq(cap,tick(l,5,reverse?4:5),"second parallel lane has OWN allowance");eq(2*cap*(t+1),total(b),"same receiver not capped to one lane");eq(64,total(a)+total(b),"parallel lane conservation");l.tick++;}eq(0,l.illegalReads,"loaded-only parallel");
}
}
static void searchLimits(){Level l=world();var a=box(l,4,4,false);var b=box(l,7,4,false);stock(a);lane(l,5,4,true,1);
for(int limit:new int[]{0,1,2,3}){Object r=call("collectSinks",new Class<?>[]{Level.class,BlockPos.class,int.class},l,p(5,4),limit);eq(Math.min(limit,2),(int)field(r,"visited"),"actual processed count bounded");ok((boolean)field(r,"complete")== (limit>=2),"completion independent of count TWO");eq(limit>=2?1:0,((List<?>)field(r,"sinks")).size(),"no discovery past boundary");}
// Three-node frontier: processing TWO nodes is incomplete, not the old sentinel.
Level longer=world();new ChestProbe.Box(longer,p(8,4),27);longer.states.put(p(5,4),pipe(PipeMode.PULL,1));longer.states.put(p(6,4),pipe(PipeMode.AUTO,1));longer.states.put(p(7,4),pipe(PipeMode.PUSH,1));
Object partial=call("collectSinks",new Class<?>[]{Level.class,BlockPos.class,int.class},longer,p(5,4),2);eq(2,(int)field(partial,"visited"),"truncated count TWO is still an ordinary count");ok(!(boolean)field(partial,"complete"),"queued third node means incomplete");eq(0,((List<?>)field(partial,"sinks")).size(),"third node not processed beyond budget");
Object finished=call("collectSinks",new Class<?>[]{Level.class,BlockPos.class,int.class},longer,p(5,4),3);eq(3,(int)field(finished,"visited"),"full three-node count");ok((boolean)field(finished,"complete"),"exact three-node boundary completes");eq(1,((List<?>)field(finished,"sinks")).size(),"full result sees distant target");
// Incomplete discovery cannot poison cache; same start can later finish.
try{Field t=ItemRouting.class.getDeclaredField("topoBudgetTick"),bgt=ItemRouting.class.getDeclaredField("topoBudget");t.setAccessible(true);bgt.setAccessible(true);t.setLong(null,l.tick);bgt.setInt(null,1);eq(0,tick(l,5,4),"one-node budget cannot reach receiver");bgt.setInt(null,2);eq(1,tick(l,5,4),"incomplete result was NOT cached as complete");eq(0,budget(),"exact boundary consumes actual remaining nodes");
l.tick++;int before=total(b);eq(1,tick(l,5,4),"complete exact-two result can be cached");eq(2048,budget(),"cache hit spends no BFS nodes");eq(before+1,total(b),"cached short route keeps transferring");}catch(Exception e){throw new AssertionError(e);}
}
public static void main(String[]args){networks();searchLimits();System.out.println("BFS budget PASS: "+checks+" assertions; whole routers/two-node routes; MC boundaries only");}}
'''
def check(text=None):return run(source=text,extra_files={'com/gonzotech/machines/network/ChestProbe.java':CHEST,'com/gonzotech/machines/network/BudgetProbe.java':PROBE},main_class='com.gonzotech.machines.network.BudgetProbe')
if __name__=='__main__':
    text=SOURCE.read_text();r=check();assert r.returncode==0,r.stdout+r.stderr;print(r.stdout.strip())
    folder=ROOT/'audit/fixtures/item_bfs_budget';m=json.loads((folder/'manifest.json').read_text());data=(folder/m['file']).read_bytes();assert len(data)==m['bytes'] and hashlib.sha256(data).hexdigest()==m['sha256'];assert subprocess.check_output(['git','show',f"{m['source_commit']}:{m['source']}"],cwd=ROOT)==data
    r=check(data.decode());assert r.returncode!=0 and 'AssertionError' in r.stderr,'exact327 must compile then assertion-fail';print('REJECTED: exact327 Git/hash/bytes, two-node budget charge')
    if '--mutation-check' in sys.argv:
        changes=[('two-node-sentinel','topoBudget -= search.visited();','if (search.visited() == 2) topoBudget = 0; else topoBudget -= search.visited();'),('unbounded-search','processed < maxNodes','true'),('false-completion','processed, queue.isEmpty()','processed, true'),('never-cache','if (search.complete()) perLevel.put','if (false) perLevel.put'),('free-search','topoBudget -= search.visited();','topoBudget -= 0;')]
        for label,a,b in changes:
            assert a in text,label;r=check(text.replace(a,b));assert r.returncode!=0 and 'AssertionError' in r.stderr,label+' survived or did not assertion-fail\n'+r.stdout+r.stderr;print('REJECTED:',label)
        print('Mutation sensitivity: 5/5 assertion-killed')
