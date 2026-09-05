package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Блок-узел — по сути та же труба, но с ОТКРЫТЫМИ 6 гранями: соединяется со
 * всеми соседями своего типа (и машинами) в любую сторону. Это точка ветвления,
 * уголков и тройников для труб {@link PipeBlock} (у обычной трубы только два
 * конца по оси).
 * <p>
 * Логику передачи менять не нужно: маршрутизатор {@link PipeRouting} и так
 * обходит связную цепь и делит слив РАВНОМЕРНО между всеми достижимыми
 * приёмниками. Узел лишь «открывает» все грани — а равномерность даёт ожидаемое
 * поведение вида {@code (X+Y+Z)/3}, когда несколько источников через узлы кормят
 * несколько приёмников.
 * <p>
 * В отличие от трубы, форма узла не зависит от оси — это компактный центральный
 * кубик (грани-«штуцеры» дорисовывает модель/текстура). Свойство {@code AXIS}
 * наследуется, но на соединения и форму не влияет.
 */
public class NodeBlock extends PipeBlock {

    // Центральный кубик 8×8×8 — узел одинаков во всех ориентациях.
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

    public NodeBlock(Properties properties, PipeType pipeType) {
        super(properties, pipeType);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(props -> new NodeBlock(props, pipeType()));
    }

    @Override
    public boolean connectsAllSides() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Ось для узла роли не играет — оставляем дефолт (Y), режим AUTO.
        return this.defaultBlockState().setValue(MODE, PipeMode.AUTO);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
