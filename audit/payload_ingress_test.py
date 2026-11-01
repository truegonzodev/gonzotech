#!/usr/bin/env python3
"""Actual guard/window/config/Plotnick handler + actual Fence registration, pinned NeoForge registrar/wrapper.
NetworkRegistry, connection, executor, menu, codecs and events are boundary doubles. NOT Netty/native.
"""
from pathlib import Path
import os, subprocess, tempfile, sys, json, hashlib, re
from _radiation_contracts import method
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'src/main/java/com/gonzotech';FIX=ROOT/'audit/fixtures/payload_ingress'
STUBS={
'com/gonzotech/GonzoTechMod.java':'package com.gonzotech;public class GonzoTechMod{public static final String MOD_ID="gonzotech";}',
'net/minecraft/network/Connection.java':'package net.minecraft.network;public class Connection{public boolean connected=true;public boolean isConnected(){return connected;}}',
'net/minecraft/network/FriendlyByteBuf.java':'package net.minecraft.network;public class FriendlyByteBuf{}',
'net/minecraft/network/RegistryFriendlyByteBuf.java':'package net.minecraft.network;public class RegistryFriendlyByteBuf extends FriendlyByteBuf{}',
'net/minecraft/network/ConnectionProtocol.java':'package net.minecraft.network;public enum ConnectionProtocol{PLAY,CONFIGURATION}',
'net/minecraft/network/protocol/PacketFlow.java':'package net.minecraft.network.protocol;public enum PacketFlow{SERVERBOUND,CLIENTBOUND}',
'net/minecraft/resources/ResourceLocation.java':'package net.minecraft.resources;public record ResourceLocation(String namespace,String path){public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}}',
'net/minecraft/network/protocol/common/custom/CustomPacketPayload.java':'package net.minecraft.network.protocol.common.custom;import net.minecraft.resources.ResourceLocation;public interface CustomPacketPayload{record Type<T extends CustomPacketPayload>(ResourceLocation id){}Type<? extends CustomPacketPayload> type();}',
'net/minecraft/network/codec/StreamCodec.java':'''package net.minecraft.network.codec;import java.util.function.*;public interface StreamCodec<B,V>{static <B,V,T1,T2> StreamCodec<B,V> composite(StreamCodec<? super B,T1> a,Function<V,T1> f,StreamCodec<? super B,T2>b,Function<V,T2>g,BiFunction<T1,T2,V>make){return null;}}''',
'net/minecraft/network/codec/ByteBufCodecs.java':'package net.minecraft.network.codec;import net.minecraft.network.FriendlyByteBuf;public class ByteBufCodecs{public static final StreamCodec<FriendlyByteBuf,Integer> VAR_INT=null;}',
'net/minecraft/network/chat/Component.java':'package net.minecraft.network.chat;public record Component(String key,Object[] args){public static Component translatable(String key,Object...args){return new Component(key,args);}}',
'net/minecraft/world/entity/player/Player.java':'package net.minecraft.world.entity.player;public class Player{}',
'net/minecraft/server/network/ServerGamePacketListenerImpl.java':'package net.minecraft.server.network;public record ServerGamePacketListenerImpl(net.minecraft.network.Connection getConnection){}',
'net/minecraft/server/level/ServerPlayer.java':'package net.minecraft.server.level;import net.minecraft.world.inventory.AbstractContainerMenu;public class ServerPlayer extends net.minecraft.world.entity.player.Player{public AbstractContainerMenu containerMenu;public net.minecraft.server.network.ServerGamePacketListenerImpl connection;}',
'net/minecraft/world/inventory/AbstractContainerMenu.java':'package net.minecraft.world.inventory;public class AbstractContainerMenu{public int containerId;public boolean valid=true;public boolean stillValid(net.minecraft.server.level.ServerPlayer p){return valid;}}',
'com/gonzotech/machines/menu/PlotnickMenu.java':'package com.gonzotech.machines.menu;import java.util.*;public class PlotnickMenu extends net.minecraft.world.inventory.AbstractContainerMenu{public int calls,last;public final List<Integer> buttons=new ArrayList<>();public void handleButtonClick(int button,net.minecraft.server.level.ServerPlayer player){calls++;last=button;buttons.add(button);}}',
'net/neoforged/neoforge/network/handling/IPayloadContext.java':'package net.neoforged.neoforge.network.handling;import java.util.concurrent.CompletableFuture;public interface IPayloadContext{net.minecraft.network.Connection connection();net.minecraft.world.entity.player.Player player();void disconnect(net.minecraft.network.chat.Component c);CompletableFuture<Void> enqueueWork(Runnable work);}',
'net/neoforged/neoforge/network/handling/IPayloadHandler.java':'package net.neoforged.neoforge.network.handling;public interface IPayloadHandler<T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload>{void handle(T payload,IPayloadContext context);}',
'net/neoforged/neoforge/network/handling/DirectionalPayloadHandler.java':'package net.neoforged.neoforge.network.handling;public class DirectionalPayloadHandler{}',
'net/neoforged/neoforge/network/registration/NetworkRegistry.java':'''package net.neoforged.neoforge.network.registration;import java.util.*;import net.minecraft.network.*;import net.minecraft.network.codec.*;import net.minecraft.network.protocol.*;import net.minecraft.network.protocol.common.custom.*;import net.neoforged.neoforge.network.handling.*;public class NetworkRegistry{static final Map<CustomPacketPayload.Type<?>,IPayloadHandler<?>> handlers=new HashMap<>();public static <T extends CustomPacketPayload,B extends FriendlyByteBuf>void register(CustomPacketPayload.Type<T> type,StreamCodec<? super B,T> codec,IPayloadHandler<T> handler,List<ConnectionProtocol> protocols,Optional<PacketFlow> flow,String version,boolean optional){handlers.put(type,handler);}public static <T extends CustomPacketPayload>void receive(T packet,IPayloadContext context){((IPayloadHandler<T>)handlers.get(packet.type())).handle(packet,context);}public static boolean main(CustomPacketPayload.Type<?> type){return handlers.get(type) instanceof MainThreadPayloadHandler;}}''',
'com/gonzotech/radiation/client/RadiationPanel.java':'package com.gonzotech.radiation.client;public class RadiationPanel{public static void accept(Object view){}}',
'net/neoforged/bus/api/SubscribeEvent.java':'package net.neoforged.bus.api;public @interface SubscribeEvent{}',
'net/neoforged/fml/common/EventBusSubscriber.java':'package net.neoforged.fml.common;public @interface EventBusSubscriber{String modid();}',
'net/neoforged/neoforge/event/entity/player/PlayerEvent.java':'package net.neoforged.neoforge.event.entity.player;public class PlayerEvent{public record PlayerLoggedOutEvent(net.minecraft.world.entity.player.Player getEntity){}}',
'net/neoforged/neoforge/event/server/ServerStoppedEvent.java':'package net.neoforged.neoforge.event.server;public class ServerStoppedEvent{}',
'net/neoforged/neoforge/common/ModConfigSpec.java':'''package net.neoforged.neoforge.common;public class ModConfigSpec{public static boolean loaded=true;public boolean isLoaded(){return loaded;}public static class IntValue{public int value;IntValue(int n){value=n;}public int get(){return value;}}public static class BooleanValue{boolean value;BooleanValue(boolean b){value=b;}public boolean get(){return value;}}public static class Builder{public Builder comment(String...args){return this;}public Builder translation(String k){return this;}public BooleanValue define(String k,boolean d){return new BooleanValue(d);}public IntValue defineInRange(String k,int d,int min,int max){if(!k.equals("floodRequestThreshold")||d!=128||min!=1||max!=4096)throw new AssertionError("config contract");return new IntValue(d);}public ModConfigSpec build(){return new ModConfigSpec();}}}''',
}
STUBS.update({'net/neoforged/bus/api/Event.java':'package net.neoforged.bus.api;public class Event{}','net/neoforged/fml/event/IModBusEvent.java':'package net.neoforged.fml.event;public interface IModBusEvent{}','net/neoforged/fml/config/ModConfig.java':'package net.neoforged.fml.config;public record ModConfig(Object getSpec){}','net/neoforged/fml/event/config/ModConfigEvent.java':(FIX/'ModConfigEvent.java.txt').read_text()})
FENCE='''package com.gonzotech.radiation.engine;import java.util.*;import com.gonzotech.GonzoTechMod;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.resources.ResourceLocation;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.server.level.ServerPlayer;import net.neoforged.neoforge.network.registration.PayloadRegistrar;public class RadiationViewNetwork{public static final List<String> events=new ArrayList<>();public record View()implements CustomPacketPayload{public static final Type<View> TYPE=new Type<>(new ResourceLocation("gonzotech","view"));public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=null;public Type<View>type(){return TYPE;}}public record Fence(int menu,long sequence,boolean begin,int cursorCount,int cursorFingerprint)implements CustomPacketPayload{public static final Type<Fence>TYPE=new Type<>(new ResourceLocation("gonzotech","fence"));public static final StreamCodec<RegistryFriendlyByteBuf,Fence> CODEC=null;public Type<Fence>type(){return TYPE;}}private static void handleFence(ServerPlayer p,Fence f){events.add(f.begin()?"begin":"end");}'''
PROBE=r'''
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import com.gonzotech.core.network.*;import com.gonzotech.core.config.*;import com.gonzotech.machines.menu.*;import com.gonzotech.radiation.engine.*;import net.minecraft.network.*;import net.minecraft.network.chat.*;import net.minecraft.server.level.*;import net.neoforged.neoforge.network.handling.*;import net.neoforged.neoforge.network.registration.*;
public class IngressProbe{
static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}static void eq(long a,long b,String s){ok(a==b,s+": "+a+" != "+b);}
static class Context implements IPayloadContext{
 final Connection conn=new Connection();final ServerPlayer player=new ServerPlayer();final PlotnickMenu menu=new PlotnickMenu();final Queue<Runnable> main=new ConcurrentLinkedQueue<>();int kicks,playerReads,enqueued;Component reason;boolean network=true,throwEnqueue;
 Context(){menu.containerId=4;player.containerMenu=menu;player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(conn);}
 public Connection connection(){return conn;}public net.minecraft.world.entity.player.Player player(){if(network)throw new AssertionError("off-thread player/menu access");playerReads++;return player;}
 public void disconnect(Component c){kicks++;reason=c;conn.connected=false;}
 public CompletableFuture<Void> enqueueWork(Runnable work){if(throwEnqueue)throw new IllegalStateException("executor closed");enqueued++;CompletableFuture<Void> future=new CompletableFuture<>();main.add(()->{try{work.run();future.complete(null);}catch(Throwable t){future.completeExceptionally(t);}});return future;}
 void drain(){network=false;while(!main.isEmpty())main.remove().run();network=true;}
}
static PayloadFloodWindow bucket(Context c)throws Exception{var f=PayloadFloodGuard.class.getDeclaredField("WINDOWS");f.setAccessible(true);return (PayloadFloodWindow)((Map<?,?>)f.get(null)).get(c.conn);}
static void limit(int n){GonzoServerConfig.FLOOD_REQUEST_THRESHOLD.value=n;PayloadFloodGuard.onConfigReloading(new net.neoforged.fml.event.config.ModConfigEvent.Reloading(new net.neoforged.fml.config.ModConfig(GonzoServerConfig.SPEC)));}
static void button(Context c,int menu,int id){NetworkRegistry.receive(new PlotnickButtonPayload(menu,id),c);}
static void fence(Context c,long seq,boolean begin){NetworkRegistry.receive(new RadiationViewNetwork.Fence(4,seq,begin,0,0),c);}
public static void main(String[]args)throws Exception{
PayloadFloodWindow w=new PayloadFloodWindow();for(int i=0;i<128;i++){ok(w.acquire(0,128)==PayloadFloodWindow.Admission.ACCEPTED,"default burst accepted");w.release();}ok(w.acquire(999999999,128)==PayloadFloodWindow.Admission.EXCEEDED,"exact threshold+1 in sliding second disconnects");ok(w.acquire(2000000000L,128)==PayloadFloodWindow.Admission.CLOSED,"closed connection cannot recover/requeue after time window");
w=new PayloadFloodWindow();ok(w.acquire(0,1)==PayloadFloodWindow.Admission.ACCEPTED,"one-slot admission");w.release();ok(w.acquire(1000000000L,1)==PayloadFloodWindow.Admission.ACCEPTED,"exact one-second expiration");w.release();
w=new PayloadFloodWindow();ok(w.acquire(0,1)==PayloadFloodWindow.Admission.ACCEPTED,"backlog admitted");ok(w.acquire(10000000000L,1)==PayloadFloodWindow.Admission.EXCEEDED,"outstanding remains bounded despite stalled main thread and elapsed windows");w.release();eq(0,w.outstanding(),"release after disconnect balances");
// True sliding window, not a fixed boundary that permits double bursts.
w=new PayloadFloodWindow();ok(w.acquire(0,2)==PayloadFloodWindow.Admission.ACCEPTED,"sliding first");w.release();ok(w.acquire(900000000,2)==PayloadFloodWindow.Admission.ACCEPTED,"sliding second");w.release();ok(w.acquire(1000000000,2)==PayloadFloodWindow.Admission.ACCEPTED,"expired first only");w.release();ok(w.acquire(1000000001,2)==PayloadFloodWindow.Admission.EXCEEDED,"remaining previous-window event counts");
// Reloading threshold preserves timestamps and outstanding debt, including shrink/grow.
w=new PayloadFloodWindow();w.acquire(0,2);w.release();ok(w.acquire(1,4)==PayloadFloodWindow.Admission.ACCEPTED,"grow window");w.release();ok(w.acquire(2,1)==PayloadFloodWindow.Admission.EXCEEDED,"shrinking cannot erase accepted history");
for(int threshold:new int[]{1,2,128,4096}){w=new PayloadFloodWindow();for(int i=0;i<threshold;i++)ok(w.acquire(i,threshold)==PayloadFloodWindow.Admission.ACCEPTED,"all validated thresholds");eq(threshold,w.outstanding(),"exact backlog capacity");ok(w.acquire(100000000000L,threshold)==PayloadFloodWindow.Admission.EXCEEDED,"backlog survives time at each threshold");for(int i=0;i<threshold;i++)w.release();eq(0,w.outstanding(),"all credits released");}
// Many concurrent producers must never race beyond the common bound or disconnect repeatedly.
final PayloadFloodWindow concurrent=new PayloadFloodWindow();AtomicInteger accepted=new AtomicInteger(),exceeded=new AtomicInteger();List<Thread> threads=new ArrayList<>();for(int i=0;i<16;i++)threads.add(new Thread(()->{for(int n=0;n<1000;n++){var a=concurrent.acquire(0,128);if(a==PayloadFloodWindow.Admission.ACCEPTED)accepted.incrementAndGet();if(a==PayloadFloodWindow.Admission.EXCEEDED)exceeded.incrementAndGet();}}));for(Thread t:threads)t.start();for(Thread t:threads)t.join();eq(128,accepted.get(),"concurrent hard admission bound");eq(1,exceeded.get(),"exactly one disconnect transition");
PayloadRegistrar registrar=new PayloadRegistrar("1");Wiring.register(registrar);RadiationViewNetwork.register(registrar);
ok(!NetworkRegistry.main(PlotnickButtonPayload.TYPE),"Plotnick ingress must run NETWORK");ok(!NetworkRegistry.main(RadiationViewNetwork.Fence.TYPE),"Fence ingress must run NETWORK");ok(NetworkRegistry.main(RadiationViewNetwork.View.TYPE),"S2C remains MAIN: executesOn cloned registrar");
Context c=new Context();for(int i=0;i<128;i++)button(c,i%2==0?4:-i,i);eq(128,c.main.size(),"accepted before main tasks bounded");eq(0,c.playerReads,"network ingress never reads mutable player/menu");eq(0,c.menu.calls,"no actual BE dispatch on network thread");fence(c,1,true);eq(1,c.kicks,"combined channels share ONE threshold");eq(128,c.main.size(),"overflow never enters main queue");eq(128,((Number)c.reason.args()[0]).intValue(),"reason includes configured threshold");ok(c.reason.key().equals("disconnect.gonzotech.payload_flood"),"understandable translated kick reason");for(int i=0;i<100000;i++)button(c,i,i);eq(1,c.kicks,"post-disconnect flood not a kick/enqueue storm");eq(128,c.enqueued,"unique invalid values never create fresh buckets");c.drain();eq(0,c.menu.calls,"disconnect discards accepted flood backlog before world access");eq(0,c.playerReads,"closed backlog stays out of player/menu state");
// Normal accepted Fence keeps FIFO relative to vanilla mutation, no tick buffering or end throttle.
limit(3);c=new Context();RadiationViewNetwork.events.clear();fence(c,10,true);c.enqueueWork(()->RadiationViewNetwork.events.add("vanilla mutation"));fence(c,10,false);button(c,4,7);eq(0,c.kicks,"valid end below shared threshold survives");c.drain();ok(RadiationViewNetwork.events.equals(List.of("begin","vanilla mutation","end")),"begin / vanilla / end FIFO");eq(1,c.menu.calls,"valid button passes current menu validation");eq(7,c.menu.last,"unchanged button value");
Context other=new Context();button(other,4,1);other.drain();eq(0,other.kicks,"other connections independent");eq(1,other.menu.calls,"other player progresses");
limit(1);c=new Context();button(c,4,0);c.drain();button(c,4,1);eq(1,c.kicks,"processing queue doesn't reset rate history");
c=new Context();button(c,999,0);c.drain();eq(0,c.menu.calls,"invalid menu still rejected on main");button(c,4,1);eq(1,c.kicks,"invalid menu still consumes flood quota");
// Logout cancels backlog and keeps a closed tombstone; another socket is unaffected.
limit(128);c=new Context();button(c,4,1);PayloadFloodGuard.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(c.player));c.drain();eq(0,c.menu.calls,"logout prevents deferred world work");button(c,4,1);eq(1,c.enqueued,"logout cannot resurrect bucket on old connection");
c=new Context();button(c,4,1);PayloadFloodGuard.stop(new net.neoforged.neoforge.event.server.ServerStoppedEvent());c.drain();eq(0,c.menu.calls,"server stop closes even detached queued callbacks");Context fresh=new Context();button(fresh,4,1);fresh.drain();eq(1,fresh.menu.calls,"new connection after stop starts fresh");
// Executor rejection/handler failure release exactly once, no permanent outstanding leak.
limit(1);c=new Context();c.throwEnqueue=true;try{button(c,4,0);throw new AssertionError("expected rejection");}catch(IllegalStateException expected){}c.throwEnqueue=false;eq(0,bucket(c).outstanding(),"executor rejection releases credit exactly once"); // rate quota is intentionally not refunded
limit(3);c=new Context();Context faulty=c;PayloadFloodGuard.enqueue(c,()->{throw new IllegalStateException("modeled handler failure");});c.drain();eq(0,bucket(c).outstanding(),"handler failure releases outstanding credit");
// Config event publication ignores unrelated specs, reloads live, unload resets only our spec.
limit(1);PayloadFloodGuard.onConfigReloading(new net.neoforged.fml.event.config.ModConfigEvent.Reloading(new net.neoforged.fml.config.ModConfig(new Object())));c=new Context();button(c,4,1);button(c,4,2);eq(1,c.kicks,"unrelated config event cannot overwrite threshold");
GonzoServerConfig.FLOOD_REQUEST_THRESHOLD.value=2;PayloadFloodGuard.onConfigLoading(new net.neoforged.fml.event.config.ModConfigEvent.Loading(new net.neoforged.fml.config.ModConfig(GonzoServerConfig.SPEC)));c=new Context();button(c,4,1);button(c,4,2);eq(0,c.kicks,"loading publishes configured primitive");button(c,4,3);eq(1,c.kicks,"loaded threshold applies before enqueue");
PayloadFloodGuard.onConfigUnloading(new net.neoforged.fml.event.config.ModConfigEvent.Unloading(new net.neoforged.fml.config.ModConfig(GonzoServerConfig.SPEC)));c=new Context();for(int i=0;i<128;i++)button(c,4,i);eq(0,c.kicks,"unloaded-config fallback is author default128");button(c,4,128);eq(1,c.kicks,"fallback still protects");
System.out.println("Payload ingress PASS: "+checks+" assertions; production admission and pinned registrar/wrapper; modeled queue/menu/network, not native");}}
'''

