package com.gonzotech.space;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * ГРАВИТАЦИЯ И ПОЛЁТ В КОСМИЧЕСКИХ МИРАХ.
 *
 * <p><b>Ситуация A (миры с поверхностью — Луна, Марс, Европа):</b>
 * Пониженная гравитация через ванильные атрибуты ({@link Attributes#GRAVITY})
 * и пропорциональная компенсация высоты падения ({@link Attributes#SAFE_FALL_DISTANCE}):
 * <ul>
 *   <li>Луна: {@code 0.030} (земная 0.080)</li>
 *   <li>Марс: {@code 0.045}</li>
 *   <li>Европа: {@code 0.025}</li>
 * </ul>
 *
 * <p><b>Ситуация B (пустотные миры — орбиты Солнца/Альфы Центавра, открытый космос):</b>
 * <ul>
 *   <li>Полная невесомость: {@code gravity = 0.0} (дельта −0.080)</li>
 *   <li>Безопасная высота падения: {@code +1000} блоков (урон от падения отключён)</li>
 *   <li>Медленный космический полёт в выживании/хардкоре/приключении:
 *       {@code mayfly = true}, {@code flyingSpeed = 0.007F} (в ~7 раз медленнее ванильного 0.05F
 *       для эффекта дрейфа и маневровых движителей в невесомости)</li>
 *   <li>Креатив и спектатор не изменяются (полная ванильная скорость)</li>
 * </ul>
 *
 * <p>При выходе из пустотных миров режим полёта и скорость автоматически возвращаются в ваниль.
 */
public final class SpaceGravity {

    private SpaceGravity() {
    }

    /** Ванильная база гравитации (блок/тик²). */
    private static final double BASE_GRAVITY = 0.08;
    /** Ванильная база безопасной высоты падения (блоки). */
    private static final double BASE_SAFE_FALL = 3.0;
    /** Ванильная базовая скорость полёта. */
    private static final float VANILLA_FLYING_SPEED = 0.05F;
    /** Очень медленная скорость маневрирования в невесомости (дрейф). */
    private static final float ZERO_G_FLYING_SPEED = 0.007F;

    /** Стабильные id модификаторов (lowercase). */
    private static final ResourceLocation GRAVITY_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "space_gravity");
    private static final ResourceLocation SAFE_FALL_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "space_safe_fall");

    /** Проверка, является ли измерение пустотным космическим миром (Ситуация B). */
    public static boolean isVoidSpace(ResourceKey<Level> dim) {
        return dim == SpaceDimensions.SOLAR_ORBIT
            || dim == SpaceDimensions.ALPHA_CENTAURI_ORBIT
            || dim == SpaceDimensions.DEEP_SPACE;
    }

    /**
     * Целевая гравитация мира или {@code -1}, если мир не наш (физика ванильная).
     */
    private static double targetGravity(ResourceKey<Level> dim) {
        if (dim == SpaceDimensions.MOON) return 0.030;
        if (dim == SpaceDimensions.MARS) return 0.045;
        if (dim == SpaceDimensions.EUROPA) return 0.025;
        if (isVoidSpace(dim)) return 0.0;
        return -1.0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // Только сервер — атрибуты и способности синхронизируются на клиент.
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ResourceKey<Level> dim = serverPlayer.level().dimension();
        double target = targetGravity(dim);

        if (target < 0) {
            // Не наш мир — снять оба модификатора, если висят.
            removeModifier(serverPlayer, Attributes.GRAVITY, GRAVITY_ID);
            removeModifier(serverPlayer, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
            restoreSurvivalFlight(serverPlayer);
            return;
        }

        // 1. ГРАВИТАЦИЯ
        double gravityDelta = target - BASE_GRAVITY;
        ensureModifier(serverPlayer, Attributes.GRAVITY, GRAVITY_ID, gravityDelta);

        // 2. БЕЗОПАСНАЯ ВЫСОТА ПАДЕНИЯ
        if (isVoidSpace(dim)) {
            ensureModifier(serverPlayer, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, 1000.0);
            applyVoidSpaceFlight(serverPlayer);
        } else {
            double ratio = target / BASE_GRAVITY; // <1 в космосе
            double safeFall = BASE_SAFE_FALL / Math.max(0.05, ratio);
            double safeFallDelta = safeFall - BASE_SAFE_FALL;
            ensureModifier(serverPlayer, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, safeFallDelta);
            restoreSurvivalFlight(serverPlayer);
        }
    }

    /** Включить медленный космический полёт в выживании/приключении в невесомости. */
    private static void applyVoidSpaceFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return; // Креатив и спектатор не трогаем
        }

        boolean changed = false;
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            changed = true;
        }
        if (Math.abs(player.getAbilities().getFlyingSpeed() - ZERO_G_FLYING_SPEED) > 1.0E-4F) {
            player.getAbilities().setFlyingSpeed(ZERO_G_FLYING_SPEED);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    /** Вернуть стандартные параметры способностей вне пустотного космоса. */
    private static void restoreSurvivalFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        boolean changed = false;
        if (player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            changed = true;
        }
        if (Math.abs(player.getAbilities().getFlyingSpeed() - VANILLA_FLYING_SPEED) > 1.0E-4F) {
            player.getAbilities().setFlyingSpeed(VANILLA_FLYING_SPEED);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    /**
     * Гарантирует, что на атрибуте висит транзитный ADD_VALUE-модификатор с
     * данным id и значением.
     */
    private static void ensureModifier(Player player, Holder<Attribute> attr,
                                       ResourceLocation id, double amount) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (existing != null && Math.abs(existing.amount() - amount) < 1.0E-6) {
            return; // уже правильное значение
        }
        inst.addOrUpdateTransientModifier(new AttributeModifier(
            id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeModifier(Player player, Holder<Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null && inst.getModifier(id) != null) {
            inst.removeModifier(id);
        }
    }
}
