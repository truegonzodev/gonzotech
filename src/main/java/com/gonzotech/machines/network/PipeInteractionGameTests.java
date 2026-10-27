package com.gonzotech.machines.network;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@GameTestHolder("gonzotech")
@PrefixGameTestTemplate(false)
public final class PipeInteractionGameTests {
    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech",id));
    }
    private static void use(GameTestHelper h, BlockPos pos, Player player, ItemStack held) throws Exception {
        BlockState state=h.getLevel().getBlockState(pos);
        Class<?> owner=state.getBlock() instanceof CompositePipeBlock ? CompositePipeBlock.class
                : state.getBlock() instanceof UniversalFluidPipeBlock ? UniversalFluidPipeBlock.class : PipeBlock.class;
        var method=owner.getDeclaredMethod("useItemOn",ItemStack.class,BlockState.class,Level.class,BlockPos.class,
                Player.class,InteractionHand.class,BlockHitResult.class);
        method.setAccessible(true);
        player.setItemInHand(InteractionHand.MAIN_HAND,held);
        method.invoke(state.getBlock(),held,state,h.getLevel(),pos,player,InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()+0.75,pos.getZ()+0.25),Direction.UP,pos,false));
    }
    private static void mastery(Player player) {
        player.getData(com.gonzotech.chalkboard.progress.ModAttachments.CHALKBOARD_PROGRESS.get())
                .unlockNoteFlag(com.gonzotech.chalkboard.notes.ScholarNoteFlags.WRENCH_EATEN);
    }
    @GameTest(template="radiation_empty") public static void masteryBundlePriority(GameTestHelper h) throws Exception {
        BlockPos pos=h.absolutePos(new BlockPos(1,2,1));
        for(String tier:new String[]{"first","second","third"}) for(boolean mastered:new boolean[]{false,true})
            for(boolean crouching:new boolean[]{false,true}) {
                Player player=h.makeMockPlayer(GameType.SURVIVAL);
                if(mastered)mastery(player);
                player.setShiftKeyDown(crouching);
                h.getLevel().setBlock(pos,block(tier+"_wire").defaultBlockState()
                        .setValue(PipeBlock.AXIS,Direction.Axis.X).setValue(PipeBlock.MODE,PipeMode.AUTO.next()),3);
                ItemStack held=new ItemStack(block(tier+"_heat_pipe"),2);
                use(h,pos,player,held);
                BlockState result=h.getLevel().getBlockState(pos);
                h.assertTrue(result.getBlock() instanceof CompositePipeBlock,"wire+heat becomes bundle even with mastery");
                h.assertTrue(result.getValue(CompositePipeBlock.PRESENT.get(PipeType.WIRE))
                        && result.getValue(CompositePipeBlock.PRESENT.get(PipeType.HEAT)),"both parts retained");
                h.assertTrue(result.getValue(CompositePipeBlock.MODE.get(PipeType.WIRE))==PipeMode.AUTO.next(),"old mode preserved");
                h.assertTrue(held.getCount()==1,"one added pipe consumed");
                held=new ItemStack(block(tier+"_universal_fluid_pipe"),2);use(h,pos,player,held);
                result=h.getLevel().getBlockState(pos);
                h.assertTrue(CompositePipeBlock.carriesUniversalFluid(result),"universal fluid added before mastery");
                h.assertTrue(held.getCount()==1,"one universal pipe consumed");
                held=new ItemStack(block(tier+"_item_pipe"),2);use(h,pos,player,held);
                h.assertTrue(h.getLevel().getBlockState(pos).getValue(CompositePipeBlock.PRESENT.get(PipeType.ITEM)),"ordinary extension before mastery");
                h.assertTrue(held.getCount()==1,"one item pipe consumed");
                h.getLevel().setBlock(pos,block(tier+"_universal_fluid_pipe").defaultBlockState(),3);
                held=new ItemStack(block(tier+"_wire"),2);use(h,pos,player,held);
                result=h.getLevel().getBlockState(pos);
                h.assertTrue(result.getBlock() instanceof CompositePipeBlock && CompositePipeBlock.carriesUniversalFluid(result),"universal base bundles before mastery");
                h.assertTrue(held.getCount()==1,"universal base consumes one added pipe");
            }
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void masteryFallbackAndRotation(GameTestHelper h) throws Exception {
        BlockPos pos=h.absolutePos(new BlockPos(1,2,1));
        Player player=h.makeMockPlayer(GameType.SURVIVAL);mastery(player);
        h.getLevel().setBlock(pos,block("first_wire").defaultBlockState(),3);
        ItemStack wrongTier=new ItemStack(block("second_heat_pipe"),2);
        use(h,pos,player,wrongTier);
        h.assertTrue(h.getLevel().getBlockState(pos).is(block("first_wire")),"wrong tier cannot bundle");
        h.assertTrue(wrongTier.getCount()==2,"wrong tier not consumed");
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(PipeBlock.MODE)==PipeMode.AUTO.next(),"mastery mode fallback retained");
        var initial=block("composite_pipe").defaultBlockState().setValue(CompositePipeBlock.AXIS,Direction.Axis.X)
                .setValue(CompositePipeBlock.AXIS_LOWER,Direction.Axis.X)
                .setValue(CompositePipeBlock.PRESENT.get(PipeType.WIRE),true)
                .setValue(CompositePipeBlock.PRESENT.get(PipeType.HEAT),true);
        h.getLevel().setBlock(pos,initial,3);player.setShiftKeyDown(true);
        use(h,pos,player,ItemStack.EMPTY);
        var rotated=h.getLevel().getBlockState(pos);
        h.assertTrue(rotated.getValue(CompositePipeBlock.AXIS)==Direction.Axis.Z,"crouch empty hand rotates selected layer");
        h.assertTrue(rotated.getValue(CompositePipeBlock.AXIS_LOWER)==Direction.Axis.X,"other layer unchanged");
        player.setShiftKeyDown(false);h.getLevel().setBlock(pos,initial,3);
        use(h,pos,player,ItemStack.EMPTY);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(CompositePipeBlock.MODE.get(PipeType.WIRE))==PipeMode.AUTO.next(),"empty hand mastery changes mode");
        h.succeed();
    }
}
