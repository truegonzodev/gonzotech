#!/usr/bin/env python3
"""PERF-001/7,8: execute real I/II receiveGtu/pushGtu bodies and real GtBuffer.

Level, route dispatch, BE lifecycle and NBT are narrow boundary doubles. This is
not a full BE/Minecraft compilation, routing integration or native save test.
Mutations affect temporary source copies only; compiler errors are not kills.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
from _radiation_contracts import method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/gonzotech'
ENERGY = 'machines/energy/'
REAL = ['GtBuffer', 'MachineDefs', 'SecondTierDefs', 'TickIntakeBudget']

TEST = r'''package com.gonzotech.machines.block.entity;
import com.gonzotech.machines.energy.*;
import com.gonzotech.machines.network.PipeRouting;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.Random;
public class LegacyAccumulatorSelfTest {
 static long checks;
 static void eq(long want,long got,String why){checks++;if(want!=got)throw new AssertionError(why+": expected "+want+", got "+got);}
 static final BlockPos POS=new BlockPos();
 static class Store {
  final AccumulatorBlockEntity one;final SecondAccumulatorBlockEntity two;
  final Level world;final long intake;
  Store(boolean second,long amount,long tick){
   world=new Level(tick);one=second?null:new AccumulatorBlockEntity();two=second?new SecondAccumulatorBlockEntity():null;
   if(second)two.level=world;else one.level=world;
   intake=second?SecondTierDefs.ACCUMULATOR_GTU_INTAKE:MachineDefs.ACCUMULATOR_GTU_INTAKE;
   buffer().set(amount);
  }
  GtBuffer buffer(){return one!=null?one.gtuBuffer():two.gtuBuffer();}
  long receive(long n,boolean sim){return one!=null?one.receiveGtu(n,sim):two.receiveGtu(n,sim);}
  int dirty(){return one!=null?one.dirty:two.dirty;}
  boolean push(){return one!=null?one.testPush():two.testPush();}
  Object owner(){return one!=null?one:two;}
 }
 static void receive(boolean second){
  Store s=new Store(second,0,10);long limit=s.intake;
  eq(0,s.receive(-1,false),"negative input");eq(0,s.receive(0,false),"zero input");
  eq(limit,s.receive(Long.MAX_VALUE,true),"initial simulation");eq(0,s.buffer().amountAsLong(),"simulation storage readonly");eq(0,s.dirty(),"simulation callbacks readonly");
  eq(limit-17,s.receive(limit-17,false),"source one");
  for(int i=0;i<50;i++)eq(17,s.receive(Long.MAX_VALUE,true),"remaining same-tick simulation");
  eq(17,s.receive(99,false),"source two remaining");eq(0,s.receive(1,false),"aggregate intake exhausted");
  eq(limit,s.buffer().amountAsLong(),"sum conserved");eq(2,s.dirty(),"only successful commits dirty");
  s.world.tick=11;eq(limit,s.receive(Long.MAX_VALUE,true),"future tick simulation");
  s.world.tick=10;eq(0,s.receive(1,false),"future probe cannot reset previous real budget");
  s.buffer().extract(limit,false);eq(0,s.receive(1,false),"outgoing flow does not replenish intake budget");
  s.world.tick=11;eq(limit,s.receive(Long.MAX_VALUE,false),"next tick resets allowance");
  Store other=new Store(second,0,11);eq(limit,other.receive(Long.MAX_VALUE,false),"independent BE/world budget");
  s=new Store(second,0,20);long cap=s.buffer().capacity().longValueExact();s.buffer().set(cap-1);
  eq(1,s.receive(limit,false),"capacity clipped commit");
  s.buffer().extract(limit,false);eq(limit-1,s.receive(limit,false),"only accepted amount spends budget");
  net.minecraft.nbt.CompoundTag tag=new net.minecraft.nbt.CompoundTag();s.buffer().save(tag,"Gtu");
  GtBuffer restored=new GtBuffer(cap);restored.load(tag,"Gtu");eq(s.buffer().amountAsLong(),restored.amountAsLong(),"real GtBuffer decimal NBT roundtrip boundary");
 }
 static void routing(){
  for(boolean fromII:new boolean[]{false,true})for(boolean toII:new boolean[]{false,true}){
   for(long receiver:new long[]{500,1000,2000,4000}){
    Store source=new Store(fromII,2000,100),target=new Store(toII,receiver,100);
    PipeRouting.targets=new Object[]{target.owner()};PipeRouting.loss=0;
    long expected=Math.max(0,2000-receiver)/2;
    eq(expected>0?1:0,source.push()?1:0,"I/II positive difference gate");
    eq(expected,target.buffer().amountAsLong()-receiver,"receiver half difference");
    eq(2000-expected,source.buffer().amountAsLong(),"source debit");
    eq(2000+receiver,source.buffer().amountAsLong()+target.buffer().amountAsLong(),"route conservation");
   }
  }
  Store a=new Store(false,2000,101),b=new Store(true,1000,101);
  PipeRouting.targets=new Object[]{b.owner()};PipeRouting.loss=17;a.push();
  eq(1500,b.buffer().amountAsLong(),"route loss does not increase receiver credit");
  eq(1483,a.buffer().amountAsLong(),"sender-paid route result debited by real push body");
  PipeRouting.loss=0;
  // Independent source routes feeding one owner share its BE intake clock.
  Store receiver=new Store(true,0,200);
  PipeRouting.targets=new Object[]{receiver.owner()};
  for(int i=0;i<5;i++)new Store(false,1000000,200).push();
  eq(receiver.intake,receiver.buffer().amountAsLong(),"multiple routes share total II intake");
  for(boolean second:new boolean[]{false,true}){
   Store source=new Store(second,1000,201);ThirdEnergyStorageBlockEntity third=new ThirdEnergyStorageBlockEntity(2000);
   PipeRouting.targets=new Object[]{third};eq(0,source.push()?1:0,"existing III equilibrium kept");
  }
 }
 static void randomized(boolean second){
  Store s=new Store(second,0,0);Random r=new Random(second?312:311);
  for(int tick=0;tick<200;tick++){
   s.world.tick=tick;s.buffer().set(0);long used=0;
   for(int i=0;i<100;i++){
    long request=r.nextInt((int)s.intake+100);boolean simulate=r.nextBoolean();long expected=Math.min(request,s.intake-used);
    long before=s.buffer().amountAsLong();eq(expected,s.receive(request,simulate),"random multi-call budget");
    if(!simulate)used+=expected;eq(simulate?before:before+expected,s.buffer().amountAsLong(),"random conservation/simulation");
   }
   eq(used,s.buffer().amountAsLong(),"accepted sum per tick");
  }
 }
 public static void main(String[] args){receive(false);receive(true);routing();randomized(false);randomized(true);System.out.println("Legacy accumulator PASS: "+checks+" assertions; actual receive/push, not MC integration");}
}
'''

BOUNDARIES = {
'net/minecraft/nbt/CompoundTag.java': '''package net.minecraft.nbt;public class CompoundTag{private final java.util.Map<String,String> values=new java.util.HashMap<>();public void putString(String k,String v){values.put(k,v);}public String getString(String k){return values.getOrDefault(k,"");}}''',
'net/minecraft/core/BlockPos.java': 'package net.minecraft.core;public class BlockPos{}',
'net/minecraft/world/level/Level.java': 'package net.minecraft.world.level;public class Level{public long tick;public Level(long t){tick=t;}public long getGameTime(){return tick;}}',
'com/gonzotech/machines/energy/Sinks.java': 'package com.gonzotech.machines.energy;public class Sinks{public interface GtuSink{long receiveGtu(long amount,boolean simulate);}}',
'com/gonzotech/machines/network/PipeType.java': 'package com.gonzotech.machines.network;public enum PipeType{WIRE}',
'com/gonzotech/machines/network/PipeRouting.java': '''package com.gonzotech.machines.network;
import com.gonzotech.machines.energy.Sinks.GtuSink;import net.minecraft.core.BlockPos;import net.minecraft.world.level.Level;
/** Route dispatcher boundary; actual route geometry/loss algorithm not executed. */
public class PipeRouting{
 public static Object[] targets=new Object[0];public static long loss;
 public static long drain(Level level,BlockPos pos,PipeType type,long budget,long tick,java.util.function.BiFunction<Object,BlockPos,GtuSink> factory){
  long paid=0;for(Object target:targets){GtuSink sink=factory.apply(target,pos);if(sink==null||budget-paid<=loss)continue;
   long probe=sink.receiveGtu(budget-paid-loss,true);long accepted=sink.receiveGtu(probe,false);
   if(accepted>0)paid+=accepted+loss;}return paid;
 }
}''',
'com/gonzotech/machines/block/entity/ThirdEnergyStorageBlockEntity.java': '''package com.gonzotech.machines.block.entity;
public class ThirdEnergyStorageBlockEntity implements com.gonzotech.machines.energy.Sinks.GtuSink{
 long stored;public ThirdEnergyStorageBlockEntity(long n){stored=n;}public long storedGtuMilli(){return stored;}public long receiveGtu(long n,boolean simulate){if(!simulate)stored+=n;return n;}
}''',
}


def sources(overrides=None):
    overrides = overrides or {}
    result = dict(BOUNDARIES)
    for name in REAL:
        path = ENERGY + name + '.java'
        result['com/gonzotech/' + path] = overrides.get(path, (JAVA / path).read_text())
    for name, defs in [('AccumulatorBlockEntity', 'MachineDefs'), ('SecondAccumulatorBlockEntity', 'SecondTierDefs')]:
        relative = f'machines/block/entity/{name}.java'
        source = overrides.get(relative, (JAVA / relative).read_text())
        # Exact production fields and methods; only MC owner/route APIs are doubles.
        buffer_line = next(x for x in source.splitlines() if 'private final GtBuffer gtu =' in x)
        budget_line = next(x for x in source.splitlines() if 'private final TickIntakeBudget intake =' in x)
        receive = method(source, 'public long receiveGtu(')
        push = method(source, 'private boolean pushGtu(')
        selected_imports = {'GtBuffer', 'TickIntakeBudget', 'MachineDefs', 'SecondTierDefs', 'GtuSink', 'PipeRouting', 'PipeType', 'BlockPos', 'Level'}
        # Preserve the production imports needed by these bodies/fields: a new
        # missing import must not be hidden by a generated wildcard import.
        imports = '\n'.join(line for line in source.splitlines() if line.startswith('import ') and line.rsplit('.', 1)[-1].rstrip(';') in selected_imports)
        result[f'com/gonzotech/{relative}'] = f'''package com.gonzotech.machines.block.entity;
{imports}
public class {name} implements GtuSink{{
{buffer_line}
{budget_line}
 public Level level;public int dirty;public void setChanged(){{dirty++;}}private void updateComparatorOutput(){{}}
 public GtBuffer gtuBuffer(){{return gtu;}}public boolean testPush(){{return pushGtu(level,new BlockPos());}}
{receive}
{push}
}}'''
    result['com/gonzotech/machines/block/entity/LegacyAccumulatorSelfTest.java'] = TEST
    return result


def execute(compiler, java, overrides=None):
    with tempfile.TemporaryDirectory(prefix='gonzo-legacy-accu-') as tmp:
        root = Path(tmp)
        paths = []
        for name, content in sources(overrides).items():
            p = root / 'src' / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content)
            paths.append(str(p))
        compiled = subprocess.run(compiler + ['-d', str(root / 'classes')] + paths, capture_output=True, text=True)
        assert compiled.returncode == 0, 'compile failure is not a mutation kill:\n' + compiled.stdout + compiled.stderr
        return subprocess.run([java, '-cp', str(root / 'classes'), 'com.gonzotech.machines.block.entity.LegacyAccumulatorSelfTest'], capture_output=True, text=True)


if __name__ == '__main__':
    home = os.environ.get('JAVA_HOME')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    if not java or not Path(java).is_file():
        raise SystemExit('Java 21 required')
    compiler = [java, '-jar', os.environ['ECJ_JAR'], '-21', '-proc:none', '-nowarn'] if os.environ.get('ECJ_JAR') else [javac, '--release', '21']
    run = execute(compiler, java)
    assert run.returncode == 0, run.stdout + run.stderr
    print(run.stdout.strip())
    if '--mutation-check' in sys.argv:
        helper = ENERGY + 'TickIntakeBudget.java'
        first = 'machines/block/entity/AccumulatorBlockEntity.java'
        second = 'machines/block/entity/SecondAccumulatorBlockEntity.java'
        originals = {p: (JAVA / p).read_text() for p in (helper, first, second)}
        branch = '''            if (target instanceof SecondAccumulatorBlockEntity other) {
                long cap = Math.max(0, myStored - other.gtuBuffer().amountAsLong()) / 2;
                if (cap <= 0) return null;
                return (amount, simulate) -> other.receiveGtu(Math.min(amount, cap), simulate);
            }
'''
        mutations = [
            (first, branch, ''),
            (first, 'Math.max(0, myStored - other.gtuBuffer().amountAsLong()) / 2', 'Math.max(0, myStored - other.gtuBuffer().amountAsLong())'),
            (helper, 'committed && inputTick == tick ? received : 0', '0'),
            (helper, 'if (!simulate && accepted > 0)', 'if (accepted > 0)'),
            (helper, 'received = used + accepted', 'received = used + Math.min(requested, allowance)'),
            (helper, 'inputTick == tick', 'true'),
        ]
        for path, defs in [(first, 'MachineDefs'), (second, 'SecondTierDefs')]:
            mutations.append((path, f'intake.receive(gtu, amount, (long) {defs}.ACCUMULATOR_GTU_INTAKE,\n            simulate, level == null ? 0L : level.getGameTime())', f'gtu.receive(Math.min(amount, (long) {defs}.ACCUMULATOR_GTU_INTAKE), simulate)'))
        for path, old, new in mutations:
            assert originals[path].count(old) == 1, old
            run = execute(compiler, java, {path: originals[path].replace(old, new)})
            assert run.returncode != 0 and 'AssertionError' in run.stderr, 'mutation survived or non-assertion failure: ' + old + '\n' + run.stdout + run.stderr
            print('REJECTED: ' + path + ' :: ' + old.splitlines()[0])
        print(f'Mutation sensitivity: {len(mutations)}/{len(mutations)} assertion-killed')
