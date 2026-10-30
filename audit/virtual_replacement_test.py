#!/usr/bin/env python3
"""PERF-001/11: actual virtual replacement + callers, no inventory-side mirror.
Reuses the upstream wrappers/explicit MC and manual Mixin boundaries of
slot_capacity_test; injects real setVirtual/get/remove bodies into its BE probe.
--mutation-check must assertion-kill the exact old method and temporary mutants.
"""
import hashlib,json,os,shutil,subprocess,sys,tempfile
from pathlib import Path
from _radiation_contracts import method
import slot_capacity_test as admission

ROOT=admission.ROOT
SOURCE=admission.JAVA/'machines/block/entity/PlotnickBlockEntity.java'
FIX=ROOT/'audit/fixtures/virtual_replacement'

PROBE=r'''package probe;IMPORTS
import net.neoforged.neoforge.items.wrapper.*;import net.minecraft.world.level.block.entity.HopperBlockEntity;
public class Probe{
static int checks;static Item a=new Item(),b=new Item(),single=new Item(1);
static void eq(int expected,int actual,String why){checks++;if(expected!=actual)throw new AssertionError(why+": "+expected+" != "+actual);}static void yes(boolean v,String why){eq(1,v?1:0,why);}
static ItemStack stack(Item i,int n,int component){return n==0?ItemStack.EMPTY:new ItemStack(i,n,component);}
static void quiet(Plotnick p){eq(0,p.automaticInsert,"setter must not call insert");eq(0,p.automaticDump,"setter must not call dump");eq(0,p.drops,"setter must not spill");}
public static void main(String[]args){try{runProbe();}catch(RuntimeException e){throw new AssertionError("unexpected invalid replacement",e);}}
static void runProbe(){
 for(int slot:new int[]{11,12,13,14,34})for(Item oldType:new Item[]{a,b})for(Item newType:new Item[]{a,b})
 for(int before:new int[]{0,1,7,63,64})for(int after:new int[]{0,1,3,64})for(int component:new int[]{17,91}){
  Plotnick p=new Plotnick();ItemStack old=stack(oldType,before,17),incoming=stack(newType,after,component);
  p.setItem(slot,old);int dirty=p.changes;p.networkStatsDirty=false;
  p.setItem(slot,incoming);
  yes(p.getItem(slot)==incoming,"replacement stores caller value, no synthesized merge");eq(after,p.getItem(slot).getCount(),"exact replacement count");
  eq(before,old.getCount(),"caller owns displaced stack, no implicit consumption");eq(after,incoming.getCount(),"setter does not split donor");
  eq(before+after,old.getCount()+p.getItem(slot).getCount(),"explicit owner swap conservation");
  if(after>0)eq(component,p.getItem(slot).component,"replacement components preserved");
  eq(before==0&&after==0?dirty:dirty+1,p.changes,"empty no-op vs dirty replacement");yes(before==0&&after==0?!p.networkStatsDirty:p.networkStatsDirty,"stats invalidated for real replacement");quiet(p);
 }
 Plotnick p=new Plotnick();ItemStack initial=stack(a,7,17);p.setItem(13,initial);int dirty=p.changes;
 for(int idx:new int[]{-1,24,1000})p.writeVirtualForProbe(idx,stack(b,1,91));eq(dirty,p.changes,"invalid virtual index no-op");yes(p.getItem(13)==initial,"invalid index leaves inventory intact");quiet(p);
 // Invalid raw replacement is rejected BEFORE donor mutation/routing, not trimmed.
 for(Item item:new Item[]{a,single}){
  int badCount=item==single?2:65;p=new Plotnick();initial=stack(a,7,17);p.setItem(13,initial);ItemStack bad=stack(item,badCount,91);dirty=p.changes;boolean failed=false;
  try{p.setItem(13,bad);}catch(IllegalArgumentException expected){failed=true;}
  yes(failed,"oversized raw replacement rejected");eq(badCount,bad.getCount(),"rejected donor untouched");yes(p.getItem(13)==initial,"invalid replacement atomic");eq(dirty,p.changes,"invalid replacement does not dirty");quiet(p);
 }
 // Legacy overflow is not migrated or 'fixed' here; in-place updates still work.
 p=new Plotnick();initial=stack(a,128,17);p.virtual.set(2,initial);p.setItem(13,initial);eq(128,p.getItem(13).getCount(),"legacy same-ref not trimmed");quiet(p);
 // Full remove transfers ownership once; setter EMPTY must not return it a second time.
 p=new Plotnick();p.setItem(13,stack(a,16,17));ItemStack owned=p.removeItemNoUpdate(13);eq(16,owned.getCount(),"removeNoUpdate hands old stack to caller");eq(0,p.getItem(13).getCount(),"removed inventory empty");p.setItem(13,ItemStack.EMPTY);eq(16,owned.getCount(),"clearing does not consume extracted owner");quiet(p);
 // Actual NeoForge algorithms use actual virtual setter, not the admission fixture stub.
 p=new Plotnick();var handler=new InvWrapper(p);ItemStack offer=stack(a,16,17);dirty=p.changes;
 ItemStack left=handler.insertItem(13,offer,true);eq(0,left.getCount(),"handler simulation predicts full acceptance");eq(0,p.getItem(13).getCount(),"simulate never writes virtual");eq(dirty,p.changes,"simulate no dirty");
 left=handler.insertItem(13,offer,false);eq(0,left.getCount(),"handler commit accepted");eq(16,p.getItem(13).getCount(),"handler virtual commit");
 left=handler.insertItem(13,stack(a,3,17),false);eq(0,left.getCount(),"handler same-type merge accepted");eq(19,p.getItem(13).getCount(),"merge done by caller, not setter");
 ItemStack displaced=p.getItem(13);handler.setStackInSlot(13,stack(b,2,91));eq(19,displaced.getCount(),"explicit replacement caller retains old owner");eq(2,p.getItem(13).getCount(),"modifiable handler raw replacement");quiet(p);
 p=new Plotnick();var sided=new SidedInvWrapper(p,Direction.DOWN);left=sided.insertItem(2,stack(a,16,17),false);eq(0,left.getCount(),"DOWN wrapper2 to physical13 depot");eq(16,p.getItem(13).getCount(),"sided virtual commit");
 ItemStack extracted=sided.extractItem(2,5,true);eq(5,extracted.getCount(),"sided extract simulation");eq(16,p.getItem(13).getCount(),"extract simulation storage unchanged");
 extracted=sided.extractItem(2,5,false);eq(5,extracted.getCount(),"sided extract commit");eq(11,p.getItem(13).getCount(),"sided extraction exact");quiet(p);
 // Real ItemRouting insert/mutation into virtual storage still respects full stacks.
 p=new Plotnick();yes(ItemRouting.tryPlaceForProbe(p,13,stack(a,1,17)),"pipe empty virtual insertion");yes(ItemRouting.tryPlaceForProbe(p,13,stack(a,1,17)),"pipe merges via caller");eq(2,p.getItem(13).getCount(),"pipe count");
 p.setItem(13,stack(a,64,17));yes(!ItemRouting.tryPlaceForProbe(p,13,stack(a,1,17)),"pipe full rejects without debit");eq(64,p.getItem(13).getCount(),"pipe full unchanged");quiet(p);
 // Hopper caller ownership: tentative remove -> destination refusal -> snapshot restore.
 p=new Plotnick();p.setItem(11,stack(a,7,17));Base refuses=new Base(){public boolean canPlaceItem(int s,ItemStack v){return false;}};
 for(int attempt=0;attempt<100;attempt++){
  ItemStack snapshot=p.getItem(11).copy();ItemStack token=p.removeItem(11,1);eq(1,token.getCount(),"tentative extraction one");
  left=HopperBlockEntity.move(refuses,token,0,Direction.UP);eq(1,left.getCount(),"destination refusal returns reserved item");
  if(!left.isEmpty())p.setItem(11,snapshot); // ownership of the failed token ends at rollback
  eq(7,p.getItem(11).getCount(),"rollback restores snapshot once");eq(0,refuses.getItem(0).getCount(),"refusing destination empty");quiet(p);
 }
 Base accepts=new Base();ItemStack token=p.removeItem(11,1);left=HopperBlockEntity.move(accepts,token,0,Direction.UP);eq(0,left.getCount(),"successful transfer needs no rollback");eq(7,p.getItem(11).getCount()+accepts.getItem(0).getCount(),"successful source+destination conservation");quiet(p);
 System.out.println("Virtual replacement PASS: "+checks+" assertions; production setter/callers, MC/manual-mixin boundaries only");
}}
'''.replace('IMPORTS',admission.IMPORTS)

