#!/usr/bin/env python3
"""Execute the REAL radiation core (0.3.315), not a Python balance mirror.

Boundary doubles below model item identity/count, immutable components and preset/
material lookup. They are NOT Minecraft API or Mixin integration evidence. Actual
ItemRadioactivity, RadiationCluster, ExternalRadiation, RadiationCopies, DoseMath,
RadUnits, CarrierItem and ContainerContentsAccess are compiled unchanged.
RadiationOwners.children and RadiationSystem's small pure/scan-claim methods and
world-transfer statements are extracted verbatim. Full owner persistence, event
registration, packet transactions and real item codecs require MC GameTests.

Use Java 21 javac, or JAVA_HOME=<Java 21 runtime> ECJ_JAR=<ECJ with -21 support>.
--mutation-check additionally verifies that targeted production-code regressions
are rejected. Mutations are made only to temporary copies, never src/.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from _radiation_contracts import JAVA, method, read

# Minimal test data model; production lookup/injection/serialization is deliberately
# outside this harness. Do not expand these doubles as proof of Mojmap signatures.
STUBS = {
    "net/minecraft/world/item/Item.java": """
package net.minecraft.world.item;
public class Item {
 public final String id; public final double preset, factor;
 public Item(String id, double preset, double factor) { this.id=id;this.preset=preset;this.factor=factor; }
}
""",
    "net/minecraft/world/item/ItemStack.java": """
package net.minecraft.world.item;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import com.gonzotech.radiation.engine.*;
public class ItemStack {
 public static final ItemStack EMPTY=new ItemStack(new Item("empty",0,0),0);
 private final Item item; private int count;
 private final Map<DataComponents.Key<?>,Object> data=new HashMap<>();
 public ItemStack cargo=EMPTY;
 public ItemStack(Item item,int count) {this.item=item;this.count=count;}
 public Item getItem(){return item;} public int getCount(){return count;}
 public boolean isEmpty(){return this==EMPTY || count<=0;}
 public void grow(int n){count+=n;} public void shrink(int n){count-=n;}
 @SuppressWarnings("unchecked") public <T>T get(DataComponents.Key<T> key){return (T)data.get(key);}
 public <T>void set(DataComponents.Key<T> key,T value){data.put(key,value);}
 // Simulates the copy injection CALLS; does not prove real Mixin application.
 public ItemStack copy(){
  if(isEmpty())return EMPTY;
  ItemStack c=new ItemStack(item,count);c.data.putAll(data);c.cargo=cargo;
  ExternalRadiation.copy(this,c);RadiationCopies.detachNested(this,c);return c;
 }
 public static boolean matches(ItemStack a,ItemStack b){
  return a.item==b.item && a.count==b.count && a.data.equals(b.data);
 }
}
""",
    "net/minecraft/core/component/DataComponents.java": """
package net.minecraft.core.component;
import net.minecraft.world.item.component.*;
public class DataComponents {
 public static final class Key<T> {}
 public static final Key<ItemContainerContents> CONTAINER=new Key<>();
 public static final Key<BundleContents> BUNDLE_CONTENTS=new Key<>();
}
""",
    "net/minecraft/world/item/component/ItemContainerContents.java": """
package net.minecraft.world.item.component;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import com.gonzotech.radiation.engine.ContainerContentsAccess;
public record ItemContainerContents(List<ItemStack> items) implements ContainerContentsAccess {
 public List<ItemStack> gonzo$radiationItems(){return items;}
}
""",
    "net/minecraft/world/item/component/BundleContents.java": """
package net.minecraft.world.item.component;
import java.util.*;
import net.minecraft.world.item.ItemStack;
public class BundleContents {
 private final List<ItemStack> items; private int selected=-1;
 public BundleContents(List<ItemStack> items){this.items=List.copyOf(items);}
 public Iterable<ItemStack> items(){return items;}
 public int getSelectedItem(){return selected;}
 public static class Mutable {
  private final BundleContents value;
  public Mutable(BundleContents source){value=new BundleContents(source.items);value.selected=source.selected;}
  public void toggleSelectedItem(int n){value.selected=n;}
  public BundleContents toImmutable(){return value;}
 }
}
""",
    "net/minecraft/resources/ResourceLocation.java": """
package net.minecraft.resources;
public record ResourceLocation(String path){public String getPath(){return path;}}
""",
    "net/minecraft/core/registries/BuiltInRegistries.java": """
package net.minecraft.core.registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
public class BuiltInRegistries {
 public static final BuiltInRegistries ITEM=new BuiltInRegistries();
 public ResourceLocation getKey(Item item){return new ResourceLocation(item.id);}
}
""",
    "com/gonzotech/radiation/RadSources.java": """
