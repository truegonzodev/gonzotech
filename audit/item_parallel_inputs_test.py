#!/usr/bin/env python3
"""Actual production item routers/guard: independent inputs, forward transit, cycles.
No native Minecraft/geometry/scheduler proof. Includes isolated cold/warm controls,
which do NOT reproduce the author's remaining native isolated-start failure.
"""
import hashlib,json,subprocess,sys
from pathlib import Path
from item_loaded_chunks_test import run, SOURCE, JAVA
from double_chest_routing_test import PROBE as CHEST_PROBE
ROOT=Path(__file__).resolve().parents[1]
PROBE=r'''package com.gonzotech.machines.network;
import java.util.*;import net.minecraft.core.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.ChestType;
public class NetworkProbe{
static int checks;static final Item GOODS=new Item();static void eq(int a,int b,String s){checks++;if(a!=b)throw new AssertionError(s+": "+b+" != "+a);}static void ok(boolean b,String s){eq(1,b?1:0,s);}static BlockPos p(int x,int z){return ChestProbe.p(x,z);}
static class Pipe extends ChestProbe.Pipe{final int per;Pipe(PipeMode mode,int cap){super(mode);per=cap;}public int throughputLimit(BlockState s,PipeType t){return 5*per;}public int perItemThroughputLimit(BlockState s,PipeType t){return per;}}
static BlockState pipe(PipeMode mode,int per){return new BlockState(new Pipe(mode,per));}static ChestProbe.Box[] box(Level l,BlockPos pos,boolean twice,Direction side){return twice?ChestProbe.pair(l,pos,side,false):new ChestProbe.Box[]{new ChestProbe.Box(l,pos,27)};}static int sum(ChestProbe.Box[]a){return ChestProbe.sum(a);}static void stock(ChestProbe.Box[]a,int n){a[0].slots[0]=new ItemStack(GOODS,n);}static void clean(Level l){eq(0,l.illegalReads,"no unloaded read");}
static class Hub{final Level l=new Level();final ChestProbe.Box[][]inputs=new ChestProbe.Box[3][];final ChestProbe.Box[]target;final BlockPos[] roots={p(3,8),p(13,8),p(8,14)};final BlockState start;Hub(boolean fromDouble,boolean toDouble,int per){l.assembleChests=true;l.load(p(8,8));start=pipe(PipeMode.PULL,per);inputs[0]=box(l,p(2,8),fromDouble,Direction.SOUTH);inputs[1]=box(l,p(14,8),fromDouble,Direction.SOUTH);inputs[2]=box(l,p(8,15),fromDouble,Direction.EAST);target=box(l,p(8,8),toDouble,Direction.SOUTH);for(var a:inputs)stock(a,64);for(int x=3;x<=7;x++)l.states.put(p(x,8),pipe(x==3?PipeMode.PULL:x==7?PipeMode.PUSH:PipeMode.AUTO,per));for(int x=9;x<=13;x++)l.states.put(p(x,8),pipe(x==13?PipeMode.PULL:x==9?PipeMode.PUSH:PipeMode.AUTO,per));int last=toDouble?10:9;for(int z=last;z<=14;z++)l.states.put(p(8,z),pipe(z==14?PipeMode.PULL:z==last?PipeMode.PUSH:PipeMode.AUTO,per));}int tick(int i){return ItemRouting.tickExtract(l,roots[i],start);}int total(){return sum(target)+sum(inputs[0])+sum(inputs[1])+sum(inputs[2]);}}
static void hubTests(){
int[][]orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
for(boolean src:new boolean[]{false,true})for(boolean dst:new boolean[]{false,true})for(int cap:new int[]{1,2})for(int[]order:orders){Hub h=new Hub(src,dst,cap);int before=h.total();for(int tick=0;tick<5;tick++){for(int i:order)eq(cap,h.tick(i),"each independent input accepted in SAME tick");eq(3*cap*(tick+1),sum(h.target),"receiver sums all independent paths");eq(before,h.total(),"three-way conservation");h.l.tick++;}clean(h.l);}
// Multiple item TYPES on one input must retain its aggregate cap, not one item.
Hub h=new Hub(false,true,1);for(int k=0;k<5;k++)h.inputs[0][0].slots[k]=new ItemStack(new Item(),8);eq(5,h.tick(0),"one route accepts five distinct types in same tick");eq(5,sum(h.target),"no sink-mutex reduces aggregate to one");clean(h.l);
for(int[]order:orders){h=new Hub(true,true,1);for(var b:h.target)b.fill(new Item(),64);h.target[1].slots[26]=new ItemStack(GOODS,63);int before=h.total(),accepted=0;for(int i:order)accepted+=h.tick(i);eq(1,accepted,"one remaining slot accepts only one across inputs");eq(before,h.total(),"full receiver cannot dupe/debit failed inserts");clean(h.l);}
}
static void forwardTests(){
// A -> B(near/far) -> C in one tick, with and without old B stock.
for(int stock:new int[]{0,16}){Hub h=new Hub(false,true,1);for(int x=9;x<=13;x++)h.l.states.remove(p(x,8));for(int z=10;z<=14;z++)h.l.states.remove(p(8,z));stock(h.target,stock);ChestProbe.Box out=new ChestProbe.Box(h.l,p(14,9),27);for(int x=9;x<=13;x++)h.l.states.put(p(x,9),pipe(x==9?PipeMode.PULL:x==13?PipeMode.PUSH:PipeMode.AUTO,1));int before=sum(h.inputs[0])+sum(h.target)+out.total();eq(1,h.tick(0),"receive into B");eq(1,ItemRouting.tickExtract(h.l,p(9,9),pipe(PipeMode.PULL,1)),"received B forwards in same tick through OTHER half");eq(stock,sum(h.target),"B is not frozen by incoming route");eq(1,out.total(),"C receives forward transit");eq(before,sum(h.inputs[0])+sum(h.target)+out.total(),"transit conservation");var back=ItemRouting.transferAttempt(h.l,p(14,9),out,p(2,8),h.inputs[0][0]);ok(!back.allowed(),"C -> A closes three-inventory cycle, denied");var repeated=ItemRouting.transferAttempt(h.l,p(2,8),h.inputs[0][0],p(8,8),ItemRouting.containerAt(h.l,p(8,8)));ok(repeated.allowed(),"repeated forward edge is not a lock");clean(h.l);}
// Exercise the public router on an actual same-tick reverse route, not just
// the guard API: flip endpoints after a successful forward transfer.
ChestProbe.Rig reversed=new ChestProbe.Rig(Direction.SOUTH,false,false,true);reversed.setSource(0,8);eq(1,reversed.tick(),"seed actual forward route");BlockPos last=p(8,6);reversed.l.states.put(reversed.root,pipe(PipeMode.PUSH,1));reversed.l.states.put(last,pipe(PipeMode.PULL,1));eq(0,ItemRouting.tickExtract(reversed.l,last,pipe(PipeMode.PULL,1)),"actual router denies reverse same-tick transfer");eq(7,reversed.from[0].total(),"reverse attempt cannot refund source");eq(1,sum(reversed.to),"accepted target retains item");clean(reversed.l);
// Worlds share coordinates and tick number, never history.
Hub a=new Hub(false,true,1),b=new Hub(false,true,1);b.l.tick=a.l.tick;eq(1,a.tick(0),"world A edge");var reverse=ItemRouting.transferAttempt(b.l,p(8,8),ItemRouting.containerAt(b.l,p(8,8)),p(2,8),b.inputs[0][0]);ok(reverse.allowed(),"world B must not inherit world A history");a.l.tick++;ok(ItemRouting.transferAttempt(a.l,p(8,8),ItemRouting.containerAt(a.l,p(8,8)),p(2,8),a.inputs[0][0]).allowed(),"next tick is not permanently locked");
// Filter participates in the SAME history as ordinary item pipes.
Level l=new Level();l.assembleChests=true;l.load(p(8,8));BlockPos f=p(8,8),src=p(7,8),dst=p(11,8);BlockState fs=new BlockState(new ItemFilterBlock());l.states.put(f,fs);var be=new com.gonzotech.machines.block.entity.ItemFilterBlockEntity(l,f);ChestProbe.Box donor=new ChestProbe.Box(l,src,27),receiver=new ChestProbe.Box(l,dst,27);donor.slots[0]=new ItemStack(GOODS,3);receiver.slots[0]=new ItemStack(GOODS,1);l.states.put(p(9,8),pipe(PipeMode.AUTO,1));l.states.put(p(10,8),pipe(PipeMode.PUSH,1));var inverse=ItemRouting.transferAttempt(l,dst,receiver,src,donor);ok(inverse.allowed(),"seed reverse actual transfer history");receiver.removeItem(0,1);donor.slots[0].grow(1);inverse.record();ItemFilterRouting.tick(l,f,fs,be);eq(4,donor.total(),"filter cannot bypass recorded return edge");eq(0,receiver.total(),"filter reverse target not written");clean(l);
}
static void coldAndDiagnostics(){
// Controls only: model does not reproduce native isolated double cold failure.
for(boolean target:new boolean[]{false,true}){ChestProbe.Rig cold=new ChestProbe.Rig(Direction.SOUTH,false,true,target);cold.setSource(53,8);eq(1,cold.tick(),"isolated prebuilt double cold control");ChestProbe.Rig warm=new ChestProbe.Rig(Direction.SOUTH,false,false,target);warm.from[0]=new ChestProbe.Box(warm.l,warm.source,27);warm.l.states.put(warm.source,new BlockState(new ChestBlock()));warm.setSource(0,8);eq(1,warm.tick(),"single starts before conversion");BlockPos other=warm.source.relative(Direction.SOUTH);warm.l.states.put(warm.source,new BlockState(new ChestBlock(),ChestType.LEFT,Direction.SOUTH));warm.l.states.put(other,new BlockState(new ChestBlock(),ChestType.RIGHT,Direction.NORTH));new ChestProbe.Box(warm.l,other,27);ItemRouting.onWorldEdit(warm.l);warm.l.tick++;eq(1,warm.tick(),"warm conversion preserves native physical near owner");clean(cold.l);clean(warm.l);}
ChestProbe.Rig r=new ChestProbe.Rig(Direction.SOUTH,false,true,false);r.setSource(53,8);r.l.tick=200;r.l.diagnostics=true;eq(1,r.tick(),"diagnostics does not change result");ok(r.l.debug.stream().anyMatch(s->s.startsWith("source-ready")),"source classification observable");ok(r.l.debug.stream().anyMatch(s->s.startsWith("compound-owners")),"both owner checks observable");ok(r.l.debug.stream().anyMatch(s->s.startsWith("cache")),"cache budget observable");ok(r.l.debug.stream().anyMatch(s->s.startsWith("result")),"final moved observable");r.l.debug.clear();r.l.tick++;r.tick();eq(0,r.l.debug.size(),"sampled diagnostics skip other ticks");
}
static void pureGuard(){
var g=new ItemTransferGuard();var one=List.of(ItemTransferGuard.Owner.block(1));var two=List.of(ItemTransferGuard.Owner.block(2));var three=List.of(ItemTransferGuard.Owner.block(3));ok(g.allows(1,one,two),"first edge");g.record(1,one,two);ok(g.allows(1,one,two),"repeat same forward edge");ok(!g.allows(1,two,one),"no immediate return");ok(g.allows(1,two,three),"forward allowed after receiving");g.record(1,two,three);ok(!g.allows(1,three,one),"no longer cycle");ok(g.allows(2,three,one),"tick reset clears all history");ok(!g.allows(2,one,one),"no self transfer");ok(!g.allows(2,List.of(),one),"unknown owners fail closed");
// Independent transitive-closure oracle, random owner sets, successes/failures.
Random random=new Random(327);for(int frame=0;frame<30;frame++){boolean[][]edges=new boolean[10][10];for(int trial=0;trial<100;trial++){int a=random.nextInt(10),b=random.nextInt(10),a2=random.nextInt(10),b2=random.nextInt(10);int[]from=trial%3==0?new int[]{a,a2}:new int[]{a},to=trial%5==0?new int[]{b,b2}:new int[]{b};boolean[][]reach=new boolean[10][10];for(int i=0;i<10;i++)reach[i]=edges[i].clone();for(int k=0;k<10;k++)for(int i=0;i<10;i++)for(int j=0;j<10;j++)reach[i][j]|=reach[i][k]&&reach[k][j];boolean expected=true;for(int x:from)for(int y:to)expected&=x!=y&&!reach[y][x];List<ItemTransferGuard.Owner>fs=Arrays.stream(from).mapToObj(x->ItemTransferGuard.Owner.block(x)).toList(),ts=Arrays.stream(to).mapToObj(x->ItemTransferGuard.Owner.block(x)).toList();ok(g.allows(100+frame,fs,ts)==expected,"history matches transitive-closure oracle");if(expected&&random.nextBoolean()){g.record(100+frame,fs,ts);for(int x:from)for(int y:to)edges[x][y]=true;}}}
}
public static void main(String[]args){hubTests();forwardTests();coldAndDiagnostics();pureGuard();System.out.println("Parallel inputs PASS: "+checks+" assertions; actual whole routers + tick DAG, observed MC boundaries only");}}
'''