def sources(virtual_method=None):
    text=SOURCE.read_text();out=admission.sources()
    out['probe/Probe.java']=PROBE
    # Vary per-item stack size to test rejection before mutation for nonstackables.
    out['net/minecraft/world/item/Item.java']='package net.minecraft.world.item;public class Item{public final int max;public Item(){this(64);}public Item(int max){this.max=max;}}'
    out['net/minecraft/world/item/ItemStack.java']=out['net/minecraft/world/item/ItemStack.java'].replace('return 64;','return item==null?64:item.max;')
    code=out['probe/Plotnick.java']
    stub='void setVirtual(int i,ItemStack s){virtual.set(i,s);}'
    assert code.count(stub)==1
    code=code.replace(stub,virtual_method or method(text,'private void setVirtual('))
    old_get='public ItemStack getItem(int s){return s>=11?virtual.get(s-11):items.get(s);}'
    assert code.count(old_get)==1
    code=code.replace(old_get,method(text,'public ItemStack getItem('))
    cap='static int capacityFor(ItemStack s){return Math.min(64,s.getMaxStackSize());}'
    assert code.count(cap)==1;code=code.replace(cap,method(text,'public static int capacityFor('))
    code=code[:code.rfind('}')]+admission.methods(text,['public ItemStack removeItem(', 'public ItemStack removeItemNoUpdate(', 'public boolean canTakeItemThroughFace('])+'''
public int automaticInsert,automaticDump;
boolean insert(ItemStack s){automaticInsert++;return false;}void dump(ItemStack s,Object pos){automaticDump++;}
public void writeVirtualForProbe(int idx,ItemStack s){setVirtual(idx,s);}
}'''
    out['probe/Plotnick.java']=code
    out['net/minecraft/world/ContainerHelper.java']='''package net.minecraft.world;import java.util.List;import net.minecraft.world.item.ItemStack;public class ContainerHelper{public static ItemStack removeItem(List<ItemStack> items,int s,int n){return items.get(s).split(n);}public static ItemStack takeItem(List<ItemStack> items,int s){ItemStack v=items.get(s);items.set(s,ItemStack.EMPTY);return v;}}'''
    return out

