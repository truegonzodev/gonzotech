#!/usr/bin/env python3
"""Execute the real logger in A/B JVMs and validate its comparison parser."""
from pathlib import Path
import tempfile
from item_loaded_chunks_test import run
from pipe_compare import read, compare

PROBE = '''package com.gonzotech.machines.network;
public class BenchmarkProbe {
 static void ok(boolean b){if(!b)throw new AssertionError("benchmark contract");}
 public static void main(String[]args){
  PipeBenchmark.reset();String id=PipeBenchmark.identity();ok(PipeBenchmark.report("")==null);
  for(int window=0;window<4;window++){
   for(int t=0;t<200;t++){
    PipeBenchmark.beginTick();PipeBenchmark.add(PipeBenchmark.Metric.NODE_ITEMS,2);
    PipeBenchmark.resource("WIRE",7);PipeBenchmark.resource("HEAT",3);PipeBenchmark.endTick(t%3);
    ok(PipeBenchmark.ready()==(t==199));
   }
   String row=PipeBenchmark.report("cells=3 queries=2 corridors=0 pending=1");
   ok(row.contains(id)&&row.contains(" NODE_ITEMS=400 ")&&row.contains("ticks=200 "));
   ok(row.contains("totalTicks="+((window+1)*200)+" ")&&row.contains("pendingMax=2 "));
   ok(row.contains("WIRE=1400")&&row.contains("HEAT=600"));
   System.out.println(row);ok(!PipeBenchmark.ready()&&PipeBenchmark.report("")==null);
  }
  PipeBenchmark.beginTick();PipeBenchmark.endTick(0);
  String partial=PipeBenchmark.report("cells=0 queries=0 corridors=0 pending=0");
  ok(partial.contains("ticks=1 ")&&partial.contains(" NODE_ITEMS=0 "));System.out.println(partial);
  PipeBenchmark.reset();ok(!PipeBenchmark.identity().equals(id)&&PipeBenchmark.report("")==null);
 }
}'''

if __name__ == '__main__':
    with tempfile.TemporaryDirectory() as tmp:
        paths=[]
        for variant in ('baseline','experimental'):
            result=run(extra_files={'com/gonzotech/machines/network/BenchmarkProbe.java':PROBE},
                       main_class='com.gonzotech.machines.network.BenchmarkProbe',
                       java_args=[f'-Dgonzotech.pipe.variant={variant}','-Dgonzotech.pipe.profile=true'])
            assert result.returncode==0,result.stderr
            path=Path(tmp)/variant;path.write_text(result.stdout,encoding='utf8');paths.append(path)
        a=read(paths[0],'baseline');b=read(paths[1],'experimental')
        assert len(a)==len(b)==1 and a[0]['charges']=={'WIRE':1400,'HEAT':600}
        assert len(read(paths[0],'baseline',skip_ticks=0))==4
        report=compare(a,b)
        assert 'NODE_ITEMS/тик | 2.0000 | 2.0000' in report and 'короткая' in report
        for path,variant,session,skip in [(paths[0],'experimental',None,0),(paths[0],'baseline','absent',0),(paths[0],'baseline',None,9999)]:
            try:read(path,variant,session,skip)
            except ValueError:pass
            else:raise AssertionError('invalid selection accepted')
        text=paths[0].read_text(encoding='utf8');session=a[0]['session']
        paths[0].write_text(text+text.replace(session,'second'),encoding='utf8')
        try:read(paths[0],'baseline')
        except ValueError:pass
        else:raise AssertionError('mixed sessions accepted')
        assert len(read(paths[0],'baseline',session))==1
        paths[0].write_text(text+text,encoding='utf8')
        try:read(paths[0],'baseline')
        except ValueError:pass
        else:raise AssertionError('duplicate samples accepted')
    print('Real A/B logger windows, reset, partial flush, warmup, identity and parser rejection PASS')