package com.gonzotech.radiation;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import com.gonzotech.radiation.engine.RadiationOwners;
public class RadSources {
 public static double emissionPerItem(ItemStack s){return s.isEmpty()?0:s.getItem().preset;}
 public static double emissionOfStack(ItemStack s){
  double value=emissionPerItem(s)*s.getCount();
  if(s.getItem() instanceof CarrierItem c){ItemStack cargo=c.previewCarried(s);
   value+=CarrierItem.RADIOACTIVITY_FACTOR*emissionPerItem(cargo)*cargo.getCount();}
  return value;
 }
 public static List<ItemStack> contentsOf(ItemStack s){return RadiationOwners.children(s);}
}
""",
    "com/gonzotech/radiation/RadMaterials.java": """
package com.gonzotech.radiation;
import net.minecraft.world.item.ItemStack;
public class RadMaterials {public static double itemFactor(ItemStack s){return s.getItem().factor;}}
""",
    "com/gonzotech/radiation/TestCarrierItem.java": """
package com.gonzotech.radiation;
import net.minecraft.world.item.*;
import com.gonzotech.radiation.engine.ExternalRadiation;
public class TestCarrierItem extends Item implements CarrierItem {
 public TestCarrierItem(){super("carrier",0,1);}
 public boolean hasCarried(ItemStack owner){return !owner.cargo.isEmpty();}
 public ItemStack takeCarried(ItemStack owner){throw new UnsupportedOperationException("not modeled");}
 public ItemStack previewCarried(ItemStack owner){
  return ExternalRadiation.previewContents(owner,owner.cargo.copy());
 }
}
""",
    "com/gonzotech/radiation/ItemToxicity.java": """
package com.gonzotech.radiation;
import net.minecraft.world.item.ItemStack;
public class ItemToxicity {public static double toxicityOfStack(ItemStack stack){return 0;}}
""",
    "com/gonzotech/mixin/ContainerContentsConstructorInvoker.java": """
package com.gonzotech.mixin;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
public class ContainerContentsConstructorInvoker {
 public static ItemContainerContents gonzo$create(List<ItemStack> items){return new ItemContainerContents(List.copyOf(items));}
}
""",
    "com/gonzotech/radiation/engine/MenuDoseTransaction.java": """
package com.gonzotech.radiation.engine;
import net.minecraft.world.item.ItemStack;
public class MenuDoseTransaction {public static void copied(ItemStack a,ItemStack b){}}
""",
}
REAL = ["radiation/ItemRadioactivity.java", "radiation/RadUnits.java",
        "radiation/CarrierItem.java"] + ["radiation/engine/" + name + ".java" for name in
        ("DoseMath", "ExternalRadiation", "RadiationCluster", "RadiationCopies", "ContainerContentsAccess")]


def declaration(source, name):
    match = re.search(r'(?:public|private) static final (?:int|double) ' + name + r'\s*=\s*[^;]+;', source)
    assert match, f"missing production constant {name}"
    return match[0]


def prepare(root, mutation=None):
    for name, source in STUBS.items():
        p = root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(source, encoding="utf-8")
    for name in REAL:
        source = read(name)
        if mutation and name == mutation[0]:
            assert mutation[1] in source, f"mutation no longer applicable: {mutation[1]}"
            source = source.replace(mutation[1], mutation[2], 1)
        p = root / "com/gonzotech" / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(source, encoding="utf-8")
    children = method(read("radiation/engine/RadiationOwners.java"), "public static List<ItemStack> children(")
    (root / "com/gonzotech/radiation/engine/RadiationOwners.java").write_text(
        "package com.gonzotech.radiation.engine; import java.util.*; "
        "import net.minecraft.world.item.ItemStack; import net.minecraft.core.component.DataComponents; "
        "public class RadiationOwners {" + children + "}", encoding="utf-8")
    system = read("radiation/RadiationSystem.java")
    scan = method(system, "private static void scanContainersInto(")
    transfer = re.search(r'double transfer = lead\b.*?;', scan, re.S)
    assert transfer
    max_target = re.search(r'maxTarget = Math.max\(maxTarget, floor.maxTarget\(\)\);', scan)
    clamp = re.search(r'ordinaryTransfer = Math.min\(ordinaryTransfer \+ floor.ordinaryTransfer\(\),.*?;', scan)
    assert max_target and clamp
    probe = """import java.util.*;
