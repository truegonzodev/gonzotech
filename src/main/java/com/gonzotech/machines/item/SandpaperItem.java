package com.gonzotech.machines.item;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemAbilities;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * «Наждачкис» (0.3.253, автор) — бесконечный абразивный компонент:
 * ПКМ по брёвнам обтёсывает их (способность топора — работают и модовые
 * брёвна), ПКМ по блокам с polished-версией полирует её. Без прочности и
 * расходников — сам предмет крафтится один раз из корундового абразива.
 */
public class SandpaperItem extends Item {

    /** Полировка: у polished-блоков нет ванильной ItemAbility — свой справочник. */
    private static final Map<Block, Block> POLISH = new HashMap<>();

    static {
        POLISH.put(Blocks.ANDESITE, Blocks.POLISHED_ANDESITE);
        POLISH.put(Blocks.DIORITE, Blocks.POLISHED_DIORITE);
        POLISH.put(Blocks.GRANITE, Blocks.POLISHED_GRANITE);
        POLISH.put(Blocks.TUFF, Blocks.POLISHED_TUFF);
        POLISH.put(Blocks.DEEPSLATE, Blocks.POLISHED_DEEPSLATE);
        POLISH.put(Blocks.BASALT, Blocks.POLISHED_BASALT);
        POLISH.put(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE);
        // Мох гонзо (обновление недр 0.3.19): шлифованный — отдельный блок.
        POLISH.put(ModBlocks.NOBLE_MOSS.get(), ModBlocks.POLISHED_NOBLE_MOSS.get());
        POLISH.put(ModBlocks.SMOOTH_NOBLE_MOSS.get(), ModBlocks.POLISHED_NOBLE_MOSS.get());
    }

    public SandpaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        // 1) Обтёсывание брёвен — через способность топора (модовые брёвна тоже).
        BlockState stripped = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
        if (stripped != null) {
            if (!level.isClientSide()) {
                level.setBlock(pos, stripped, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
                if (context.getPlayer() != null) context.getPlayer().swing(context.getHand());
            }
            return InteractionResult.SUCCESS;
        }

        // 2) Полировка известных пар.
        Block polished = POLISH.get(state.getBlock());
        if (polished != null) {
            if (!level.isClientSide()) {
                level.setBlock(pos, polished.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.4F);
                if (context.getPlayer() != null) context.getPlayer().swing(context.getHand());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