def check(source=None,filter_source=None,guard_source=None):
    return run(source=source,filter_source=filter_source,guard_source=guard_source,extra_files={'com/gonzotech/machines/network/ChestProbe.java':CHEST_PROBE,'com/gonzotech/machines/network/NetworkProbe.java':PROBE},main_class='com.gonzotech.machines.network.NetworkProbe')

if __name__=='__main__':
    text=SOURCE.read_text();result=check();assert result.returncode==0,result.stdout+result.stderr;print(result.stdout.strip())
    fixtures=ROOT/'audit/fixtures/item_parallel_inputs';old={}
    for record in json.loads((fixtures/'manifest.json').read_text()):
        data=(fixtures/record['file']).read_bytes();assert len(data)==record['bytes'] and hashlib.sha256(data).hexdigest()==record['sha256'];assert subprocess.check_output(['git','show',f"{record['source_commit']}:{record['source']}"],cwd=ROOT)==data;old[Path(record['source']).name]=data.decode()
    # Old326 lacks transferAttempt; reflection-free positive probe calls it later.
    # Execute only the exact same three-input test against the exact old router pair.
    old_probe=PROBE.replace('public static void main(String[]args){hubTests();forwardTests();coldAndDiagnostics();pureGuard();','public static void main(String[]args){hubTests();')
    start=old_probe.index('static void forwardTests()');end=old_probe.index('public static void main',start);old_probe=old_probe[:start]+old_probe[end:]
    old_result=run(source=old['ItemRouting.java'],filter_source=old['ItemFilterRouting.java'],extra_files={'com/gonzotech/machines/network/ChestProbe.java':CHEST_PROBE,'com/gonzotech/machines/network/NetworkProbe.java':old_probe},main_class='com.gonzotech.machines.network.NetworkProbe')
    assert old_result.returncode!=0 and 'AssertionError' in old_result.stderr,'old326 must compile then fail parallel input assertion';print('REJECTED: exact326 pair, same-tick parallel inputs (Git/hash/bytes)')
    if '--mutation-check' in sys.argv:
        guard=(JAVA/'machines/network/ItemTransferGuard.java').read_text();f=(JAVA/'machines/network/ItemFilterRouting.java').read_text()
        mutants=[]
        mutants += [('no-cycle-check','source',text.replace('if (!guard.allows(now, sourceOwners, targetOwners)) {','if (false) {')),
                    ('no-history-record','source',text.replace('guard.record(now, sourceOwners, targetOwners);','')),
                    ('worldless-history','source',text.replace('TRANSFER_GUARDS.computeIfAbsent(level,','TRANSFER_GUARDS.computeIfAbsent(null,')),
                    ('no-tick-reset','guard',guard.replace('if (tick == now) return;','if (tick != Long.MIN_VALUE) return;')),
                    ('no-reachability','guard',guard.replace('if (reaches(to, from)) {','if (false) {')),
                    ('only-direct-return','guard',guard.replace('if (reaches(to, from)) {','if (outgoing.getOrDefault(to, Set.of()).contains(from)) {')),
                    ('receiver-mutex','guard',guard.replace('if (source.isEmpty() || target.isEmpty()) return false;','if (source.isEmpty() || target.isEmpty()) return false;\n        if (outgoing.values().stream().anyMatch(set -> target.stream().anyMatch(set::contains))) return false;')),
                    ('source-freeze','guard',guard.replace('if (source.isEmpty() || target.isEmpty()) return false;','if (source.isEmpty() || target.isEmpty()) return false;\n        if (outgoing.values().stream().anyMatch(set -> source.stream().anyMatch(set::contains))) return false;')),
                    ('filter-cycle-bypass','filter',f.replace('if (!attempt.allowed()) continue;',''))]
        for name,which,changed in mutants:
            assert changed != {'source':text,'filter':f,'guard':guard}[which],name
            result=check(**{{'source':'source','filter':'filter_source','guard':'guard_source'}[which]:changed});assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived: '+name+'\n'+result.stdout+result.stderr;print('REJECTED:',name)
        print(f'Mutation sensitivity: {len(mutants)}/{len(mutants)} assertion-killed')