def files(guard=None,window=None,button=None,registration=None,fence_registration=None):
    out=dict(STUBS)
    for name,text in [('core/network/PayloadFloodGuard',guard),('core/network/PayloadFloodWindow',window),('machines/menu/PlotnickButtonPayload',button),('core/config/GonzoServerConfig',None)]:out['com/gonzotech/'+name+'.java']=text or (JAVA/(name+'.java')).read_text()
    for name in ['PayloadRegistrar','HandlerThread','MainThreadPayloadHandler']:
        package='handling' if name=='MainThreadPayloadHandler' else 'registration';out[f'net/neoforged/neoforge/network/{package}/{name}.java']=(FIX/(name+'.java.txt')).read_text()
    source=(JAVA/'GonzoTechMod.java').read_text();a=source.index('        registrar.executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK).playToServer(');b=source.index('::handle);',a)+len('::handle);');wiring=registration or source[a:b]
    out['Wiring.java']='import net.neoforged.neoforge.network.registration.PayloadRegistrar;public class Wiring{public static void register(PayloadRegistrar registrar){'+wiring+'}}'
    source=(JAVA/'radiation/engine/RadiationViewNetwork.java').read_text();out['com/gonzotech/radiation/engine/RadiationViewNetwork.java']=FENCE+(fence_registration or method(source,'public static void register('))+'}'
    out['IngressProbe.java']=PROBE
    return out

