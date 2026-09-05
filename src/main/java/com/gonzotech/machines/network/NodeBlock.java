package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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
 * Форма — обычный ПОЛНЫЙ КУБ (не тонкий брусок трубы и не хитроструктура): узел
 * одинаков во всех ориентациях, все грани — одна и та же текстура. Поэтому здесь
 * НЕ переопределяются {@code getShape}/{@code getCollisionShape} — берётся
 * дефолтный куб {@link net.minecraft.world.level.block.Block}.
 */
public class NodeBlock extends PipeBlock {

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

    // Полный куб (перебиваем тонкую форму трубы из PipeBlock).
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
