package com.gonzotech.core.item;

import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ведро с едким летучим химикатом (этилен).
 * Обычное ведро разъедается за 180 тиков (9 секунд) в любом месте:
 * в инвентаре, в станке или на земле, превращаясь в «дырявое ведро».
 * При растворении ведра взрывается внутри игрока/механизма (сила 1, большой эмиттер,
 * 20-40 sneeze-партиклов, отравление II на 10 сек и 0.3 Tx заражения).
 * При клике ПКМ ведром по блоку взрывается в точке клика в пределах interaction range.
 */
public class CorrosiveBucketItem extends Item {

    public static final int LIFETIME_TICKS = 180;
    private static final String TAG_LEAK_AT = "leak_at";

    public CorrosiveBucketItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void initLeakAt(ItemStack stack, long gameTime) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putLong(TAG_LEAK_AT, gameTime + LIFETIME_TICKS);
        }));
    }

    public static long getLeakAt(ItemStack stack, long gameTime) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            net.minecraft.nbt.CompoundTag tag = data.copyTag();
            if (tag.contains(TAG_LEAK_AT)) {
                return tag.getLong(TAG_LEAK_AT);
            }
        }
        long leakAt = gameTime + LIFETIME_TICKS;
        initLeakAt(stack, gameTime);
        return leakAt;
    }

    public static boolean isExpired(ItemStack stack, long gameTime) {
        return gameTime >= getLeakAt(stack, gameTime);
    }

    public static void triggerEthyleneExplosion(Level level, double x, double y, double z, @Nullable Entity source) {
        if (level.isClientSide) return;
        if (level instanceof ServerLevel serverLevel) {
            // Большой партикл эмиттера
            serverLevel.sendParticles(ModParticles.ETHYLEN_EXPLOSION_EMITTER.get(), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            // 20-40 партиклов "sneeze" в радиусе 1 (3x3x3)
            int sneezeCount = 20 + serverLevel.random.nextInt(21);
            serverLevel.sendParticles(ParticleTypes.SNEEZE, x, y, z, sneezeCount, 0.8, 0.8, 0.8, 0.05);

            // Игроки в зоне поражения взрыва (радиус 3.5 блока)
            AABB blastZone = new AABB(x - 3.5, y - 3.5, z - 3.5, x + 3.5, y + 3.5, z + 3.5);
            List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class, blastZone);
            for (ServerPlayer player : players) {
                // Отравление II на 10 сек (200 тиков, amplifier 1)
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
                // Доза химического заражения 0.3 Tx = 300_000_000 nTx
                PsycheChemical.addDoseToxicity(player, 300_000_000.0);
            }
        }
        // Сила взрыва 1 (мало урона и разрушений)
        level.explode(source, x, y, z, 1.0F, Level.ExplosionInteraction.BLOCK);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        Vec3 clickPos = context.getClickLocation();

        if (player != null) {
            double maxRange = player.blockInteractionRange();
            if (player.distanceToSqr(clickPos) > (maxRange + 1.0) * (maxRange + 1.0)) {
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            triggerEthyleneExplosion(level, clickPos.x, clickPos.y, clickPos.z, player);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
                ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get());
                if (!player.getInventory().add(leaky)) {
                    player.drop(leaky, false);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        long gameTime = level.getGameTime();
        if (isExpired(stack, gameTime)) {
            ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get(), stack.getCount());
            if (entity instanceof Player player) {
                player.getInventory().setItem(slotId, leaky);
                triggerEthyleneExplosion(level, player.getX(), player.getY() + 0.5, player.getZ(), player);
            }
        }
    }
}