def run(**kwargs):
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
    with tempfile.TemporaryDirectory(prefix='payload-ingress-') as tmp:
        root=Path(tmp);paths=[]
        for name,body in files(**kwargs).items():p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
        c=subprocess.run([java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-nowarn','-d',str(root/'classes')]+paths,text=True,capture_output=True);assert c.returncode==0,'Compiler failure NOT a mutation kill:\n'+c.stdout+c.stderr
        return subprocess.run([java,'-cp',str(root/'classes'),'IngressProbe'],text=True,capture_output=True)
if __name__=='__main__':
    for record in json.loads((FIX/'manifest.json').read_text())['files']:
        data=(FIX/record['file']).read_bytes();assert len(data)==record['bytes'] and hashlib.sha256(data).hexdigest()==record['sha256']
    mod=(JAVA/'GonzoTechMod.java').read_text()
    for hook in ['onConfigLoading','onConfigReloading','onConfigUnloading']:assert 'modEventBus.addListener(com.gonzotech.core.network.PayloadFloodGuard::'+hook+');' in mod
    r=run();assert r.returncode==0,r.stdout+r.stderr;print(r.stdout.strip())
    for record in json.loads((ROOT/'audit/fixtures/work_329/manifest.json').read_text())['files'][1:]:
        data=(ROOT/'audit/fixtures/work_329'/record['file']).read_bytes();assert len(data)==record['bytes'] and hashlib.sha256(data).hexdigest()==record['sha256'];assert data==subprocess.check_output(['git','show',record['source_commit']+':'+record['source']],cwd=ROOT)
        old=data.decode()
        if record['file'].startswith('PlotnickButton'):kwargs={'button':old}
        elif record['file'].startswith('RadiationView'):kwargs={'fence_registration':method(old,'public static void register(')}
        else:
            a=old.index('        registrar.playToServer(\n            com.gonzotech.machines.menu.PlotnickButtonPayload.TYPE');b=old.index('::handle);',a)+len('::handle);');kwargs={'registration':old[a:b]}
        r=run(**kwargs);assert r.returncode!=0 and 'AssertionError' in r.stderr,'exact329 '+record['file']+' must compile then assertion-fail';print('REJECTED: exact329 '+record['file']+' (Git/hash/bytes)')
    if '--mutation-check' in sys.argv:
        window=(JAVA/'core/network/PayloadFloodWindow.java').read_text();guard=(JAVA/'core/network/PayloadFloodGuard.java').read_text()
        cases=[('no-outstanding-cap',{'window':window.replace(' || outstanding >= threshold','')}),('off-by-one-quota',{'window':window.replace('count >= threshold','count > threshold')}),('closed-reopens',{'window':window.replace('if (closed) return Admission.CLOSED;','closed = false;')}),('fixed-window-erases-history',{'window':window.replace('count--;','count = 0;')}),('no-closed-backlog-check',{'guard':guard.replace('if (!window.isClosed()) work.run();','work.run();')}),('no-logout-close',{'guard':guard.replace('if (window != null) window.close();','')}),('stop-forgets-not-closes',{'guard':guard.replace('WINDOWS.values().forEach(PayloadFloodWindow::close);','')})]
        for label,kwargs in cases:
            r=run(**kwargs);assert r.returncode!=0 and 'AssertionError' in r.stderr,label+' survived\n'+r.stdout+r.stderr;print('REJECTED:',label)
