package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Универсальная жидкостная труба — несёт ВОДУ и ПАР одновременно как два
 * независимых потока в одном блоке. В отличие от одиночной трубы фиксированного
 * типа ({@link PipeBlock}), она {@link #carries} истинна и для {@link PipeType#WATER},
 * и для {@link PipeType#STEAM}, поэтому стыкуется и с водными, и с паровыми
 * трубами/узлами/машинами.
 * <p>
 * <b>Общий бюджет.</b> Вода и пар делят суммарную пропускную способность за тик
 * ({@link com.gonzotech.machines.energy.MachineDefs#UNIVERSAL_FLUID_OUTPUT} =
 * 800 mB/t) — учёт ведёт {@link FluidBudgetLedger} в {@link PipeRouting}. Пар в
 * котёл она по-прежнему не пихает: это решает сама машина-приёмник (котёл — не
 * {@code SteamSink}).
 * <p>
 * Геометрически труба живёт в общем FLUID-углу сечения (как водная/паровая), так
 * что физически совпадает с ними. Тип-параметр базового {@link PipeBlock} —
 * {@link PipeType#WATER} (нужен лишь как «якорь» для геометрии/кодека); реальная
 * принадлежность к обоим типам задаётся переопределениями ниже.
 */
public class UniversalFluidPipeBlock extends PipeBlock {

    public UniversalFluidPipeBlock(Properties properties) {
        super(properties, PipeType.WATER);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(UniversalFluidPipeBlock::new);
    }

    /** Несёт всё жидкостное семейство (сейчас — вода и пар). */
    @Override
    public boolean carries(BlockState state, PipeType type) {
        return type.isFluid();
    }

    @Override
    public boolean opensToward(BlockState state, PipeType type, Direction dir) {
        if (!type.isFluid()) return false;
        if (connectsAllSides()) return true;
        return dir.getAxis() == state.getValue(AXIS);
    }

    // ─────────────────────────── гаечный ключ ───────────────────────────

    /**
     * Универсальную трубу нельзя собрать в связку (её FLUID-угол уже занят двумя
     * ресурсами) — ключом только прокручиваем режим. Composite-логику базового
     * класса перебиваем.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            if (!level.isClientSide()) {
                PipeMode nextMode = state.getValue(MODE).next();
                level.setBlock(pos, state.setValue(MODE, nextMode), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }

        // Энергетическая труба (провод/теплотруба) в руке → собрать связку: FLUID-
        // угол занимает эта универсальная труба (вода+пар), в свободный угол ложится
        // добавляемый тип. Узлы и жидк.трубы не подходят. Только у обычной (не-узел).
        if (!connectsAllSides()) {
            PipeType adding = CompositePipeBlock.pipeTypeOf(stack);
            if (adding != null && !adding.isFluid() && ModCompositeAccess.get() != null) {
                if (!level.isClientSide()) {
                    Direction.Axis axis = state.getValue(AXIS);
                    BlockState composite = ModCompositeAccess.get().defaultBlockState()
                        .setValue(AXIS, axis)
                        .setValue(CompositePipeBlock.WATERLOGGED, state.getValue(WATERLOGGED))
                        .setValue(CompositePipeBlock.PRESENT.get(adding), true)
                        .setValue(CompositePipeBlock.MODE.get(adding), PipeMode.AUTO);
                    composite = CompositePipeBlock.withUniversalFluid(composite);
                    level.setBlock(pos, composite, Block.UPDATE_ALL);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
