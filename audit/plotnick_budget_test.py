#!/usr/bin/env python3
"""Actual global dispatcher/FIFO + selected BE admission/lifecycle methods; MC/event/logger doubles.
Tests job counts, NOT elapsed milliseconds or native tick integration.
"""
from pathlib import Path
import os, subprocess, tempfile, sys, json, hashlib
import plotnick_work_test as work
from _radiation_contracts import method
ROOT=work.ROOT; JAVA=work.JAVA
TEST=r'''
static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static void eq(long a,long b,String s){ok(a==b,s+": "+a+" != "+b);}
int menuViewers,refreshes;boolean removed;boolean isRemoved(){return removed;}void refreshNetworkStats(ServerLevel l){refreshes++;}
static void tick(ServerLevel l){PlotnickServerBudget.tick(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(l.getServer()));}
static void advance(ServerLevel l){l.getServer().tick++;l.tick++;tick(l);}
static WorkProbe make(ServerLevel l,int x){WorkProbe w=new WorkProbe(l);w.worldPosition=new BlockPos(x,1,0);l.entities.put(w.worldPosition,w);return w;}
public static void main(String[]args){
ServerLevel a=new ServerLevel(),b=new ServerLevel();b.owner=a.owner;
WorkProbe[] ws=new WorkProbe[7];for(int i=0;i<7;i++){ws[i]=make(i%2==0?a:b,i);ws[i].handleButton(BTN_MARK,null);}
for(int i=0;i<1000;i++)ws[0].handleButton(BTN_MARK,null);
tick(a);eq(2,PlotnickServerBudget.snapshot(a.owner).started(),"global exactly TWO across both dimensions");eq(1,ws[0].executed,"first FIFO actor");eq(1,ws[1].executed,"second dimension gets second slot");eq(0,ws[2].executed,"third waits without reclick");eq(5,PlotnickServerBudget.snapshot(a.owner).waiting(),"one ticket/BE despite repeat storm");tick(b);eq(0,ws[2].executed,"duplicate same-server tick cannot reset quota");
advance(a);eq(1,ws[2].executed,"third served next tick");eq(1,ws[3].executed,"fourth FIFO");
WorkProbe newcomer=make(b,100);newcomer.handleButton(BTN_MARK,null);advance(a);eq(1,ws[4].executed,"older fifth before newcomer");eq(1,ws[5].executed,"older sixth before newcomer");eq(0,newcomer.executed,"newcomer cannot jump queue");advance(a);eq(1,ws[6].executed,"seventh eventually served");eq(1,newcomer.executed,"newcomer eventually served");
// Wait time is NOT charged; 200 ticks begin at completion, not initial click.
ws[6].handleButton(BTN_MARK,null);eq(-1,ws[6].calculations.pending(),"completion CD active");a.tick=200;ws[6].handleButton(BTN_MARK,null);eq(-1,ws[6].calculations.pending(),"wait did not consume part of cooldown");a.tick=203;ws[6].handleButton(BTN_MARK,null);eq(BTN_MARK,ws[6].calculations.pending(),"deadline = completion + 200");ws[6].cancelScheduledWork();
// Cancel/unload/replacement/hibernation and preflight failure don't spend start slots or cooldown.
WorkProbe cancel=make(a,200),gone=make(a,201),invalid=make(a,202),valid=make(b,203);
cancel.handleButton(BTN_DRILL,null);cancel.handleButton(BTN_DRILL_STOP,null);eq(-1,cancel.calculations.pending(),"Stop immediate pending-start cancellation");
gone.handleButton(BTN_MARK,null);gone.cancelScheduledWork();eq(-1,gone.calculations.pending(),"unload cancels pending");ok(gone.calculations.request(BTN_MARK,a.tick),"unload before begin no CD");gone.calculations.cancel(a.tick);
invalid.handleButton(BTN_MARK,null);a.entities.remove(invalid.worldPosition);valid.handleButton(BTN_MARK,null);advance(a);eq(0,invalid.executed,"replacement identity cannot execute");eq(1,valid.executed,"invalid ticket doesn't consume heavy slot");ok(invalid.calculations.request(BTN_MARK,a.tick),"replaced preflight no CD");invalid.calculations.cancel(a.tick);
WorkProbe sleepy=make(a,205);sleepy.handleButton(BTN_MARK,null);sleepy.hibernating=true;advance(a);eq(0,sleepy.executed,"sleeping BE cannot run from global queue");
// Background GUI planning is heavy too; it must not bypass the same budget.
WorkProbe g1=make(a,300),g2=make(b,301),g3=make(a,302);for(WorkProbe g:List.of(g1,g2,g3)){g.menuViewers=1;g.networkStatsDirty=true;g.scheduleWork((ServerLevel)g.level);}advance(a);eq(2,PlotnickServerBudget.snapshot(a.owner).started(),"GUI also shares two global starts");eq(1,g1.refreshes,"first GUI");eq(1,g2.refreshes,"second GUI");eq(0,g3.refreshes,"third GUI waits");advance(a);eq(1,g3.refreshes,"GUI eventual progress");
// Bounded invalid-ticket work even if every preflight is stale.
for(int i=0;i<1000;i++){WorkProbe w=make(a,1000+i);w.handleButton(BTN_MARK,null);a.entities.remove(w.worldPosition);}advance(a);eq(64,PlotnickServerBudget.snapshot(a.owner).checked(),"at most 64 preflight checks/tick");eq(0,PlotnickServerBudget.snapshot(a.owner).started(),"preflight failures never spend heavy slots");eq(936,PlotnickServerBudget.snapshot(a.owner).waiting(),"remaining invalid entries drain over later ticks");
PlotnickServerBudget.unload(new net.neoforged.neoforge.event.level.LevelEvent.Unload(a));eq(0,PlotnickServerBudget.snapshot(a.owner).waiting(),"world unload releases queued world callbacks");
// 2048 BEs, alternating worlds. No starvation/duplication; two starts each real server tick.
for(int i=0;i<2048;i++)make(i%2==0?a:b,10000+i).handleButton(BTN_MARK,null);
long base=PlotnickServerBudget.snapshot(a.owner).totalStarted();for(int i=0;i<1024;i++){advance(a);eq(2,PlotnickServerBudget.snapshot(a.owner).started(),"stress hard bound");eq(2048-2*(i+1),PlotnickServerBudget.snapshot(a.owner).waiting(),"stress FIFO drain");}eq(base+2048,PlotnickServerBudget.snapshot(a.owner).totalStarted(),"stress no discard/repeat");
// First actor, disconnect/world change and live mode are revalidated AFTER fair waiting.
ServerPlayer first=new ServerPlayer(a),second=new ServerPlayer(a);a.players.add(first);a.players.add(second);WorkProbe actor=make(a,35000);actor.handleButton(BTN_MARK,first);actor.handleButton(BTN_MARK,second);actor.handleButton(BTN_NETWORK,second);ok(actor.calculationRequester==first,"first actor retained through coalescing/conflict");a.players.remove(first);advance(a);eq(0,actor.executed,"disconnected waiting actor never executes");ok(actor.calculations.request(BTN_MARK,a.tick),"disconnected preflight no CD");actor.calculations.cancel(a.tick);
WorkProbe travel=make(a,35001);travel.handleButton(BTN_MARK,second);second.world=b;advance(a);eq(0,travel.executed,"changed world while waiting cannot execute");second.world=a;
WorkProbe mode=make(a,35002);mode.calculations.request(TASK_ORE_MARK,a.tick);mode.calculations.begin();mode.calculations.complete(a.tick);mode.handleButton(BTN_MARK,null);mode.items.set(SLOT_CARD_A,new ItemStack(ModMachines.MEMORY_CARD.get()));mode.items.get(SLOT_CARD_A).filled=true;advance(a);eq(0,mode.executed,"new live card's active cooldown respected after waiting");eq(0,mode.calculations.save()[BTN_MARK],"unexecuted old mode not charged");eq(0,PlotnickServerBudget.snapshot(a.owner).started(),"rejected live mode doesn't spend heavy slot");
WorkProbe pack=make(a,35003);pack.handleButton(BTN_NETWORK,null);pack.drilling=true;pack.buildQueue.addLast(new NetworkPlanner.Entry(new BlockPos(0,1,0),false));pack.handleButton(BTN_PACK_UP,null);ok(!pack.drilling&&pack.buildQueue.isEmpty(),"pack cancellation doesn't wait for global slot");eq(0,pack.executed,"pack scan itself remains queued");advance(a);eq(BTN_PACK_UP,pack.lastOperation,"replacement pack request eventually runs");pack.handleButton(BTN_MARK,null);ok(PlotnickServerBudget.queued(a,pack),"new work queued after pack calculation");pack.handleButton(BTN_PACK_UP,null);ok(!PlotnickServerBudget.queued(a,pack),"pack during own CD removes canceled ticket without stale callback");eq(-1,pack.calculations.pending(),"pack during CD doesn't enqueue rescan");
// Exceptions spend one start and mode cooldown, dispatcher exits cleanly.
WorkProbe fail=make(a,40000);fail.throwCalculation=true;fail.handleButton(BTN_MARK,null);a.owner.tick++;try{tick(a);throw new AssertionError("expected fixture failure");}catch(IllegalStateException expected){}eq(1,PlotnickServerBudget.snapshot(a.owner).started(),"failed begun calculation charged");fail.throwCalculation=false;fail.handleButton(BTN_MARK,null);eq(-1,fail.calculations.pending(),"failure CD retained");tick(a);eq(1,PlotnickServerBudget.snapshot(a.owner).started(),"reentry same tick cannot reset spent work");
// Identity keys (not equals), duplicate ticket retains first runnable, O(1) head/middle/tail cancellation.
PlotnickFairQueue<Object,Integer> q=new PlotnickFairQueue<>();Object k1=new String("same"),k2=new String("same"),k3=new Object();q.offer(k1,1);q.offer(k2,2);q.offer(k1,99);q.offer(k3,3);eq(3,q.size(),"identity-distinct equal keys remain separate");eq(1,q.poll(),"coalescing retains first ticket and FIFO");q.cancel(k3);eq(2,q.poll(),"identity-distinct equal keys survive tail removal");eq(0,q.size(),"queue emptied");q.offer(k1,1);q.offer(k2,2);q.offer(k3,3);q.cancel(k2);eq(1,q.poll(),"middle cancel keeps head");eq(3,q.poll(),"middle cancel keeps tail");
WorkProbe dead=make(a,50000),survivor=make(b,50001);dead.handleButton(BTN_MARK,null);survivor.handleButton(BTN_MARK,null);PlotnickServerBudget.unload(new net.neoforged.neoforge.event.level.LevelEvent.Unload(a));eq(1,PlotnickServerBudget.snapshot(a.owner).waiting(),"world unload doesn't cancel other world");advance(b);eq(1,survivor.executed,"other world continues");PlotnickServerBudget.stop(new net.neoforged.neoforge.event.server.ServerStoppedEvent(a.owner));eq(0,PlotnickServerBudget.snapshot(a.owner).totalStarted(),"server stop clears counters and queue");
ServerLevel fresh=new ServerLevel();make(fresh,0).handleButton(BTN_MARK,null);tick(fresh);eq(1,PlotnickServerBudget.snapshot(fresh.owner).started(),"new server independent budget");
System.out.println("Plotnick global budget PASS: "+checks+" assertions; actual dispatcher/FIFO/selected BE methods; modeled lifecycle, not native");}
'''
def files(be=None,budget=None,fifo=None):
    out=work.files(be);be=be or work.SOURCE.read_text()
    for n,text in [('PlotnickServerBudget',budget),('PlotnickFairQueue',fifo)]:out[f'com/gonzotech/machines/miner/{n}.java']=text or (JAVA/f'machines/miner/{n}.java').read_text()
    probe=out['WorkProbe.java'].replace(work.TEST,TEST).replace('void scheduleWork(ServerLevel l){}','')
    for sig in ['private void scheduleWork(', 'private void processScheduledWork(', 'private void cancelScheduledWork(']:probe=probe[:-1]+method(be,sig)+'}'
    out['WorkProbe.java']=probe
    p='net/minecraft/server/level/ServerLevel.java';s=out[p].replace('public class ServerLevel extends Level{','public class ServerLevel extends Level{public net.minecraft.server.MinecraftServer owner=new net.minecraft.server.MinecraftServer();public net.minecraft.server.MinecraftServer getServer(){return owner;}public final java.util.Set<BlockPos> unloaded=new java.util.HashSet<>();public boolean hasChunkAt(BlockPos p){return !unloaded.contains(new BlockPos(p.getX()>>4,0,p.getZ()>>4));}public final java.util.Map<BlockPos,Object> entities=new java.util.HashMap<>();');s=s.replace('return null;}public void sendParticles','return entities.get(p);}public void sendParticles');out[p]=s
    out.update({
    'net/minecraft/server/MinecraftServer.java':'package net.minecraft.server;public class MinecraftServer{public int tick;public int getTickCount(){return tick;}}',
    'net/neoforged/neoforge/event/tick/ServerTickEvent.java':'package net.neoforged.neoforge.event.tick;import net.minecraft.server.MinecraftServer;public class ServerTickEvent{public record Post(MinecraftServer getServer){}}',
    'net/neoforged/neoforge/event/server/ServerStoppedEvent.java':'package net.neoforged.neoforge.event.server;public record ServerStoppedEvent(net.minecraft.server.MinecraftServer getServer){}',
    'org/slf4j/Logger.java':'package org.slf4j;public class Logger{public boolean isDebugEnabled(){return false;}public void debug(String s,Object...args){}}',
    'com/mojang/logging/LogUtils.java':'package com.mojang.logging;public class LogUtils{public static org.slf4j.Logger getLogger(){return new org.slf4j.Logger();}}'})
    return out

