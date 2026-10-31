#!/usr/bin/env python3
"""PERF-001/4: execute actual ItemRouting cache methods, not a TTL mirror.

BFS discovery, MC container/entity lookup and world clock are boundary doubles.
This checks cache expiry/invalidation, not native minecart motion or item transfer.
--mutation-check requires assertion failures; compiler failures are not kills.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys
import tempfile
from _radiation_contracts import method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/java/com/gonzotech/machines/network/ItemRouting.java'

STUBS = {
    'net/minecraft/core/BlockPos.java': 'package net.minecraft.core;public record BlockPos(long value){public long asLong(){return value;}}',
    'com/gonzotech/machines/network/PipeRoutingDiagnostics.java':'package com.gonzotech.machines.network;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;public class PipeRoutingDiagnostics{public static boolean itemEnabled(Level l){return false;}public static void itemEvent(Level l,BlockPos p,BlockPos e,String s,String d){}}',
    'net/minecraft/core/Direction.java': 'package net.minecraft.core;public enum Direction{NORTH}',
    'net/minecraft/world/Container.java': 'package net.minecraft.world;public interface Container{}',
    'net/minecraft/world/level/Level.java': 'package net.minecraft.world.level;public class Level{public long tick;public boolean client;public long getGameTime(){return tick;}public boolean isClientSide(){return client;}}',
}

PROBE = r'''
 static class PipeRoutingDiagnostics { static boolean itemEnabled(Level l){return false;}static void itemEvent(Level l,BlockPos p,BlockPos e,String s,String d){} }
 private record Box(String name) implements Container {}
 private static final Map<Level,Map<Long,List<Sink>>> DISCOVERY=new IdentityHashMap<>();
 private static int scans,checks;
 private static List<Sink> collectSinks(Level level,BlockPos pos,int[] visited,int budget){
  scans++;visited[0]=1;
  // Stand-in for a complete small BFS: no attempt to simulate PERF-001/3.
  return List.copyOf(DISCOVERY.getOrDefault(level,Map.of()).getOrDefault(pos.asLong(),List.of()));
 }
 private static boolean sinkLive(Level level,Sink sink){
  // Entity/block lookup boundary. The real route geometry is not executed here.
  return DISCOVERY.getOrDefault(level,Map.of()).values().stream().flatMap(List::stream)
      .anyMatch(current -> current.container()==sink.container());
 }
 private static void fixture(Level level,BlockPos start,Container box){
  DISCOVERY.computeIfAbsent(level,k->new HashMap<>()).put(start.asLong(),
   box==null?List.of():List.of(new Sink(box,Direction.NORTH,List.of(start),new BlockPos(start.asLong()+1))));
 }
 private static void eq(long want,long got,String label){checks++;if(want!=got)throw new AssertionError(label+": expected "+want+", got "+got);}
 private static void fresh(){TOPO_CACHE.clear();TOPO_VERSION.clear();DISCOVERY.clear();topoBudgetTick=Long.MIN_VALUE;topoBudget=0;scans=0;}
 public static void main(String[] args){
  eq(40,TOPO_TTL_TICKS,"existing TTL unchanged");eq(2048,TOPO_BUDGET_PER_TICK,"existing BFS budget unchanged");
  fresh();Level world=new Level();BlockPos start=new BlockPos(1);fixture(world,start,null);
  eq(0,sinksCached(world,start).size(),"initial empty search");eq(1,scans,"initial BFS");
  Box cart=new Box("arrived");fixture(world,start,cart);
  for(int age=1;age<=40;age++){
   world.tick=age;eq(0,sinksCached(world,start).size(),"unchanged geometry does not force early BFS");
   eq(1,scans,"one cached empty result within existing TTL");
  }
  world.tick=41;eq(1,sinksCached(world,start).size(),"arriving endpoint found after expiry WITHOUT block edit");
  eq(2,scans,"expired negative result rebuilt");
  var found=sinksCached(world,start);eq(1,liveSinks(world,start,found).size(),"known live endpoint retained");
  fixture(world,start,null);eq(0,liveSinks(world,start,found).size(),"departed endpoint is not used");
  eq(0,sinksCached(world,start).size(),"live rejection invalidated cache before TTL");eq(3,scans,"live rejection forces new discovery");
  fixture(world,start,cart);world.tick=42;onWorldEdit(world);
  eq(1,sinksCached(world,start).size(),"version change rebuilds WITHIN TTL");eq(4,scans,"world edit is not hidden by young age");
  int before=scans;for(int age=43;age<=82;age++){world.tick=age;sinksCached(world,start);}
  eq(before,scans,"cache hits do not silently refresh builtTick");
  world.tick=83;sinksCached(world,start);eq(before+1,scans,"positive cache also expires");

  // The truth table is checked via real sinksCached, not a reimplemented condition.
  for(boolean staleVersion:new boolean[]{false,true})for(int age:new int[]{0,1,39,40,41,1000}){
   fresh();world=new Level();fixture(world,start,null);sinksCached(world,start);
   fixture(world,start,cart);world.tick=age;if(staleVersion)onWorldEdit(world);
   boolean refresh=staleVersion||age>40;
   eq(refresh?1:0,sinksCached(world,start).size(),"version/age matrix");eq(refresh?2:1,scans,"matrix BFS count");
  }
  fresh();world=new Level();Level other=new Level();fixture(world,start,cart);fixture(other,start,null);
  eq(1,sinksCached(world,start).size(),"world A endpoint");eq(0,sinksCached(other,start).size(),"same address world B independent");
  BlockPos second=new BlockPos(90);fixture(world,second,null);eq(0,sinksCached(world,second).size(),"start positions independent");
  world.client=true;sinksCached(world,start);sinksCached(world,start);eq(5,scans,"existing client branch discovers each call");

  fresh();world=new Level();fixture(world,start,null);sinksCached(world,start);
  world.tick=41;fixture(world,start,cart);topoBudgetTick=41;topoBudget=0;
  eq(0,sinksCached(world,start).size(),"existing exhausted-budget fallback preserved, not solved as BFS fairness");
  eq(1,scans,"no BFS over exhausted budget");
  world.tick=42;eq(1,sinksCached(world,start).size(),"next available tick retries expired cache");eq(2,scans,"retry discovers endpoint");
  System.out.println("Routing cache PASS: "+checks+" assertions; actual cache methods, MC/BFS boundaries only");
 }
'''


def generated(source):
    declarations = '\n'.join(re.findall(r'^    static final int TOPO_\w+ = [\d_]+;', source, re.M))
    start = source.index('    private static final java.util.Map<Level, long[]> TOPO_VERSION')
    end = source.index('    /** Миксин зовёт', start)
    fields = source[start:end]
    records = method(source, 'private record Sink(') + '\n' + method(source, 'private record CachedSinks(')
    bodies = '\n'.join(method(source, signature) for signature in (
        'public static void onWorldEdit(', 'private static long topoVersion(',
        'private static List<Sink> sinksCached(', 'private static List<Sink> liveSinks('))
    return ('package com.gonzotech.machines.network;\nimport java.util.*;\n'
            'import net.minecraft.core.*;import net.minecraft.world.Container;import net.minecraft.world.level.Level;\n'
            'public class RoutingCacheProbe{\n' + declarations + '\n' + fields + records + bodies + PROBE + '\n}\n')


def run(compiler, java, source):
    with tempfile.TemporaryDirectory(prefix='gonzo-routing-cache-') as folder:
        root = Path(folder)
        files = dict(STUBS)
        files['com/gonzotech/machines/network/RoutingCacheProbe.java'] = generated(source)
        paths = []
        for name, content in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
            paths.append(str(path))
        compiled = subprocess.run(compiler + ['-d', str(root / 'classes')] + paths, capture_output=True, text=True)
        assert compiled.returncode == 0, 'compiler failure is not a kill:\n' + compiled.stdout + compiled.stderr
        return subprocess.run([java, '-cp', str(root / 'classes'), 'com.gonzotech.machines.network.RoutingCacheProbe'], capture_output=True, text=True)


if __name__ == '__main__':
    home = os.environ.get('JAVA_HOME')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    if not java or not Path(java).is_file():
        raise SystemExit('Java 21 required')
    compiler = [java, '-jar', os.environ['ECJ_JAR'], '-21', '-proc:none', '-nowarn'] if os.environ.get('ECJ_JAR') else [javac, '--release', '21']
    source = SOURCE.read_text()
    result = run(compiler, java, source)
    assert result.returncode == 0, result.stdout + result.stderr
    print(result.stdout.strip())
    if '--mutation-check' in sys.argv:
        condition = 'hit.version() == version && now - hit.builtTick() <= TOPO_TTL_TICKS'
        mutations = [
            (condition, condition.replace(' && ', ' || ')),
            (condition, 'hit.version() == version'),
            (condition, 'now - hit.builtTick() <= TOPO_TTL_TICKS'),
            (condition, 'false'),
            ('now - hit.builtTick() <= TOPO_TTL_TICKS', 'now - hit.builtTick() < TOPO_TTL_TICKS'),
        ]
        for old, new in mutations:
            assert source.count(old) == 1, old
            result = run(compiler, java, source.replace(old, new))
            assert result.returncode != 0 and 'AssertionError' in result.stderr, 'mutation survived or non-assertion failure: ' + new + '\n' + result.stdout + result.stderr
            print('REJECTED: ' + new)
        print(f'Mutation sensitivity: {len(mutations)}/{len(mutations)} assertion-killed')
