package net.minecraft.world.level.block.state;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
/**
 * Стаб state machine (сигнатуры сверены с MCP-зеркалом 1.21.4: simpleCodec —
 * static на BlockBehaviour, Properties вложен сюда).
 */
public abstract class BlockBehaviour {
    public static <B extends Block> MapCodec<B> simpleCodec(Function<BlockBehaviour.Properties, B> factory) {
        throw new UnsupportedOperationException("stub");
    }

    protected final StateDefinition<Block, BlockState> stateDefinition = null;

    protected MapCodec<? extends Block> codec() { return null; }

    protected void registerDefaultState(BlockState state) { }
    public BlockState defaultBlockState() { return null; }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { }

    protected BlockState getStateForPlacement(BlockPlaceContext context) { return null; }
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) { }
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) { }
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) { return null; }
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) { return null; }
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) { return null; }
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return null; }
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return null; }
    protected FluidState getFluidState(BlockState state) { return null; }
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { }
    protected boolean hasAnalogOutputSignal(BlockState state) { return false; }
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return 0; }

    public static class Properties {
        public Properties() { }
        public Properties noCollission() { return this; }
        public Properties strength(float hardness) { return this; }
        public Properties strength(float hardness, float resistance) { return this; }
        public Properties lightLevel(java.util.function.ToIntFunction<BlockState> light) { return this; }
        public Properties requiresCorrectToolForDrops() { return this; }
        public Properties sound(net.minecraft.world.level.block.SoundType type) { return this; }
        public Properties randomTicks() { return this; }
    }
}
