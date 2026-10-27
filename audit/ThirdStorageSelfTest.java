import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.energy.TickEnergyStorage;
import java.util.Random;

/** Executes the REAL finite store, not a Python reimplementation. No Minecraft doubles. */
public final class ThirdStorageSelfTest {
    static long checks;
    static void eq(long expected,long actual,String reason) {
        checks++; if(expected!=actual) throw new AssertionError(reason+": expected "+expected+", got "+actual);
    }
    static void yes(boolean value,String reason) { eq(1,value?1:0,reason); }
    static TickEnergyStorage store(ThirdStorageDefs.Spec spec,long amount) {
        var store=new TickEnergyStorage(spec);store.restore(amount);return store;
    }
    static void exactConstants() {
        var a=ThirdStorageDefs.ACCUMULATOR;var b=ThirdStorageDefs.BATTERY;
        eq(230882000,a.capacity(),"III capacity");eq(294000,a.intake(),"III input");eq(294000,a.output(),"III output");eq(32,a.idleLoss(),"III loss");
        eq(68088000,b.capacity(),"battery capacity");eq(244000,b.intake(),"battery input");eq(188000,b.output(),"battery output");eq(0,b.idleLoss(),"battery loss");
        eq(3,ThirdStorageDefs.ITEM_SLOTS,"ordinary slots");eq(3,ThirdStorageDefs.SYNC_PARTS,"sync limbs");
    }
    static void budgets(ThirdStorageDefs.Spec spec) {
        var s=store(spec,0);
        eq(0,s.receive(-1,false,0),"negative receive");eq(0,s.extract(-1,false,0),"negative extract");
        eq(spec.intake(),s.receive(Long.MAX_VALUE,true,0),"simulate unlimited request");eq(0,s.stored(),"simulation does not fill");
        eq(spec.intake()-17,s.receive(spec.intake()-17,false,0),"source one");
        for(int i=0;i<100;i++) eq(17,s.receive(999,true,0),"simulation does not spend remaining intake");
        eq(17,s.receive(999,false,0),"source two remainder");eq(0,s.receive(999,false,0),"aggregate intake exhausted");
        eq(spec.intake(),s.stored(),"conserved intake");
        // A probe for another tick must not reset this tick's real budget.
        eq(spec.intake(),s.receive(Long.MAX_VALUE,true,1),"future-tick receive probe");
        eq(0,s.receive(1,false,0),"future simulation cannot reset budget");
        s.restore(spec.capacity());
        eq(spec.output()-11,s.extract(spec.output()-11,false,0),"output path one");
        for(int i=0;i<100;i++) eq(11,s.extract(999,true,0),"output simulation readonly");
        eq(11,s.extract(999,false,0),"output path two");eq(0,s.extract(1,false,0),"aggregate output exhausted");
        eq(spec.output(),s.extract(Long.MAX_VALUE,true,1),"future-tick output probe");eq(0,s.extract(1,false,0),"future probe cannot reset output");
        eq(spec.output(),s.extract(Long.MAX_VALUE,false,1),"next tick output resets");
        eq(spec.intake(),s.receive(Long.MAX_VALUE,false,1),"input resets independently");
        s=store(spec,spec.capacity()-1);
        eq(1,s.receive(Long.MAX_VALUE,false,1),"capacity clipping");eq(0,s.receive(1,false,1),"full rejects");
        s=store(spec,1);eq(1,s.extract(Long.MAX_VALUE,false,2),"empty clipping");eq(0,s.extract(1,false,2),"empty rejects");
        s.restore(Long.MIN_VALUE);eq(0,s.stored(),"negative NBT clamps");s.restore(Long.MAX_VALUE);eq(spec.capacity(),s.stored(),"oversize NBT clamps");
    }
    static void lossAndTransfer() {
        var a=store(ThirdStorageDefs.ACCUMULATOR,1001);eq(32,a.idle(10),"exact 0.032 loss");eq(969,a.stored(),"fraction retained");
        eq(0,a.idle(10),"not twice same tick");eq(32,a.idle(11),"next tick loss");a.restore(1);eq(1,a.idle(12),"loss cannot overdraw");eq(0,a.stored(),"no debt");
        var battery=store(ThirdStorageDefs.BATTERY,68087999);
        for(int tick=0;tick<10000;tick++) eq(0,battery.idle(tick),"zero battery loss");eq(68087999,battery.stored(),"battery stable at milli precision");
        // Multiple destinations + receive/output in one tick: independent quotas, no creation.
        var source=store(ThirdStorageDefs.ACCUMULATOR,1000000);var target=store(ThirdStorageDefs.BATTERY,0);
        for(int tick=0;tick<1000;tick++) {
            long before=source.stored()+target.stored(),paid=0,taken=0,loss=source.idle(tick)+target.idle(tick);
            for(int lane=0;lane<6;lane++) {
                long offer=source.extract(50000,true,tick),accept=target.receive(offer,true,tick);
                eq(accept,target.receive(accept,false,tick),"execute simulated intake");eq(accept,source.extract(accept,false,tick),"execute simulated output");paid+=accept;
                long out=target.extract(40000,false,tick);taken+=out;
            }
            yes(paid<=source.spec().output() && paid<=target.spec().intake(),"aggregate parallel transfer cap");
            yes(taken<=target.spec().output(),"aggregate parallel consumption cap");
            eq(before-loss-taken,source.stored()+target.stored(),"energy conservation");
        }
    }
    static void sync() {
        for(var spec:new ThirdStorageDefs.Spec[]{ThirdStorageDefs.ACCUMULATOR,ThirdStorageDefs.BATTERY}) {
            var s=store(spec,0);var random=new Random(316);
            long[] boundaries={0,1,31,32,999,1000,32767,32768,999999,1000000,32768000,48900000,68088000,spec.capacity()-1,spec.capacity()};
            for(long value:boundaries) {s.restore(value);roundTrip(s);}
            for(int i=0;i<50000;i++) {s.restore(random.nextLong(spec.capacity()+1));roundTrip(s);}
            // Independent packet limbs: emulate the menu's SimpleContainerData, NOT a clamped partial buffer.
            int[] parts={0,882,230};parts[0]=999;parts[1]=999;parts[2]=229;
            eq(229999999,TickEnergyStorage.decode(parts[0],parts[1],parts[2]),"full-to-lower carry across packets");
        }
    }
    static void roundTrip(TickEnergyStorage s) {
        int[] parts={s.syncPart(0),s.syncPart(1),s.syncPart(2)};
        for(int part:parts) {yes(part>=0 && part<=999,"small limb");eq(part,(short)part,"signed-short safety");}
        eq(s.stored(),TickEnergyStorage.decode((short)parts[0],(short)parts[1],(short)parts[2]),"exact milli sync");
        var placed=store(s.spec(),s.stored());eq(s.stored(),placed.stored(),"exact persisted charge restore");
    }
    static void randomModel(ThirdStorageDefs.Spec spec) {
        var s=store(spec,0);var random=new Random(spec.capacity());long stored=0,input=0,output=0;
        for(int tick=0;tick<5000;tick++) {
            input=0;output=0;
            for(int call=0;call<30;call++) {
                boolean receive=random.nextBoolean(),sim=random.nextInt(3)==0;
                long request=switch(random.nextInt(15)) {case 0->Long.MAX_VALUE;case 1->-1;default->random.nextInt(400000);};
                long expected=request<=0?0:Math.min(request,receive?Math.min(spec.capacity()-stored,spec.intake()-input):Math.min(stored,spec.output()-output));
                eq(expected,receive?s.receive(request,sim,tick):s.extract(request,sim,tick),"random budget reference");
                if(!sim) {if(receive){stored+=expected;input+=expected;}else{stored-=expected;output+=expected;}}
                eq(stored,s.stored(),"random content reference");
            }
            long loss=Math.min(stored,spec.idleLoss());eq(loss,s.idle(tick),"random exact loss");stored-=loss;
            eq(0,s.idle(tick),"random once-only loss");eq(stored,s.stored(),"random after loss");roundTrip(s);
        }
    }
    public static void main(String[] args) {
        exactConstants();budgets(ThirdStorageDefs.ACCUMULATOR);budgets(ThirdStorageDefs.BATTERY);
        lossAndTransfer();sync();randomModel(ThirdStorageDefs.ACCUMULATOR);randomModel(ThirdStorageDefs.BATTERY);
        System.out.println("ThirdStorageSelfTest PASS: "+checks+" assertions (real pure core; not Minecraft)");
    }
}
