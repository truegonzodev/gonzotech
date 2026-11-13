package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gonzotech")
@PrefixGameTestTemplate(false)
public final class PipePersistenceGameTests {
    private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech", id)); }

    @SuppressWarnings("unchecked")
    @GameTest(template="radiation_empty")
    public static void universalClumpNbtRoundTripAndRecovery(GameTestHelper h) throws Exception {
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(3,2,3));
        for (String tier : new String[]{"first","second","third"}) {
            var state=block(tier+"_universal_node").defaultBlockState();
            for(int i=0;i<30;i++)level.setBlock(root.offset(0,i,0),state,3);
            h.assertTrue(NodeClumpIndex.sizeAt(level,root)==30,"native universal group built: "+tier);
            var tag=NodeClumpSavedData.get(level).save(new CompoundTag(),level.registryAccess());
            var load=NodeClumpSavedData.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
            var decoded=load.invoke(null,tag);
            var field=NodeClumpSavedData.class.getDeclaredField("clumps");field.setAccessible(true);
            var records=(java.util.List<NodeClumpIndex.ClumpSave>)field.get(decoded);
            h.assertTrue(records.stream().anyMatch(r->java.util.Arrays.stream(r.members()).anyMatch(p->p==root.asLong())&&r.members().length==30),"NBT retains universal30: "+tier);
            for(int i=0;i<30;i++)NodeClumpIndex.onNodeRemoved(level,root.offset(0,i,0),state);
            h.assertTrue(NodeClumpIndex.sizeAt(level,root)==0,"runtime records removed without editing world");
            NodeClumpIndex.restoreAll(level,records);
            for(int i=0;i<30;i++)h.assertTrue(NodeClumpIndex.sizeAt(level,root.offset(0,i,0))==30,"NBT restores each member");
            if (tier.equals("second")) {
                for(int i=0;i<30;i++)NodeClumpIndex.onNodeRemoved(level,root.offset(0,i,0),state);
                ((UniversalNodeBlock)state.getBlock()).tick(state,level,root,level.random);
                h.assertTrue(NodeClumpIndex.sizeAt(level,root)==30,"scheduled tick recovers old omitted group without block edit");
            }
        }
        h.succeed();
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech",id)));
    }
    @GameTest(template="radiation_empty")
    public static void thirdGrinderRecipeLoadsAndRequiresSecond(GameTestHelper h) {
        var id=ResourceLocation.fromNamespaceAndPath("gonzotech","third_grinder");
        var holder=h.getLevel().getServer().getRecipeManager().getRecipes().stream().filter(r->r.id().location().equals(id)).findFirst();
        h.assertTrue(holder.isPresent(),"third_grinder is actually loaded by native RecipeManager");
        h.assertTrue(holder.get().value() instanceof CraftingRecipe,"crafting recipe type");
        var recipe=(CraftingRecipe)holder.get().value();
        var stacks=new java.util.ArrayList<ItemStack>();
        for(String name:new String[]{"stainless_steel_plate","aluminum_housing","stainless_steel_plate","stainless_steel_plate","second_grinder","stainless_steel_plate","stainless_steel_plate","sandpapy","stainless_steel_plate"}) stacks.add(item(name));
        var input=CraftingInput.of(3,3,stacks);
        h.assertTrue(recipe.matches(input,h.getLevel()),"approved PHP/PGP/PSP pattern matches");
        var output=recipe.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(output.is(item("third_grinder").getItem())&&output.getCount()==1,"one grinder III result");
        stacks.set(4,ItemStack.EMPTY);
        h.assertTrue(!recipe.matches(CraftingInput.of(3,3,stacks),h.getLevel()),"missing grinder II cannot craft III");
        h.succeed();
    }
}