def run(**kwargs):
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
    with tempfile.TemporaryDirectory(prefix='plotnick-budget-') as tmp:
        root=Path(tmp);paths=[]
        for name,body in files(**kwargs).items():p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
        c=subprocess.run([java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-nowarn','-d',str(root/'classes')]+paths,text=True,capture_output=True);assert c.returncode==0,'Compiler failure NOT a mutation kill:\n'+c.stdout+c.stderr
        return subprocess.run([java,'-cp',str(root/'classes'),'WorkProbe'],text=True,capture_output=True)
if __name__=='__main__':
    r=run();assert r.returncode==0,r.stdout+r.stderr;print(r.stdout.strip())
    be=work.SOURCE.read_text();assert 'be.scheduleWork(server);' in be and 'be.processCalculation(server);' not in be
    assert 'cancelScheduledWork();' in method(be,'@Override public void setRemoved(') and 'cancelScheduledWork();' in method(be,'@Override public void onChunkUnloaded(')
    manifest=json.loads((ROOT/'audit/fixtures/work_329/manifest.json').read_text())
    record=manifest['files'][0];data=(ROOT/'audit/fixtures/work_329'/record['file']).read_bytes();assert hashlib.sha256(data).hexdigest()==record['sha256'] and len(data)==record['bytes'];assert data==subprocess.check_output(['git','show',record['source_commit']+':'+record['source']],cwd=ROOT)
    old=data.decode();r=run(be=be.replace(method(be,'private void processCalculation('),method(old,'private void processCalculation(')));assert r.returncode!=0 and 'AssertionError' in r.stderr,'exact329 unbudgeted method must compile then assertion-fail';print('REJECTED: exact329 unbudgeted processCalculation (Git/hash/bytes)')
    if '--mutation-check' in sys.argv:
        budget=(JAVA/'machines/miner/PlotnickServerBudget.java').read_text();fifo=(JAVA/'machines/miner/PlotnickFairQueue.java').read_text()
        cases=[('pack-leaks-canceled-ticket',{'be':be.replace('if (id == BTN_PACK_UP && calculations.pending() != BTN_PACK_UP) {\n            PlotnickServerBudget.cancel(server, this);','if (id == BTN_PACK_UP && calculations.pending() != BTN_PACK_UP) {')}),('third-start',{'budget':budget.replace('STARTS_PER_TICK = 2','STARTS_PER_TICK = 3')}),('unbounded-preflight',{'budget':budget.replace('CHECKS_PER_TICK = 64','CHECKS_PER_TICK = 6400')}),('reset-on-duplicate-tick',{'budget':budget.replace('state.tick != server.getTickCount()','true')}),('duplicate-jumps-queue',{'fifo':fifo.replace('if (entries.containsKey(key)) return;','cancel(key);')}),('equal-not-identity',{'fifo':fifo.replace('IdentityHashMap','java.util.HashMap').replace('import java.util.java.util.HashMap;','import java.util.HashMap;')}),('no-gui-budget',{'be':be.replace('networkStatsDirty && PlotnickServerBudget.start(server)','networkStatsDirty')}),('no-replacement-check',{'be':be.replace('|| server.getBlockEntity(worldPosition) != this','')}),('unload-clears-other-world',{'budget':budget.replace('work.level() == level','true')})]
        # Duplicate ticket ordering is also exercised explicitly by the stress/probe above.
        for label,kwargs in cases:
            r=run(**kwargs);assert r.returncode!=0 and 'AssertionError' in r.stderr,label+' survived\n'+r.stdout+r.stderr;print('REJECTED:',label)
