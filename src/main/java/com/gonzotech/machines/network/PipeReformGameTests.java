package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gonzotech")
@PrefixGameTestTemplate(false)
public final class PipeReformGameTests {
    private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech", id)); }
    private static PipeNetworkService.Plan ready(GameTestHelper h, BlockPos root, PipeType type) {
        for (int i = 0; i < 32; i++) {
            PipeNetworkService.beginTick();
            var plan = PipeNetworkService.request(h.getLevel(), root, type, type == PipeType.ITEM ? PipeNetworkService.Kind.ITEM : PipeNetworkService.Kind.RESOURCE, p -> false, p -> false);
            PipeNetworkService.endTick(); if (plan != null) return plan;
        }
        throw new AssertionError("small native network did not finish");
    }
    @GameTest(template = "radiation_empty") public static void pipeWholeNodeCapAndConservation(GameTestHelper h) {
        var level = h.getLevel(); var root = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(root, block("first_item_node").defaultBlockState(), 3);
        var a = root.west(); var b = root.north(); var c = root.east();
        for (var pos : new BlockPos[]{a,b,c}) level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        Container fromA = (Container)level.getBlockEntity(a), fromB = (Container)level.getBlockEntity(b), to = (Container)level.getBlockEntity(c);
        fromA.setItem(0,new ItemStack(Items.DIAMOND,64)); fromB.setItem(0,new ItemStack(Items.DIAMOND,64));
        ready(h, root, PipeType.ITEM);
        int moved = ItemRouting.tickExtract(level, root, level.getBlockState(root));
        h.assertTrue(moved == 1,"whole node shares one per-item allowance across both source faces");
        h.assertTrue(ItemRouting.tickExtract(level, root, level.getBlockState(root)) == 0,"same tick cannot reset allowance");
        int sum = 0; for (Container inv : new Container[]{fromA,fromB,to}) for(int s=0;s<inv.getContainerSize();s++)sum+=inv.getItem(s).getCount();
        h.assertTrue(sum == 128,"native inventory sum conserved"); h.succeed();
    }
    @GameTest(template = "radiation_empty") public static void pipeResourceBridgeInvalidatesBeforeTransfer(GameTestHelper h) {
        var level = h.getLevel(); var source = h.absolutePos(new BlockPos(1,2,3));
        var entry=source.east(); var middle=entry.east(); var last=middle.east(); var target=last.east();
        level.setBlock(source,Blocks.BARREL.defaultBlockState(),3);level.setBlock(target,Blocks.BARREL.defaultBlockState(),3);
        for(var pos:new BlockPos[]{entry,middle,last}) level.setBlock(pos,block("first_wire").defaultBlockState().setValue(PipeBlock.AXIS,Direction.Axis.X),3);
        var plan=ready(h,entry,PipeType.WIRE); long[] accepted={0};
        long taken=PipeRouting.drain(level,source,PipeType.WIRE,10000,0,(be,pos)->pos.equals(target)?(amount,simulate)->{if(!simulate)accepted[0]+=amount;return amount;}:null);
        h.assertTrue(accepted[0]>0 && taken>=accepted[0] && taken<=10000,"native delivery plus path loss is paid by source");
        level.setBlock(middle,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!plan.current(),"real LevelChunk mixin revokes bridge lease immediately");
        long before=accepted[0];taken=PipeRouting.drain(level,source,PipeType.WIRE,10000,0,(be,pos)->pos.equals(target)?(amount,simulate)->{if(!simulate)accepted[0]+=amount;return amount;}:null);
        h.assertTrue(taken==0 && accepted[0]==before,"severed native route cannot transfer even in the same tick"); h.succeed();
    }
    @GameTest(template = "radiation_empty") public static void pipeEmptyLeafKeepsProductionPlan(GameTestHelper h) {
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(3,2,3));
        level.setBlock(root,block("first_item_node").defaultBlockState(),3);
        var plan=ready(h,root,PipeType.ITEM);var leaf=root.east();
        level.setBlock(leaf,block("first_item_pipe").defaultBlockState().setValue(PipeBlock.AXIS,Direction.Axis.X),3);
        h.assertTrue(plan.current()&&plan.visited()==2,"empty native leaf is appended without full invalidation");
        level.setBlock(leaf,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(plan.current()&&plan.visited()==1,"empty native leaf removed locally");h.succeed();
    }
}
