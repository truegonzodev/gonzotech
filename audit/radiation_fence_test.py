#!/usr/bin/env python3
"""Actual Fence handler + whole creative bridge + actual radiation core; packet/menu/copy-hook doubles.
No native codec, event bus, real client ordering or Mixin bootstrap proof.
"""
from pathlib import Path
import os,subprocess,tempfile,sys,json,hashlib
from _radiation_contracts import method
import radiation_engine_test as core
ROOT=Path(__file__).resolve().parents[1];SOURCE=ROOT/'src/main/java/com/gonzotech/radiation/engine/RadiationViewNetwork.java'
PROBE=r'''
import java.util.*;import net.minecraft.server.level.*;import net.minecraft.world.item.*;import net.minecraft.world.inventory.*;import com.gonzotech.radiation.engine.*;
public class FenceProbe{
static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static void eq(long a,long b,String s){ok(a==b,s+": "+a+" != "+b);}static void dose(double a,ItemStack s,String label){ok(Math.abs(a-ExternalRadiation.read(s).rate())<1e-8,label);}static void send(ServerPlayer p,int menu,long seq,boolean begin,int count){RadiationViewNetwork.receive(p,new RadiationViewNetwork.Fence(menu,seq,begin,count,0));}
public static void main(String[]args){
for(int repeats:new int[]{1,2,100,1000}){
 ServerPlayer p=new ServerPlayer();Item item=new Item("ore",0,1);ItemStack original=new ItemStack(item,64);p.inventoryMenu.slots.add(new Slot(original));p.inventoryMenu.slots.add(new Slot(ItemStack.EMPTY));ExternalRadiation.write(original,17,12);
 send(p,0,1,true,0);int copies=ItemStack.copies;
 for(int i=0;i<repeats;i++)send(p,0,1,true,0);eq(copies,ItemStack.copies,"duplicate begin before ACK must not recopy session");
 CreativeRadiationBridge.beforePacket(p);original.shrink(16);ItemStack destination=new ItemStack(item,16);p.inventoryMenu.slots.set(1,new Slot(destination));
 send(p,0,2,true,0);eq(copies,ItemStack.copies,"newer same-menu begin retains original provenance snapshot");send(p,0,1,true,0);send(p,0,1,false,0);eq(0,RadiationViewNetwork.ack(p),"stale end cannot ACK/finish newer phase");
 send(p,0,2,false,0);eq(2,RadiationViewNetwork.ack(p),"matched end ACK");dose(17,original,"origin per-unit dose");dose(17,destination,"destination provenance across coalesced begin");eq(64,original.getCount()+destination.getCount(),"Fence never edits counts");
 copies=ItemStack.copies;send(p,0,2,true,0);send(p,0,1,false,0);eq(copies,ItemStack.copies,"ACKed replay cannot snapshot");eq(2,RadiationViewNetwork.ack(p),"ACK monotonic");
 send(p,0,3,true,0);CreativeRadiationBridge.beforePacket(p);original.grow(4);destination=new ItemStack(item,12);p.inventoryMenu.slots.set(1,new Slot(destination));p.containerMenu=new AbstractContainerMenu(9);send(p,0,3,false,0);eq(3,RadiationViewNetwork.ack(p),"valid old-menu end survives screen close");dose(17,destination,"close reconciliation dose preserved");
 send(p,8,4,true,0);send(p,8,4,false,0);eq(4,RadiationViewNetwork.ack(p),"end barrier ACK even if begin menu raced with open");
 send(p,9,5,true,0);send(p,7,5,false,0);eq(4,RadiationViewNetwork.ack(p),"mismatched end cannot discard pending");send(p,9,5,false,65);eq(4,RadiationViewNetwork.ack(p),"invalid count rejected");send(p,9,5,false,0);eq(5,RadiationViewNetwork.ack(p),"valid end not poisoned by rejected end");
 send(p,9,0,true,0);send(p,9,-1,false,0);eq(5,RadiationViewNetwork.ack(p),"nonpositive rejected");
 // Reopening same numeric menu id must not coalesce across different menu objects.
 p.containerMenu=p.inventoryMenu;send(p,0,6,true,0);copies=ItemStack.copies;p.inventoryMenu=new AbstractContainerMenu(0);p.inventoryMenu.slots.add(new Slot(new ItemStack(item,1)));p.containerMenu=p.inventoryMenu;send(p,0,7,true,0);ok(ItemStack.copies>copies,"reused menu id has distinct identity");send(p,0,7,false,0);
}
ServerPlayer q=new ServerPlayer();Item material=new Item("metal",0,1);ItemStack source=new ItemStack(material,8);ExternalRadiation.write(source,29,40);q.inventoryMenu.slots.add(new Slot(source));q.inventoryMenu.slots.add(new Slot(ItemStack.EMPTY));send(q,0,1,true,0);CreativeRadiationBridge.beforePacket(q);source.shrink(3);ItemStack moved=new ItemStack(material,3);q.inventoryMenu.slots.set(1,new Slot(moved));q.containerMenu=new AbstractContainerMenu(12);send(q,12,2,true,0);dose(29,moved,"replacement non-inventory menu reconciles old creative snapshot");send(q,12,2,false,0);eq(2,RadiationViewNetwork.ack(q),"replacement menu end ACK");eq(8,source.getCount()+moved.getCount(),"menu replacement no count edits");
ServerPlayer a=new ServerPlayer(),b=new ServerPlayer();send(a,0,1,true,0);send(b,0,1,true,0);send(a,0,1,false,0);eq(0,RadiationViewNetwork.ack(b),"players isolated");send(b,0,1,false,0);eq(1,RadiationViewNetwork.ack(b),"other player valid end");
System.out.println("Radiation fence PASS: "+checks+" assertions; actual handler/creative bridge/core, MC boundaries only");}}
'''
def run(handler=None):
    source=SOURCE.read_text();handler=handler or method(source,'private static void handleFence(')
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java');compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-nowarn']
    with tempfile.TemporaryDirectory(prefix='fence-') as t:
        root=Path(t);core.prepare(root)
        p=root/'net/minecraft/world/item/ItemStack.java';s=p.read_text().replace(' public ItemStack copy(){',' public static int copies;\n public ItemStack copy(){\n copies++;');s=s.replace(' public static boolean matches(',''' public ItemStack copyWithCount(int n){ItemStack s=copy();s.count=n;return s;}
 public static boolean isSameItemSameComponents(ItemStack a,ItemStack b){return a.item==b.item&&a.data.equals(b.data);}
 public static boolean matches(''');p.write_text(s)
        out={
        'net/minecraft/world/inventory/Slot.java':'package net.minecraft.world.inventory;import net.minecraft.world.item.ItemStack;public record Slot(ItemStack getItem){}',
        'net/minecraft/world/inventory/AbstractContainerMenu.java':'package net.minecraft.world.inventory;import java.util.*;public class AbstractContainerMenu{public final int containerId;public final List<Slot> slots=new ArrayList<>();public AbstractContainerMenu(int id){containerId=id;}}',
        'net/minecraft/server/level/ServerPlayer.java':'''package net.minecraft.server.level;import net.minecraft.world.inventory.*;public class ServerPlayer{public AbstractContainerMenu inventoryMenu=new AbstractContainerMenu(0),containerMenu=inventoryMenu;public GameMode gameMode=new GameMode();public Object registryAccess(){return null;}public static class GameMode{public boolean isCreative(){return true;}}}''',
        'com/gonzotech/radiation/engine/CreativeRadiationBridge.java':(ROOT/'src/main/java/com/gonzotech/radiation/engine/CreativeRadiationBridge.java').read_text(),
        'com/gonzotech/radiation/engine/RadiationViewNetwork.java':'''package com.gonzotech.radiation.engine;import java.util.*;import net.minecraft.server.level.*;import net.minecraft.world.item.*;public class RadiationViewNetwork{private static final Map<ServerPlayer,Long> ACK=new WeakHashMap<>();'''+method(source,'private record PendingFence(')+'''private static final Map<ServerPlayer,PendingFence> PENDING=new WeakHashMap<>();public record Fence(int menu,long sequence,boolean begin,int cursorCount,int cursorFingerprint){}public static void receive(ServerPlayer p,Fence f){handleFence(p,f);}public static long ack(ServerPlayer p){return ACK.getOrDefault(p,0L);}public static int fingerprint(ItemStack s,Object registry){return s.getCount()*31+System.identityHashCode(s.getItem());}'''+handler+'}',
        'FenceProbe.java':PROBE}
        for n,c in out.items():p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(c)
        c=subprocess.run(compiler+['-d',str(root/'classes')]+[str(p) for p in root.rglob('*.java')],capture_output=True,text=True);assert c.returncode==0,'Compiler failure NOT a mutation kill:\n'+c.stdout+c.stderr
        return subprocess.run([java,'-cp',str(root/'classes'),'FenceProbe'],capture_output=True,text=True)
