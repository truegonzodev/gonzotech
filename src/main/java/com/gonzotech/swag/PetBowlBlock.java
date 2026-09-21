package com.gonzotech.swag;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Миска для питомцев (автор, 2026-09-19 — «ЖИРНЫЕ КОТЫ УРА»).
 *
 * <ul>
 *   <li>один инвентарный слот (стопка еды до 64); приёмная еда — тег
 *       {@code gonzotech:pet_bowl_food} (= #minecraft:cat_food + #minecraft:wolf_food);</li>
 *   <li>ПКМ едой — положить; ПКМ пустой рукой в сиде — забрать всё;</li>
 *   <li>свойство {@link #HAS_FOOD} переключает модель «с кормом» (коты/волки
 *       видят миску через {@link PetBowlBlockEntity#findNearestBowl});</li>
 *   <li>лут: сама миска + содержимое (сплошная миска разбивается целиком).</li>
 * </ul>
 */
public class PetBowlBlock extends BaseEntityBlock {

    public static final MapCodec<PetBowlBlock> CODEC = simpleCodec(PetBowlBlock::new);
    public static final BooleanProperty HAS_FOOD = BooleanProperty.create("has_food");

    /** Низкая миска 12×4×12. */
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);

    public PetBowlBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HAS_FOOD, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetBowlBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_FOOD);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PetBowlBlockEntity bowl && PetBowlBlockEntity.isPetFood(stack)) {
            if (!level.isClientSide() && bowl.insertFood(stack)) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof PetBowlBlockEntity bowl
            && bowl.hasFood()) {
            if (!level.isClientSide()) {
                bowl.extractAll(player);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        // Содержимое миски падает отдельными стаками (clustered drop, без NBT-копирования).
        BlockEntity be = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof PetBowlBlockEntity bowl && bowl.hasFood()) {
            drops.add(bowl.peekFood().copy());
        }
        return drops;
    }
}
