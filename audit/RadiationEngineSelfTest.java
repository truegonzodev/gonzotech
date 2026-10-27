import com.gonzotech.radiation.*;
import com.gonzotech.radiation.engine.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.core.component.DataComponents;
import java.util.*;

/** Production-core regressions. Boundary doubles/limitations: radiation_engine_test.py. */
public final class RadiationEngineSelfTest {
    static final double M=1_000_000, Z=1_000_000_000;
    static final Item CLEAN=new Item("test_material",0,1), RADIUM=new Item("radium",75*M,1);
    static int groups=0, checks=0;
    static void require(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    static void eq(double a,double b,String why){
        require(Double.isFinite(a) && Math.abs(a-b)<=Math.max(1e-7,Math.abs(b)*1e-12),why+": "+a+" != "+b);
    }
    static void group(Runnable test){ExternalRadiation.clear();test.run();groups++;}
    static ItemStack stack(Item item,int count,double rate){
        ItemStack s=new ItemStack(item,count);ItemRadioactivity.setInduced(s,rate);return s;
    }
    static double rate(ItemStack s){return ItemRadioactivity.getInduced(s);}
    static ItemStack box(List<ItemStack> contents){
        ItemStack s=stack(CLEAN,1,0);s.set(DataComponents.CONTAINER,new ItemContainerContents(List.copyOf(contents)));return s;
    }
    static double loss(double value){return value*(1-0.25*Math.tanh(value/(33*M)));}
    // Independent transcription of the APPROVED 314 law, not parsed constants.
    static double expected(double current,double source,double intrinsic,double factor){
        double target=Math.min(Math.max(0,source-intrinsic),5*Z);
        if(target<M || factor<=0 || current>target)return loss(current);
        double seed=Math.max(current,M);
        return loss(Math.min(seed+seed*0.25*(1-seed/target)*factor*Math.pow(0.9987,seed/M),target));
    }
    static void coreLaw(){
        ItemStack s=stack(CLEAN,64,0);
        for(double current:new double[]{0,0.5*M,M,20*M,33*M,200*M,5*Z})
            for(double source:new double[]{0,0.99*M,M,15*M,75*M,5*Z,10*Z})
                for(double factor:new double[]{0,0.2,1}){
                    double got=ItemRadioactivity.nextInduced(current,s,source>0,source,factor);
                    eq(got,expected(current,source,0,factor),"approved law, nZt/s scaling");
                    eq(rate(s),0,"nextInduced is read-only");
                }
        ItemStack natural=stack(RADIUM,64,0);
        eq(ItemRadioactivity.nextInduced(0,natural,true,75*M,1),0,"subtract per-item intrinsic");
        eq(ItemRadioactivity.nextInduced(0,natural,true,100*M,1),expected(0,100*M,75*M,1),"intrinsic not multiplied by count");
        double a=ItemRadioactivity.nextInduced(5*Z,s,false,0,1);eq(a,3.75*Z,"cooling second 1");
        a=ItemRadioactivity.nextInduced(a,s,false,0,1);eq(a,2.8125*Z,"cooling second 2");
        a=ItemRadioactivity.nextInduced(a,s,false,0,1);eq(a,2.109375*Z,"cooling second 3");
        ItemRadioactivity.setInduced(s,M-1);eq(rate(s),0,"cleanup below 1 mZt");
        ItemRadioactivity.setInduced(s,M);eq(rate(s),M,"cleanup threshold inclusive");
        require(ItemRadioactivity.restDecay(5*Z,100000)<M,"bounded long rest reaches cleanup");
    }
    static void transfers(){
        eq(DoseMath.merge(2*M,8,6*M,8),4*M,"weighted per-item merge");
        eq(DoseMath.merge(2*M,Integer.MAX_VALUE,6*M,Integer.MAX_VALUE),4*M,"count sum does not overflow int");
        for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            eq(DoseMath.finite(invalid),0,"nonfinite/negative rate rejected");
        eq(DoseMath.merge(M,1,0,99),M/100,"merge must not apply item cleanup floor");
        ItemStack a=stack(CLEAN,8,2*M),b=stack(CLEAN,8,6*M);
        ExternalRadiation.tick(a,20);ExternalRadiation.tick(b,40);
        ExternalRadiation.merge(a,b,8);a.grow(8);b.shrink(8);
        eq(rate(a),4*M,"merge before destination growth");eq(rate(a)*a.getCount(),64*M,"material-weighted rate conserved");
        eq(ExternalRadiation.read(a).tick(),40,"merge keeps latest timestamp");
        ItemStack copy=a.copy();copy.shrink(12);a.shrink(4);
        eq(rate(copy),4*M,"copy/split per-item rate inherited");
        eq(rate(a)*a.getCount()+rate(copy)*copy.getCount(),64*M,"split does not create material-weighted rate");
        ExternalRadiation.dilute(a,4);a.grow(4);eq(rate(a),3*M,"clean manufactured output dilutes");
        eq(rate(copy),4*M,"copies have independent external state");
        require(ExternalRadiation.read(ItemStack.EMPTY).rate()==0,"EMPTY never becomes radioactive");
    }
    static void sourceSelection(){
        for(int count:new int[]{1,64})for(int n:new int[]{1,30}){
            List<ItemStack> roots=new ArrayList<>();
            for(int i=0;i<n;i++)roots.add(stack(RADIUM,count,0));
            ItemStack target=stack(CLEAN,count,0);roots.add(target);
            RadiationCluster.step(roots,0,RadiationCluster.Policy.NORMAL,20,null);
            eq(rate(target),expected(0,75*M,0,1),"source count/number does not strengthen per-item source");
            for(int i=0;i<n;i++)eq(rate(roots.get(i)),0,"equal natural presets never self-induce");
        }
        ItemStack lone=stack(RADIUM,64,0);
        RadiationCluster.step(List.of(lone,lone),0,RadiationCluster.Policy.NORMAL,20,null);
        eq(rate(lone),0,"self/alias excluded");eq(RadiationCluster.inspect(List.of(lone,lone)).count(),64,"alias counted once");
        // Select by FULL strength, transfer induced at 75%; full-strength tie prefers higher transfer.
        ItemStack preset=stack(new Item("preset65",65*M,1),1,0),induced=stack(CLEAN,64,75*M),target=stack(CLEAN,1,0);
        List<RadiationCluster.Step> trace=new ArrayList<>();
        RadiationCluster.step(List.of(preset,induced,target),0,RadiationCluster.Policy.NORMAL,20,trace::add);
        eq(trace.get(2).otherSource(),56.25*M,"strongest full item wins, not strongest transfer or stack total");
        ItemStack equalPreset=stack(RADIUM,1,0),equalInduced=stack(CLEAN,64,75*M),t=stack(CLEAN,1,0);
        trace.clear();RadiationCluster.step(List.of(equalInduced,equalPreset,t),0,RadiationCluster.Policy.NORMAL,20,trace::add);
        eq(trace.get(2).otherSource(),75*M,"equal full strength tie uses greater transfer");
    }
    static double[] ordered(boolean reverse){
        ItemStack a=stack(CLEAN,64,20*M),b=stack(CLEAN,1,10*M),c=stack(CLEAN,32,0);
        RadiationCluster.step(reverse?List.of(c,b,a):List.of(a,b,c),0,RadiationCluster.Policy.NORMAL,20,null);
        return new double[]{rate(a),rate(b),rate(c)};
    }
    static void snapshotAndTrace(){
        double[] a=ordered(false),b=ordered(true);
        for(int i=0;i<a.length;i++)eq(a[i],b[i],"immutable snapshot independent of update order");
        ItemStack source=stack(CLEAN,64,20*M),target=stack(CLEAN,1,0);
        List<RadiationCluster.Step> trace=new ArrayList<>();
        RadiationCluster.Summary result=RadiationCluster.step(List.of(source,target),0,RadiationCluster.Policy.NORMAL,20,trace::add);
        eq(trace.get(1).otherSource(),15*M,"source snapshot captured before cooling source");
        eq(trace.get(0).before(),20*M,"trace before is real per-item rate");
        eq(trace.get(0).after(),rate(source),"trace after is committed external rate");
        eq(trace.get(1).target(),15*M,"trace target corresponds to selected source");
        require(result.changed(),"changed summary reported");
        eq(result.total(),rate(source)*64+rate(target),"post-step total includes induced exactly once");
    }
    static void nested(){
        ItemStack natural=stack(RADIUM,1,0),inside=stack(CLEAN,64,0),outside=stack(CLEAN,1,0);
        ItemStack wrapper=box(List.of(natural,ItemStack.EMPTY,inside));
        var component=wrapper.get(DataComponents.CONTAINER);
        RadiationCluster.Summary summary=RadiationCluster.step(List.of(wrapper,outside),0,RadiationCluster.Policy.NORMAL,20,null);
        eq(rate(wrapper),0,"wrapper excludes its OWN complete subtree");
        eq(rate(inside),expected(0,75*M,0,1),"nested siblings actively irradiate");
        eq(rate(outside),rate(inside),"nested source reaches other physical slots");
        require(wrapper.get(DataComponents.CONTAINER)==component,"pass never rewrites live contents component");
        eq(summary.count(),67,"wrapper, content and outside counted once");
        eq(summary.slots(),4,"empty nested slot is not a radioactive node");
        ItemStack dirty=stack(CLEAN,1,20*M);ExternalRadiation.tick(dirty,0);
        RadiationCluster.step(List.of(box(List.of(dirty))),0,RadiationCluster.Policy.NORMAL,20,null);
        eq(rate(dirty),loss(20*M),"nested slot receives ONE decay, not deep + single decay");
        ItemStack active=stack(CLEAN,1,20*M);ExternalRadiation.tick(active,0);
        RadiationCluster.step(List.of(box(List.of(stack(RADIUM,1,0),active))),0,RadiationCluster.Policy.NORMAL,20,null);
        eq(rate(active),expected(20*M,75*M,0,1),"deep passive tick must not consume nested active source tick");
        ItemStack deep=stack(RADIUM,1,0);
        for(int i=0;i<5;i++)deep=box(List.of(deep));
        eq(RadiationCluster.inspect(List.of(deep)).slots(),5,"depth 5 contents are not traversed");
        ItemStack cargo=stack(RADIUM,1,0),carrier=stack(new TestCarrierItem(),1,0);carrier.cargo=cargo;
        for(int i=0;i<4;i++)carrier=box(List.of(carrier));
        eq(RadiationCluster.inspect(List.of(carrier)).preset(),45*M,"cargo filter still applies at depth 4");
    }
    static void shieldAndChunk(){
        for(double chunk:new double[]{0,30*M-1,30*M}){
            ItemStack s=stack(CLEAN,1,0);RadiationCluster.step(List.of(s),chunk,RadiationCluster.Policy.NORMAL,20,null);
            eq(rate(s),0,"chunk input is STRICTLY above 30 mZt/s");
        }
        for(var policy:List.of(RadiationCluster.Policy.NORMAL,RadiationCluster.Policy.LEAD)){
            ItemStack s=stack(CLEAN,1,0);RadiationCluster.step(List.of(s),30*M+1,policy,20,null);
            eq(rate(s),expected(0,30*M+1,0,1),"incoming chunk is not attenuated by lead");
        }
        ItemStack source=stack(RADIUM,64,0),target=stack(CLEAN,64,0);
        RadiationCluster.step(List.of(source,target),0,RadiationCluster.Policy.LEAD,20,null);
        eq(rate(target),expected(0,15*M,0,1),"lead internal per-item transfer x0.2");
        ItemStack carrier=stack(new TestCarrierItem(),1,0),outside=stack(CLEAN,1,0);
        carrier.cargo=stack(CLEAN,10,0);ExternalRadiation.writeContents(carrier,new ExternalRadiation.Dose(20*M,0));
        List<RadiationCluster.Step> trace=new ArrayList<>();
        RadiationCluster.step(List.of(carrier,outside),0,RadiationCluster.Policy.NORMAL,20,trace::add);
        var out=trace.stream().filter(s->s.stack()==outside).findFirst().orElseThrow();
        eq(out.otherSource(),20*M*0.6*0.75,"cargo screen and induced re-emission are separate");
        require(ExternalRadiation.readContents(carrier).rate()<20*M,"cargo sidecar updated after its single tick");
    }
    static void chunkTarget(){
        ItemStack natural=stack(RADIUM,64,0),dirty=stack(CLEAN,64,10*M);
        var summary=RadiationCluster.inspect(List.of(natural,dirty,dirty));
        eq(summary.preset(),4800*M,"natural part retains count");
        eq(summary.induced(),640*M,"full dose part retains count");
        eq(summary.total(),5440*M,"full dose sums both");
        eq(summary.chunkTarget(),4810*M,"chunk target has only strongest SINGLE induced rate");
        List<ItemStack> roots=new ArrayList<>(List.of(natural,dirty));
        for(int i=0;i<40;i++)roots.add(stack(CLEAN,64,10*M));
        eq(RadiationCluster.inspect(roots).chunkTarget(),4810*M,"additional induced slots cannot strengthen chunk target");
    }
    static void catchup(){
        ItemStack dirty=stack(CLEAN,1,20*M);ExternalRadiation.tick(dirty,0);
        double aged=20*M;for(int i=0;i<4;i++)aged=loss(aged);
        eq(ItemRadioactivity.inducedBeforeSourceStep(dirty,100),aged,"missed intervals are PASSIVE, not active heating");
        eq(rate(dirty),20*M,"source snapshot preparation is read-only");
        eq(ExternalRadiation.read(dirty).tick(),0,"snapshot does not claim the current tick");
        List<RadiationCluster.Step> trace=new ArrayList<>();ItemStack target=stack(CLEAN,1,0);
        RadiationCluster.step(List.of(dirty,target),0,RadiationCluster.Policy.NORMAL,100,trace::add);
        eq(trace.get(1).otherSource(),aged*0.75,"source strength uses passive catchup before snapshot");
        eq(rate(dirty),loss(aged),"one current passive tick after four missed ticks");
        double before=rate(dirty);RadiationCluster.step(List.of(dirty),0,RadiationCluster.Policy.NORMAL,100,null);
        eq(rate(dirty),before,"same timestamp cannot double-tick induced activity");
    }
    static void copies(){
        ItemStack leaf=stack(CLEAN,16,20*M);
        var mutable=new BundleContents.Mutable(new BundleContents(List.of(leaf)));mutable.toggleSelectedItem(0);
        ItemStack bundle=stack(CLEAN,1,0);bundle.set(DataComponents.BUNDLE_CONTENTS,mutable.toImmutable());
        for(ItemStack original:List.of(box(List.of(leaf,ItemStack.EMPTY)),bundle)){
            ItemStack copy=original.copy();
            var a=RadiationOwners.children(original);var b=RadiationOwners.children(copy);
            eq(a.size(),b.size(),"copy preserves stable/trailing empty slot addresses");
            require(a.get(0)!=b.get(0),"copied physical owner does not share nested stack identity");
            eq(a.get(0).getCount(),b.get(0).getCount(),"copy preserves nested item count");
            double previous=rate(b.get(0));ExternalRadiation.rate(a.get(0),100*M);
            eq(rate(b.get(0)),previous,"external dose of copied owner remains independent");
            RadiationCluster.step(List.of(original),0,RadiationCluster.Policy.NORMAL,20,null);
            eq(rate(b.get(0)),previous,"processing original cannot irradiate copy remotely");
            eq(RadiationCluster.inspect(List.of(copy)).total(),previous*16,"copied contents remain real source material");
        }
        eq(bundle.copy().get(DataComponents.BUNDLE_CONTENTS).getSelectedItem(),0,"bundle selected slot preserved");
    }
    static void actualFlows(){
        eq(RadiationFlowProbe.transfer(2,12,0.01*0.25),0.025,"ordinary finite difference transfer");
        eq(RadiationFlowProbe.world(false,2,12,0.25),0.025,"actual ordinary flow wiring");
        eq(RadiationFlowProbe.world(true,0.2,12,0.25),0.004,"actual lead target-screen then subtract contamination");
        eq(RadiationFlowProbe.world(true,2,12,0.25),0,"lead cannot raise contamination above screened target");
        for(boolean lead:new boolean[]{false,true})for(double c:new double[]{0,0.2,2,20})
            for(double source:new double[]{0,1,12,100})for(double f:new double[]{0,0.25,1}){
                double expected=lead?0.01*Math.max(0.2*source*f-c,0):0.01*f*Math.max(source-c,0);
                eq(RadiationFlowProbe.world(lead,c,source,f),expected,"flow law grid");
            }
        eq(RadiationFlowProbe.bounded(2,10000,12,10000,10),10,"many world owners cannot overshoot strongest target");
        eq(RadiationFlowProbe.bounded(12,10000,12,10000,10),0,"world transfer stops at maximum target");
        var a=new RadiationFlowProbe.ServerLevel();var b=new RadiationFlowProbe.ServerLevel();
        require(RadiationFlowProbe.claim(a,1,100),"first claim");
        require(!RadiationFlowProbe.claim(a,1,100),"no second same-tick claim");
        require(!RadiationFlowProbe.claim(a,1,119),"one claim per 20 ticks");
        require(RadiationFlowProbe.claim(a,1,120),"next second claim");
        require(RadiationFlowProbe.claim(b,1,100),"same chunk key in another dimension is independent");
        require(RadiationFlowProbe.claim(a,2,100),"other chunk independent");
        require(RadiationFlowProbe.claim(a,1,0),"clock rollback cannot block scan forever");
    }
    static double[] loop(int slots,int count){
        List<ItemStack> roots=new ArrayList<>();for(int i=0;i<slots;i++)roots.add(stack(CLEAN,count,5*Z));
        double c=30.1*M;
        for(int i=1;i<=5000;i++){
            var result=RadiationCluster.step(roots,c,RadiationCluster.Policy.NORMAL,i*20L,null);
            c+=RadiationFlowProbe.transfer(c,result.chunkTarget(),0.01*0.95);
            require(c<5*Z,"closed loop chunk cannot run away");
            for(ItemStack s:roots)require(rate(s)<=5*Z && s.getCount()==count,"closed loop cap/count invariant");
        }
        require(rate(roots.get(0))<40*M,"induced-only loop cools below high activity without hard plateau");
        return new double[]{c,rate(roots.get(0))};
    }
    static void feedback(){
        double[] base=loop(1,1);
        for(int slots:new int[]{1,41})for(int count:new int[]{1,64}){
            double[] result=loop(slots,count);
            eq(result[0],base[0],"slot/count-independent contamination feedback");
            eq(result[1],base[1],"slot/count-independent induced feedback");
        }
    }
    public static void main(String[] args){
        group(RadiationEngineSelfTest::coreLaw);group(RadiationEngineSelfTest::transfers);
        group(RadiationEngineSelfTest::sourceSelection);group(RadiationEngineSelfTest::snapshotAndTrace);
        group(RadiationEngineSelfTest::nested);group(RadiationEngineSelfTest::shieldAndChunk);
        group(RadiationEngineSelfTest::chunkTarget);group(RadiationEngineSelfTest::catchup);
        group(RadiationEngineSelfTest::copies);group(RadiationEngineSelfTest::actualFlows);
        group(RadiationEngineSelfTest::feedback);
        System.out.println("Production radiation core: "+groups+" behavioral groups PASS ("+checks+" assertions; boundary doubles, not Minecraft)");
    }
}