if __name__=='__main__':
    source=SOURCE.read_text();r=run();assert r.returncode==0,r.stdout+r.stderr;print(r.stdout.strip())
    for a in ['handleFence(player, v);','PENDING.remove(player);','PENDING.clear();']:assert a in source,'wiring '+a
    record=json.loads((ROOT/'audit/fixtures/work_328/manifest.json').read_text())['files'][2];data=(ROOT/'audit/fixtures/work_328'/record['file']).read_bytes();assert len(data)==record['bytes'] and hashlib.sha256(data).hexdigest()==record['sha256'];assert data==subprocess.check_output(['git','show',record['source_commit']+':'+record['source']],cwd=ROOT)
    old=data.decode();a=old.index('            if (c.player() instanceof ServerPlayer player');b=old.index('\n        }));',a);body=old[a:b].replace('if (c.player() instanceof ServerPlayer player\n                    &&','if (');oldhandler='private static void handleFence(ServerPlayer player,Fence v){'+body+'}'
    r=run(oldhandler);assert r.returncode!=0 and 'AssertionError' in r.stderr,'exact328 handler must compile then assertion-fail';print('REJECTED: exact328 handler (Git/hash/bytes)')
    if '--mutation-check' in sys.argv:
        handler=method(source,'private static void handleFence(')
        tests=[('orphan-replaced-snapshot','if (pending != null) CreativeRadiationBridge.finish(player);',''),('begin-replay','if (pending != null && v.sequence <= pending.sequence()) return;','if (false) return;'),('always-snapshot','pending == null || pending.menu() != player.containerMenu','true'),('stale-end-closes-newer','if (pending != null && (v.sequence != pending.sequence() || v.menu != pending.menu().containerId)) return;','if (false) return;'),('end-requires-current-menu','if (v.menu == player.inventoryMenu.containerId)','if (v.menu == player.containerMenu.containerId && v.menu == player.inventoryMenu.containerId)'),('lost-ACK','ACK.put(player, v.sequence);',''),('numeric-menu-only','pending.menu() != player.containerMenu','pending.menu().containerId != player.containerMenu.containerId')]
        for label,a,b in tests:
            assert a in handler;r=run(handler.replace(a,b));assert r.returncode!=0 and 'AssertionError' in r.stderr,label+' survived\n'+r.stdout+r.stderr;print('REJECTED:',label)
