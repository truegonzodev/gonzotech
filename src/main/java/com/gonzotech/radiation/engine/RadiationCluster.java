package com.gonzotech.radiation.engine;

import com.gonzotech.radiation.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;

/** One physical owner's slots. Menus are views, never cluster owners.
 * Reads all sources before updating external rates; never writes item components/counts. */
public final class RadiationCluster {
    public static final double CHUNK_THRESHOLD = 30 * RadUnits.MILLI;
    /** Owner shielding applies to both other-item transfer and incoming chunk strength. */
    public record Policy(double internalTransfer) {
        public static final Policy NORMAL = new Policy(1);
        public static final Policy LEAD = new Policy(0.2);
        public Policy {
            if (!Double.isFinite(internalTransfer) || internalTransfer < 0 || internalTransfer > 1)
                throw new IllegalArgumentException("Invalid internal transfer factor");
        }
    }
    public record Step(String path, ItemStack stack, double before, double after,
                       double otherSource, double chunkSource, double target, double factor) {}
    public record Summary(int slots, long count, double preset, double induced,
                          double total, double strongestItem, double strongestInduced, boolean changed) {
        /** Natural sources retain quantity; induced feedback never gains a count multiplier. */
        public double chunkTarget() { return preset + strongestInduced; }
    }
    private record Source(double full, double transfer) {
        private static final Source ZERO = new Source(0,0);
    }
    private static final class Node {
        final ItemStack stack;
        final String path;
        final double shielding;
        final ItemStack cargoOwner;
        int end;
        Node(ItemStack stack, String path, double shielding, ItemStack cargoOwner) {
            this.stack=stack;this.path=path;this.shielding=shielding;this.cargoOwner=cargoOwner;
        }
    }
    private RadiationCluster() {}

    private static List<Node> flatten(List<ItemStack> roots) {
        List<Node> nodes=new ArrayList<>();
        Set<ItemStack> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=0;i<roots.size();i++) add(nodes,seen,roots.get(i),Integer.toString(i),1,null,0);
        return nodes;
    }
    private static void add(List<Node> nodes, Set<ItemStack> seen, ItemStack stack, String path,
                            double shielding, ItemStack cargoOwner, int depth) {
        if(stack.isEmpty() || !seen.add(stack)) return;
        Node node=new Node(stack,path,shielding,cargoOwner);nodes.add(node);
        // Cargo is part of the carrier's own emission, even at the nesting boundary.
        // Its format is item id/count plus external dose, not another container tree.
        if(stack.getItem() instanceof CarrierItem carrier) {
            ItemStack cargo=carrier.previewCarried(stack);
            if(!cargo.isEmpty() && seen.add(cargo)) {
                Node child=new Node(cargo,path+"/cargo",shielding*CarrierItem.RADIOACTIVITY_FACTOR,stack);
                nodes.add(child);child.end=nodes.size();
            }
        }
        if(depth<4) {
            int i=0;
            for(ItemStack child:RadiationOwners.children(stack))
                add(nodes,seen,child,path+"/"+(i++),shielding,null,depth+1);
        }
        node.end=nodes.size();
    }
    private static Source max(Source a,Source b) {
        return a.full>b.full || (a.full==b.full && a.transfer>b.transfer)?a:b;
    }
    private static double preset(ItemStack stack) {
        return stack.getItem() instanceof CarrierItem ? 0 : RadSources.emissionPerItem(stack);
    }
    /** Diagnostics use the same traversal and summation, without advancing time. */
    public static Summary inspect(List<ItemStack> roots) { return summarize(flatten(roots),false); }

    public static Summary step(List<ItemStack> roots, double chunkEmission, Policy policy,
                               long now, Consumer<Step> trace) {
        List<Node> nodes=flatten(roots);
        int n=nodes.size();
        if(n==0)return summarize(nodes,false);
        Source[] prefix=new Source[n+1],suffix=new Source[n+1];
        prefix[0]=suffix[n]=Source.ZERO;
        Source[] sources=new Source[n];
        for(int i=0;i<n;i++) {
            Node node=nodes.get(i);
            double base=preset(node.stack),rate=ItemRadioactivity.inducedBeforeSourceStep(node.stack,now);
            sources[i]=new Source((base+rate)*node.shielding,
                (base+rate*ItemRadioactivity.INDUCED_REEMIT)*node.shielding);
            prefix[i+1]=max(prefix[i],sources[i]);
        }
        for(int i=n-1;i>=0;i--)suffix[i]=max(sources[i],suffix[i+1]);
        // Contours are outward-only. Lead-owner shielding is separate and applies
        // BEFORE the >30 mZt/s incoming threshold (author clarification, 318).
        double incomingChunk=chunkEmission*policy.internalTransfer;
        double chunk=incomingChunk>CHUNK_THRESHOLD?incomingChunk:0;
        boolean changed=false;
        for(int i=0;i<n;i++) {
            Node node=nodes.get(i);ItemStack stack=node.stack;
            // Exclude self AND owned contents for a wrapper; sibling slots remain candidates.
            double other=max(prefix[i],suffix[node.end]).transfer*policy.internalTransfer;
            double source=Math.max(other,chunk); // Incoming chunk ignores outward contour shielding.
            double before=ItemRadioactivity.getInduced(stack),factor=RadMaterials.itemFactor(stack);
            int count=stack.getCount();
            double after=ItemRadioactivity.tickSingleInducedAt(stack,source>0,source,factor,now);
            if(count!=stack.getCount())throw new IllegalStateException("Radiation changed item count");
            if(node.cargoOwner!=null)ExternalRadiation.writeContents(node.cargoOwner,ExternalRadiation.read(stack));
            changed|=before!=after;
            if(trace!=null) trace.accept(new Step(node.path,stack,before,after,other,chunk,
                Math.min(Math.max(source-preset(stack),0),ItemRadioactivity.INDUCED_CAP),factor));
        }
        return summarize(nodes,changed);
    }
    private static Summary summarize(List<Node> nodes,boolean changed) {
        double base=0,induced=0,strongest=0,strongestInduced=0;long count=0;
        for(Node node:nodes) {
            count+=node.stack.getCount();
            double perItem=ItemRadioactivity.getInduced(node.stack)*node.shielding;
            double natural=preset(node.stack)*node.shielding;
            strongestInduced=Math.max(strongestInduced,perItem);
            strongest=Math.max(strongest,natural+perItem);
            base+=natural*node.stack.getCount();
            induced+=perItem*node.stack.getCount();
        }
        return new Summary(nodes.size(),count,base,induced,base+induced,strongest,strongestInduced,changed);
    }
}