def run(virtual_method=None):
    home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else shutil.which('java');javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
    compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-nowarn'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
    with tempfile.TemporaryDirectory(prefix='gonzo-virtual-replacement-') as tmp:
        root=Path(tmp);paths=[]
        for name,code in sources(virtual_method).items():
            p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);paths.append(str(p))
        compiled=subprocess.run(compiler+['-d',str(root/'classes')]+paths,capture_output=True,text=True)
        assert compiled.returncode==0,'compiler failure is not a mutation kill:\n'+compiled.stdout+compiled.stderr
        return subprocess.run([java,'-cp',str(root/'classes'),'probe.Probe'],capture_output=True,text=True)

if __name__=='__main__':
    meta=json.loads((FIX/'manifest.json').read_text());old=(FIX/meta['file']).read_bytes()
    assert len(old)==meta['bytes'] and hashlib.sha256(old).hexdigest()==meta['sha256']
    # If the source object is available, verify the frozen method against Git too.
    if subprocess.run(['git','cat-file','-e',meta['source_commit']+'^{commit}'],cwd=ROOT,capture_output=True).returncode==0:
        historical=subprocess.check_output(['git','show',meta['source_commit']+':'+meta['source']],cwd=ROOT,text=True)
        assert old.decode()==method(historical,'private void setVirtual(')+'\n'
    for record in json.loads((admission.FIX/'manifest.json').read_text())['files']:
        assert hashlib.sha256((admission.FIX/record['file']).read_bytes()).hexdigest()==record['sha256']
    # The only direct private caller is setItem; transfer and return remain explicit.
    text=SOURCE.read_text();assert text.count('setVirtual(')==2
    result=run();assert result.returncode==0,result.stdout+result.stderr;print(result.stdout.strip())
    if '--mutation-check' in sys.argv:
        original=method(text,'private void setVirtual(')
        mutations=[('exact0.3.323',old.decode()),
            ('return-displaced',original.replace('virtual.set(idx, stack);','ItemStack prev=virtual.get(idx);virtual.set(idx, stack);if(!prev.isEmpty())insert(prev);')),
            ('drop-displaced',original.replace('virtual.set(idx, stack);','ItemStack prev=virtual.get(idx);virtual.set(idx, stack);if(!prev.isEmpty())dump(prev,worldPosition);')),
            ('merge-not-replace',original.replace('virtual.set(idx, stack);','stack.grow(virtual.get(idx).getCount());virtual.set(idx, stack);')),
            ('skip-empty',original.replace('if (stack.isEmpty() && virtual.get(idx).isEmpty()) return;','if (stack.isEmpty()) return;')),
            ('missing-limit',original.replace('SlotCapacity.requireReplacement(this, CONTAINER_SIZE + idx, stack);','')),
            ('missing-invalidation',original.replace('networkStatsDirty = true;','')),
            ('missing-dirty',original.replace('setChanged();','')),
        ]
        for label,code in mutations:
            assert code!=original,label
            result=run(code)
            assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived or non-assertion failure: '+label+'\n'+result.stdout+result.stderr
            print('REJECTED:',label)
        print(f'Mutation sensitivity: {len(mutations)}/{len(mutations)} assertion-killed')
