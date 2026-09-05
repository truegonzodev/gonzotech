package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Составной блок труб — несколько ТИПОВ труб в одном кубе, каждый в своём
 * фиксированном углу сечения, БЕЗ соединения между собой. По сути «пучок»
 * обособленных труб (аналогия: {@code pink_petals}/горшок с несколькими
 * стеблями). Пассивен, как и одиночная труба: без BlockEntity, без тика.
 * <p>
 * <b>Фиксированные углы = детерминизм.</b> Тип всегда сидит в одном и том же
 * углу сечения ({@link #corner}). Поэтому «ток» одного блока стыкуется с «током»
 * соседнего сам собой (под-решётки выровнены by design), модель просто
 * аддитивная, а пустой угол служит игроку подсказкой «сюда влезет ещё труба».
 * <p>
 * <b>Шаг 1 (эта версия): общая ось.</b> Все типы в блоке смотрят вдоль одной оси
 * ({@code AXIS}). Независимые попарные направления (ток на север, жидкость вверх
 * и т.п.) — запланированный апгрейд (Шаг 2), потребует per-type оси, multipart-
 * моделей и валидации непересечения.
 * <p>
 * <b>Генерик по {@link PipeType}.</b> Свойства и логика строятся из
 * {@code PipeType.values()}. Сейчас реально участвуют WIRE + HEAT; когда добавим
 * FLUID/ITEM — они автоматически получат свой угол и заработают в связке без
 * переделок.
 */
public class CompositePipeBlock extends RotatedPillarBlock implements PipeCarrier {

    /** Присутствует ли тип в блоке. Ключ — {@link PipeType}. */
    public static final Map<PipeType, BooleanProperty> PRESENT = new EnumMap<>(PipeType.class);
    /** Режим (AUTO/PULL/PUSH) типа. Ключ — {@link PipeType}. */
    public static final Map<PipeType, EnumProperty<PipeMode>> MODE = new EnumMap<>(PipeType.class);

    static {
        for (PipeType t : PipeType.values()) {
            PRESENT.put(t, BooleanProperty.create("has_" + t.id()));
            MODE.put(t, EnumProperty.create("mode_" + t.id(), PipeMode.class));
        }
    }

    private final MapCodec<CompositePipeBlock> codec;

    public CompositePipeBlock(Properties properties) {
        super(properties);
        this.codec = simpleCodec(CompositePipeBlock::new);
        BlockState def = this.stateDefinition.any().setValue(AXIS, Direction.Axis.Z);
        for (PipeType t : PipeType.values()) {
            def = def.setValue(PRESENT.get(t), false).setValue(MODE.get(t), PipeMode.AUTO);
        }
        this.registerDefaultState(def);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
        for (PipeType t : PipeType.values()) {
            builder.add(PRESENT.get(t));
            builder.add(MODE.get(t));
        }
    }

    // ─────────────────────────── PipeCarrier ───────────────────────────

    @Override
    public boolean carries(BlockState state, PipeType type) {
        return state.getValue(PRESENT.get(type));
    }

    @Override
    public boolean opensToward(BlockState state, PipeType type, Direction dir) {
        if (!carries(state, type)) return false;
        // Шаг 1: общая ось — тип открыт двумя торцами вдоль оси блока.
        return dir.getAxis() == state.getValue(AXIS);
    }

    @Override
    public PipeMode modeFor(BlockState state, PipeType type) {
        return state.getValue(MODE.get(type));
    }

    // ─────────────────────────── форма (хитбокс) ───────────────────────────

    private static VoxelShape shapeFor(BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        VoxelShape shape = Shapes.empty();
        for (PipeType t : PipeType.values()) {
            if (!state.getValue(PRESENT.get(t))) continue;
            shape = Shapes.join(shape, PipeGeometry.cornerBox(axis, t), BooleanOp.OR);
        }
        return shape.isEmpty() ? Shapes.block() : shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    // ─────────────────────────── взаимодействие ───────────────────────────

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        // Ключ работает по КОНКРЕТНОЙ трубе пучка — той, куда наведён прицел.
        if (stack.getItem() instanceof WrenchItem) {
            PipeType part = partAt(state, pos, hit);
            if (part == null) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                if (player.isSecondaryUseActive()) {
                    // Shift+ПКМ — снять эту трубу из пучка (выпадает предметом).
                    removePart(level, pos, state, part, player);
                } else {
                    // ПКМ — прокрутить режим именно этой трубы.
                    PipeMode nextMode = state.getValue(MODE.get(part)).next();
                    level.setBlock(pos, state.setValue(MODE.get(part), nextMode), Block.UPDATE_ALL);
                    player.displayClientMessage(
                        Component.translatable("message.gonzotech.pipe_mode_part",
                            Component.translatable("block.gonzotech." + part.id()),
                            Component.translatable("message.gonzotech.pipe_mode_short." + nextMode.getSerializedName())),
                        true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Добавление ещё одной трубы в связку: используем предмет-трубу другого типа.
        PipeType adding = pipeTypeOf(stack);
        if (adding != null && !state.getValue(PRESENT.get(adding))) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(PRESENT.get(adding), true), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Снять одну трубу пучка. Последняя ушла → блок исчезает. */
    private void removePart(Level level, BlockPos pos, BlockState state, PipeType part, Player player) {
        if (!player.getAbilities().instabuild) {
            Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech", part.id()));
            if (item != Items.AIR) Block.popResource(level, pos, new ItemStack(item));
        }
        BlockState next = state.setValue(PRESENT.get(part), false);
        // Сколько типов осталось?
        int remaining = 0;
        PipeType lastLeft = null;
        for (PipeType t : PipeType.values()) {
            if (next.getValue(PRESENT.get(t))) {
                remaining++;
                lastLeft = t;
            }
        }
        if (remaining == 0) {
            level.removeBlock(pos, false);
        } else if (remaining == 1) {
            // Осталась одна труба — «схлопываем» пучок обратно в одиночный блок.
            PipeBlock single = ModCompositeAccess.singleOf(lastLeft);
            if (single != null) {
                BlockState singleState = single.defaultBlockState()
                    .setValue(PipeBlock.MODE, next.getValue(MODE.get(lastLeft)));
                // Совместить ось (у RotatedPillarBlock свойство AXIS общее).
                singleState = singleState.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    next.getValue(AXIS));
                level.setBlock(pos, singleState, Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, next, Block.UPDATE_ALL);
            }
        } else {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    /** Тип трубы пучка, в которую сейчас смотрит игрок (по точке наведения). */
    private static PipeType partAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        List<PipeType> present = new ArrayList<>();
        for (PipeType t : PipeType.values()) {
            if (state.getValue(PRESENT.get(t))) present.add(t);
        }
        if (present.isEmpty()) return null;
        return PipeGeometry.partAt(state.getValue(AXIS), pos, hit.getLocation(), present);
    }

    // ─────────────────────────── дроп компонентов ───────────────────────────

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // Дропаем по одной трубе каждого несомого типа (связку «разбираем»).
        List<ItemStack> drops = new ArrayList<>();
        for (PipeType t : PipeType.values()) {
            if (!state.getValue(PRESENT.get(t))) continue;
            Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech", t.id()));
            if (item != Items.AIR) drops.add(new ItemStack(item));
        }
        return drops;
    }

    /** {@link PipeType} предмета-трубы, или {@code null} если это не труба. */
    static PipeType pipeTypeOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof PipeBlock pipe) {
            return pipe.pipeType();
        }
        return null;
    }
}
