package com.gonzotech.space;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * ГРАВИТАЦИЯ КОСМИЧЕСКИХ МИРОВ (ситуация A — миры с твёрдой землёй).
 *
 * <p>Меняет саму физику через ванильные атрибуты 1.20.5+, а НЕ через эффекты
 * прыгучести/медленного падения (те дают «игрушечную лунную походку» с
 * фиксированной высотой прыжка, мигающий значок в HUD и ломают воду/лаву/полёт).
 * Атрибут честно уменьшает ускорение падения: прыжок выше, падение медленнее,
 * инерция сохраняется, элитры/парашют работают корректно.
 *
 * <p>Два транзитных (не сохраняемых) модификатора вешаются на игрока по
 * измерению:
 * <ul>
 *   <li>{@link Attributes#GRAVITY} — задаём АБСОЛЮТНОЕ целевое ускорение через
 *       {@code ADD_VALUE} с дельтой {@code target − 0.08} (ванильная база 0.08).
 *       Луна {@code 0.03}, Марс {@code 0.045}, Европа {@code 0.025}.</li>
 *   <li>{@link Attributes#SAFE_FALL_DISTANCE} — поднимаем планку урона от
 *       падения. Урон в MC зависит от ВЫСОТЫ, а не от скорости: при низкой
 *       гравитации прыгаешь выше и без компенсации ловил бы урон при приземлении.
 *       Ставим планку ~пропорционально {@code 1/ratio} (во сколько выше прыжок).</li>
 * </ul>
 *
 * <p>Реконсиляция — раз в тик на игрока, но действие происходит ТОЛЬКО когда
 * текущее состояние не совпадает с целевым (сравниваем наличие/значение
 * модификатора), поэтому дешёвая. Вышел в оверворлд/иной мир — модификаторы
 * снимаются, физика ванильная.
 */
public final class SpaceGravity {

    private SpaceGravity() {
    }

    /** Ванильная база гравитации (блок/тик²). */
    private static final double BASE_GRAVITY = 0.08;
    /** Ванильная база безопасной высоты падения (блоки). */
    private static final double BASE_SAFE_FALL = 3.0;

    /** Стабильные id модификаторов (lowercase). */
    private static final ResourceLocation GRAVITY_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "space_gravity");
    private static final ResourceLocation SAFE_FALL_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "space_safe_fall");

    /**
     * Целевая гравитация мира или {@code -1}, если мир не наш (физика ванильная).
     */
    private static double targetGravity(ResourceKey<Level> dim) {
        if (dim == SpaceDimensions.MOON) return 0.030;
        if (dim == SpaceDimensions.MARS) return 0.045;
        if (dim == SpaceDimensions.EUROPA) return 0.025;
        return -1.0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // Только сервер — атрибуты синхронизируются на клиент сами.
        if (player.level().isClientSide()) return;

        double target = targetGravity(player.level().dimension());
        if (target < 0) {
            // Не наш мир — снять оба модификатора, если висят.
            removeModifier(player, Attributes.GRAVITY, GRAVITY_ID);
            removeModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
            return;
        }

        // Гравитация: дельта к базе (ADD_VALUE) — итог = 0.08 + delta = target.
        double gravityDelta = target - BASE_GRAVITY;
        ensureModifier(player, Attributes.GRAVITY, GRAVITY_ID, gravityDelta);

        // Безопасная высота падения: чем ниже гравитация, тем выше прыжок
        // (высота ∝ 1/gravity), поэтому планку поднимаем во столько же раз.
        double ratio = target / BASE_GRAVITY;              // <1 в космосе
        double safeFall = BASE_SAFE_FALL / Math.max(0.05, ratio); // ~1/ratio
        double safeFallDelta = safeFall - BASE_SAFE_FALL;
        ensureModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, safeFallDelta);
    }

    /**
     * Гарантирует, что на атрибуте висит транзитный ADD_VALUE-модификатор с
     * данным id и значением. Если значение уже верное — ничего не делает
     * (дёшево). Если отличается/отсутствует — обновляет.
     */
    private static void ensureModifier(Player player, Holder<Attribute> attr,
                                       ResourceLocation id, double amount) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (existing != null && Math.abs(existing.amount() - amount) < 1.0E-6) {
            return; // уже правильное значение
        }
        // addOrUpdateTransientModifier заменяет по id, если уже есть.
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