public class RadiationFlowProbe {
 public static final class ServerLevel {}
 private static final Map<ServerLevel,Map<Long,Long>> LAST_CONTENT_SCAN=new WeakHashMap<>();
""" + declaration(system, "PLAYER_PERIOD_TICKS") + declaration(system, "CHEST_TO_CHUNK_RATE")
    probe += method(system, "private static double transferTowardChunk(")
    probe += method(system, "private static boolean claimContentScan(")
    probe += """
 public static double transfer(double c,double t,double r){return transferTowardChunk(c,t,r);}
 public static boolean claim(ServerLevel l,long k,long t){return claimContentScan(l,k,t);}
 public static double world(boolean lead,double contamination,double target,double factor){
""" + transfer[0] + "return transfer;}"
    probe += """
 private record Floor(double ordinaryTransfer,double maxTarget) {}
 public static double bounded(double contamination,double ordinaryTransfer,double maxTarget,double floorTransfer,double floorTarget){
 var floor=new Floor(floorTransfer,floorTarget);
""" + max_target[0] + clamp[0] + "return ordinaryTransfer;} }"
    (root / "RadiationFlowProbe.java").write_text(probe, encoding="utf-8")
    lead = root / "com/gonzotech/machines/block/ThirdLeadChestBlock.java"
    lead.parent.mkdir(parents=True, exist_ok=True)
    lead.write_text("package com.gonzotech.machines.block; public class ThirdLeadChestBlock {" +
                    declaration(read("machines/block/ThirdLeadChestBlock.java"), "CONTENT_CHUNK_FACTOR") + "}")
    shutil.copy(Path(__file__).with_name("RadiationEngineSelfTest.java"), root)


def run(java, compiler, mutation=None):
    with tempfile.TemporaryDirectory(prefix="gonzo-radiation-core-") as temp:
        root = Path(temp)
        prepare(root, mutation)
        files = sorted(str(p) for p in root.rglob("*.java"))
        compiled = subprocess.run(compiler + ["-proc:none", "-d", str(root / "classes")] + files,
                                  text=True, capture_output=True)
        if compiled.returncode:
            raise RuntimeError("radiation harness compilation failed (not a mutation kill):\n" + compiled.stdout + compiled.stderr)
        return subprocess.run([java, "-ea", "-cp", str(root / "classes"), "RadiationEngineSelfTest"],
                              text=True, capture_output=True)


def main():
    home = os.environ.get("JAVA_HOME")
    java = str(Path(home) / "bin/java") if home else shutil.which("java")
    javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
    ecj = os.environ.get("ECJ_JAR")
    if not java or not Path(java).is_file():
        raise SystemExit("Java 21 required: set JAVA_HOME")
    if ecj:
        compiler = [java, "-jar", ecj, "-21"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("Java 21 required: javac or ECJ_JAR needed")
    result = run(java, compiler)
    if result.returncode:
        raise SystemExit(result.stdout + result.stderr)
    print(result.stdout.strip())
    if "--mutation-check" in sys.argv:
        mutations = [
            ("radiation/ItemRadioactivity.java", "UPTAKE_COST = 0.0013", "UPTAKE_COST = 0.00097"),
            ("radiation/ItemRadioactivity.java", "seeded / RadUnits.MILLI", "seeded"),
            ("radiation/ItemRadioactivity.java", "sourceNzt - intrinsic", "sourceNzt"),
            ("radiation/ItemRadioactivity.java", "INDUCED_REEMIT = 0.75", "INDUCED_REEMIT = 1.0"),
            ("radiation/ItemRadioactivity.java", "nowTick, false)", "nowTick, true)"),
            ("radiation/engine/RadiationCluster.java", "Policy LEAD = new Policy(0.2)", "Policy LEAD = new Policy(1)"),
            ("radiation/engine/RadiationCluster.java", "chunkEmission>CHUNK_THRESHOLD", "chunkEmission>=CHUNK_THRESHOLD"),
            ("radiation/engine/RadiationCluster.java", "suffix[node.end]", "suffix[i]"),
            ("radiation/engine/RadiationCluster.java", "preset + strongestInduced", "preset + induced"),
            ("radiation/engine/RadiationCopies.java", "items.add(child.copy())", "items.add(child)"),
            ("radiation/engine/ExternalRadiation.java", "DoseMath.merge(a.rate(), destination.getCount(), b.rate(), added)", "a.rate() + b.rate()"),
        ]
        for mutation in mutations:
            result = run(java, compiler, mutation)
            if not result.returncode or "AssertionError" not in result.stderr:
                raise SystemExit(f"mutation survived or failed outside an assertion: {mutation}\n{result.stderr}")
            print("KILLED:", mutation[0], mutation[1])
        print(f"Mutation sensitivity: {len(mutations)}/{len(mutations)} rejected by behavioral assertions")


if __name__ == "__main__":
    main()
