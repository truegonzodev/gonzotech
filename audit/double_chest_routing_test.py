#!/usr/bin/env python3
"""PERF-001/1: whole production routing with fresh 54-slot compound views.
World/BE/chest assembly/item components are observed doubles, not Minecraft.
Exercise real router lookup/live/cache/push; exact325 and assertion mutants.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from item_loaded_chunks_test import run, SOURCE, JAVA

ROOT=Path(__file__).resolve().parents[1]
PROBE=r'''package com.gonzotech.machines.network;
import java.lang.reflect.*;import java.util.*;import net.minecraft.core.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.ChestType;
public class ChestProbe {
static int checks;static final Item GOODS=new Item(),FILLER=new Item();
static void eq(int want,int got,String why){checks++;if(want!=got)throw new AssertionError(why+": "+got+" != "+want);}static void ok(boolean v,String why){eq(1,v?1:0,why);}static BlockPos p(int x,int z){return new BlockPos(x,64,z);}
static class Pipe extends Block implements PipeCarrier{final PipeMode mode;Pipe(PipeMode m){mode=m;}public boolean carries(BlockState s,PipeType t){return true;}public boolean opensToward(BlockState s,PipeType t,Direction d){return true;}public PipeMode modeFor(BlockState s,PipeType t){return mode;}}
static class Box implements Container{final Level l;final BlockPos pos;final ItemStack[] slots;int writes;Box(Level l,BlockPos p,int n){this.l=l;pos=p;slots=new ItemStack[n];Arrays.fill(slots,ItemStack.EMPTY);l.containers.put(p,this);}void live(){if(!l.hasChunkAt(pos))throw new AssertionError("unloaded physical inventory touched");}public int getContainerSize(){live();return slots.length;}public ItemStack getItem(int s){live();return slots[s];}public void setItem(int s,ItemStack v){live();slots[s]=v;writes++;}public ItemStack removeItem(int s,int n){live();ItemStack v=slots[s];int count=Math.min(n,v.getCount());ItemStack out=v.copyWithCount(count);v.shrink(count);return out;}public void setChanged(){live();}public int getMaxStackSize(){return 64;}public boolean canPlaceItem(int s,ItemStack v){live();return true;}int total(){return Arrays.stream(slots).mapToInt(v->v.getCount()).sum();}void fill(Item i,int n){for(int s=0;s<slots.length;s++)slots[s]=new ItemStack(i,n);}}
static Box[] pair(Level l,BlockPos near,Direction dir,boolean reverse){BlockPos far=near.relative(dir);l.states.put(near,new BlockState(new ChestBlock(),reverse?ChestType.RIGHT:ChestType.LEFT,dir));l.states.put(far,new BlockState(new ChestBlock(),reverse?ChestType.LEFT:ChestType.RIGHT,dir.getOpposite()));return new Box[]{new Box(l,near,27),new Box(l,far,27)};}
static int sum(Box...boxes){return Arrays.stream(boxes).mapToInt(Box::total).sum();}static void clean(Level l){eq(0,l.illegalReads,"zero unloaded state/BE reads");}
static class Rig{final Level l=new Level();final BlockPos root,source,dest;final BlockState start;final Direction partner;final Box[] from,to;
Rig(Direction partner,boolean reverse,boolean sourceDouble,boolean targetDouble){this.partner=partner;l.assembleChests=true;root=p(6,6);l.load(root);Direction forward=partner==Direction.NORTH||partner==Direction.SOUTH?Direction.EAST:Direction.SOUTH;source=root.relative(forward.getOpposite());BlockPos mid=root.relative(forward),last=mid.relative(forward);dest=last.relative(forward);start=new BlockState(new Pipe(PipeMode.PULL));l.states.put(root,start);l.states.put(mid,new BlockState(new Pipe(PipeMode.AUTO)));l.states.put(last,new BlockState(new Pipe(PipeMode.PUSH)));from=sourceDouble?pair(l,source,partner,reverse):new Box[]{new Box(l,source,1)};to=targetDouble?pair(l,dest,partner,reverse):new Box[]{new Box(l,dest,1)};}
int tick(){return ItemRouting.tickExtract(l,root,start);}void next(){l.tick++;}void setSource(int slot,int n){from[slot/27].slots[slot%27]=new ItemStack(GOODS,n);}void setTarget(int slot,int n){to[slot/27].slots[slot%27]=new ItemStack(GOODS,n);}int total(){return sum(from)+sum(to);}}
static Object call(String name,Class<?>[]types,Object...args){try{Method m=ItemRouting.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof AssertionError a)throw a;throw new AssertionError(e.getCause());}catch(Exception e){throw new AssertionError(e);}}
static List<?> cached(Rig r){return (List<?>)call("sinksCached",new Class<?>[]{Level.class,BlockPos.class},r.l,r.root);}
static int push(Rig r,Container src,List<?> sinks){return (int)call("pushItems",new Class<?>[]{Level.class,BlockPos.class,Container.class,Direction.class,List.class,BlockPos.class,int.class,int.class,long.class},r.l,r.root,src,Direction.EAST,sinks,r.source,5,1,r.l.tick);}
public static void main(String[]args){
// Every native horizontal pairing/contact type and every one of 54 slots.
for(Direction dir:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(boolean reverse:new boolean[]{false,true})for(int slot=0;slot<54;slot++){
 Rig r=new Rig(dir,reverse,false,true);for(Box b:r.to)b.fill(FILLER,64);r.to[slot/27].slots[slot%27]=ItemStack.EMPTY;r.setSource(0,64);int before=r.total();
 Container one=ItemRouting.containerAt(r.l,r.dest),two=ItemRouting.containerAt(r.l,r.dest);ok(one!=two,"fresh wrapper on each lookup");eq(54,one.getContainerSize(),"full compound capacity");
 eq(1,r.tick(),"ordinary -> double accepted");eq(63,r.from[0].total(),"debit only accepted");eq(1,r.to[slot/27].slots[slot%27].getCount(),"last available slot reached");eq(before,r.total(),"insertion conservation");clean(r.l);
 r=new Rig(dir,reverse,true,false);r.setSource(slot,64);before=r.total();eq(1,r.tick(),"double -> ordinary accepted");eq(63,sum(r.from),"extract any of 54");eq(1,r.to[0].total(),"ordinary receiver populated");eq(before,r.total(),"extraction conservation");clean(r.l);
 r=new Rig(dir,reverse,true,true);r.setSource(slot,64);before=r.total();eq(1,r.tick(),"double -> double accepted");eq(63,sum(r.from),"double source debit");eq(1,sum(r.to),"double target credit");eq(before,r.total(),"double-to-double conservation");clean(r.l);
}
// Real backpressure under separate PULL/PUSH endpoints, no balance change.
for(int slot:new int[]{0,26,27,53}){
 Rig r=new Rig(Direction.SOUTH,false,false,true);for(Box b:r.to)b.fill(FILLER,64);r.setTarget(slot,63);r.setSource(0,2);int before=r.total();eq(1,r.tick(),"63+2 admits only one");eq(1,r.from[0].total(),"one leftover at source");eq(64,r.to[slot/27].slots[slot%27].getCount(),"stack reaches 64");r.next();eq(0,r.tick(),"full target backpressure");eq(before,r.total(),"full/partial stacking conservation");clean(r.l);
}
// Cache revalidation must tolerate repeated wrappers without forcing TTL expiry.
Rig r=new Rig(Direction.SOUTH,false,false,true);r.setSource(0,64);for(int n=0;n<64;n++){eq(1,r.tick(),"warm cache routes to fresh wrapper");r.next();}eq(0,r.from[0].total(),"64 delivered");eq(64,sum(r.to),"no duplicate on cache hits");clean(r.l);
// Cached target coordinates are NOT identity: replace either or both BEs.
for(int replaced=1;replaced<=3;replaced++){
 r=new Rig(Direction.SOUTH,false,false,true);r.setSource(0,16);List<?> sinks=cached(r);Box[]old=r.to.clone();for(int i=0;i<2;i++)if((replaced&(1<<i))!=0)r.to[i]=new Box(r.l,old[i].pos,27);
 eq(0,push(r,ItemRouting.containerAt(r.l,r.source),sinks),"replacement invalidates saved target");eq(16,r.from[0].total(),"replacement no debit");eq(0,sum(old),"retired BEs untouched");eq(0,sum(r.to),"replacement no ghost credit");clean(r.l);
 r=new Rig(Direction.SOUTH,false,true,false);r.setSource(53,16);Container captured=ItemRouting.containerAt(r.l,r.source);sinks=cached(r);Box[]retired=r.from.clone();for(int i=0;i<2;i++)if((replaced&(1<<i))!=0)r.from[i]=new Box(r.l,retired[i].pos,27);
 eq(0,push(r,captured,sinks),"captured replaced source rejected");eq(16,sum(retired),"retired source not debited");eq(0,r.to[0].total(),"no old-source ghost delivery");clean(r.l);
}
// Split and re-pair with another physical half reject the old target.
for(boolean split:new boolean[]{false,true}){
 r=new Rig(Direction.SOUTH,false,false,true);r.setSource(0,16);List<?> sinks=cached(r);if(split){r.l.states.put(r.dest,new BlockState(new ChestBlock()));r.l.states.put(r.dest.relative(Direction.SOUTH),new BlockState(new ChestBlock()));}else{r.l.states.put(r.dest,new BlockState(new ChestBlock(),ChestType.LEFT,Direction.NORTH));BlockPos newFar=r.dest.relative(Direction.NORTH);r.l.states.put(newFar,new BlockState(new ChestBlock(),ChestType.RIGHT,Direction.SOUTH));new Box(r.l,newFar,27);}
 eq(0,push(r,ItemRouting.containerAt(r.l,r.source),sinks),"split/re-pair invalidates old view");eq(16,r.from[0].total(),"topology change no debit");eq(0,sum(r.to),"no retired-half delivery");clean(r.l);
}
// Held genuine compound views also stop at an unloaded half, in both directions.
for(boolean isSource:new boolean[]{false,true}){
 Level w=new Level();w.assembleChests=true;w.load(p(15,8));w.load(p(16,8));BlockPos root=isSource?p(14,8):p(13,8),mid=isSource?p(14,9):p(13,9),last=isSource?p(13,9):p(14,9),sourcePos=isSource?p(15,8):p(12,8),destPos=isSource?p(12,9):p(15,9);BlockState start=new BlockState(new Pipe(PipeMode.PULL));w.states.put(root,start);w.states.put(mid,new BlockState(new Pipe(PipeMode.AUTO)));w.states.put(last,new BlockState(new Pipe(PipeMode.PUSH)));
 Box[]sourceBoxes=isSource?pair(w,sourcePos,Direction.EAST,false):new Box[]{new Box(w,sourcePos,1)};Box[]targetBoxes=isSource?new Box[]{new Box(w,destPos,1)}:pair(w,destPos,Direction.EAST,false);sourceBoxes[0].slots[0]=new ItemStack(GOODS,16);Container held=ItemRouting.containerAt(w,sourcePos);List<?> targets=(List<?>)call("sinksCached",new Class<?>[]{Level.class,BlockPos.class},w,root);w.unload(p(16,8));
 eq(0,(int)call("pushItems",new Class<?>[]{Level.class,BlockPos.class,Container.class,Direction.class,List.class,BlockPos.class,int.class,int.class,long.class},w,root,held,Direction.EAST,targets,sourcePos,5,1,w.tick),"held compound obeys unload boundary");eq(16,sum(sourceBoxes),"unloaded pair retains donor");eq(0,sum(targetBoxes),"no half-loaded delivery");clean(w);
 w.load(p(16,8));ItemRouting.onWorldEdit(w);w.tick++;eq(1,ItemRouting.tickExtract(w,root,start),"external loading restores fresh-view route");eq(16,sum(sourceBoxes)+sum(targetBoxes),"reload conserves all items");clean(w);
}
// Two connections to the SAME physical chest cannot become a transfer target.
Level l=new Level();l.assembleChests=true;BlockPos a=p(9,8),b=p(9,9),root=p(8,8),second=p(8,9);l.load(a);Box[]own=pair(l,a,Direction.SOUTH,false);own[0].slots[0]=new ItemStack(GOODS,16);BlockState auto=new BlockState(new Pipe(PipeMode.AUTO));l.states.put(root,auto);l.states.put(second,auto);
eq(0,ItemRouting.tickExtract(l,root,auto),"no self transfer through other wrapper");eq(0,ItemRouting.tickExtract(l,second,auto),"no self transfer through second half");eq(16,sum(own),"self route preserves amount");eq(0,l.flow,"self route produces no fictitious flow");clean(l);
// Receiving through one contact forbids a return from the other physical half.
Rig inserted=new Rig(Direction.SOUTH,false,false,true);inserted.setSource(0,16);eq(1,inserted.tick(),"insert into near half");BlockPos far=inserted.to[1].pos;Object attempt=call("transferAttempt",new Class<?>[]{Level.class,BlockPos.class,Container.class,BlockPos.class,Container.class},inserted.l,far,ItemRouting.containerAt(inserted.l,far),inserted.source,inserted.from[0]);try{Method allowed=attempt.getClass().getDeclaredMethod("allowed");allowed.setAccessible(true);ok(!(boolean)allowed.invoke(attempt),"return shield covers companion address");}catch(Exception e){throw new AssertionError(e);}
// AUTO sources captured before discovery must obey a later same-tick insertion.
l=new Level();l.assembleChests=true;l.load(p(8,8));root=p(8,8);l.states.put(root,auto);Box simple=new Box(l,p(7,8),1);simple.slots[0]=new ItemStack(GOODS,16);Box[]full=pair(l,p(9,8),Direction.SOUTH,false);for(Box box:full)box.fill(FILLER,64);full[1].slots[26]=ItemStack.EMPTY;
eq(1,ItemRouting.tickExtract(l,root,auto),"captured source does not bounce received item");eq(15,simple.total(),"AUTO no bounce debit");eq(1,full[1].slots[26].getCount(),"AUTO item remains delivered");clean(l);
// Filter's pass path must also avoid inserting into another view of its source.
l=new Level();l.assembleChests=true;l.load(p(8,8));BlockPos filter=p(8,8);BlockState fs=new BlockState(new ItemFilterBlock());l.states.put(filter,fs);var be=new com.gonzotech.machines.block.entity.ItemFilterBlockEntity(l,filter);own=pair(l,p(9,8),Direction.SOUTH,false);own[0].slots[0]=new ItemStack(GOODS,16);l.states.put(p(8,9),auto);ItemFilterRouting.tick(l,filter,fs,be);eq(16,sum(own),"filter self view no debit");eq(0,l.flow,"filter does not report self delivery");clean(l);
System.out.println("Double chest PASS: "+checks+" assertions; fresh54 views + actual whole routers, not native Minecraft");
}}
'''

def check(source=None,filter_source=None):
    return run(source=source,filter_source=filter_source,extra_files={'com/gonzotech/machines/network/ChestProbe.java':PROBE},main_class='com.gonzotech.machines.network.ChestProbe')

if __name__=='__main__':
    text=SOURCE.read_text();result=check();assert result.returncode==0,result.stdout+result.stderr;print(result.stdout.strip())
    fixtures=ROOT/'audit/fixtures/double_chest_routing';record=json.loads((fixtures/'manifest.json').read_text());data=(fixtures/record['file']).read_bytes()
    assert len(data)==record['bytes'] and hashlib.sha256(data).hexdigest()==record['sha256']
    assert subprocess.check_output(['git','show',f"{record['source_commit']}:{record['source']}"],cwd=ROOT)==data
    fr=record['filter'];fd=(fixtures/fr['file']).read_bytes();assert len(fd)==fr['bytes'] and hashlib.sha256(fd).hexdigest()==fr['sha256'];assert subprocess.check_output(['git','show',f"{fr['source_commit']}:{fr['source']}"],cwd=ROOT)==fd
    old=check(data.decode(),fd.decode());assert old.returncode!=0 and 'AssertionError' in old.stderr,'old325 must fail assertion, not compiler'
    print('REJECTED: exact original325 (Git/hash/bytes), fresh-wrapper destination')
    if '--mutation-check' in sys.argv:
        mutants=[
            ('wrapper-identity','return sameContainerAt(level, sink.containerPos(), sink.container());','return containerAt(level, sink.containerPos()) == sink.container;'),
            ('coordinates-only','return previous.contains(first) && previous.contains(second)\n            && current.contains(first) && current.contains(second);','return true;'),
            ('only-near-owner','previous.contains(first) && previous.contains(second)','previous.contains(first)'),
            ('only-far-owner','previous.contains(first) && previous.contains(second)','previous.contains(second)'),
            ('captured-source-owner','if (!sameContainerAt(level, excludeSrc, src)) {','if (!containerLookupLoaded(level, excludeSrc)) {'),
            ('companion-identity','return List.of(ItemTransferGuard.Owner.block(pos.asLong()), ItemTransferGuard.Owner.block(other.asLong()));','return List.of(ItemTransferGuard.Owner.block(pos.asLong()));'),
            ('accepted-history','guard.record(now, sourceOwners, targetOwners);',''),
        ]
        for name,old,new in mutants:
            assert text.count(old)==1,(name,old)
            changed=text.replace(old,new)
            # Owner membership is now independently enforced again by history-key
            # resolution. Exercise both layers for these deliberate unsafe variants.
            if name in ('coordinates-only','captured-source-owner'):
                changed=changed.replace(' || !compound.contains(first) || !compound.contains(second)','')
            elif name=='only-near-owner':changed=changed.replace(' || !compound.contains(second)','')
            elif name=='only-far-owner':changed=changed.replace(' || !compound.contains(first)','')
            result=check(changed);assert result.returncode!=0 and 'AssertionError' in result.stderr,'mutation survived: '+name+'\n'+result.stdout+result.stderr;print('REJECTED:',name)

        print('Mutation sensitivity: 7/7 assertion-killed (compiler failures never count)')
