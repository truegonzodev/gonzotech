package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.ItemRadioactivity;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.3.277) Диагностика инвентарного трафика.
 *
 * <p>С каждого клика по контейнеру печатает серверные суммы (предметы и
 * наведённая радиация, слоты + carried) до и после. Рост серверной суммы =
 * физический дюп; расхождение с картинкой клиента при стабильной = призрак
 * (для второй итерации рядом лежит серверный журнал {@code [LEDGER]} —
 * Σ сервера и Σ клиента, присланная клиентом).</p>
 *
 * <p>Вердикт по «absorb» (вечер 5) ОТМЕНЁН собственным источником 1.21.4:
 * setRemoteSlotNoCopy/setRemoteCarried пишут ТОЛЬКО кэш remoteSlots/
 * remoteCarried (снимок «что уже послано клиенту»), реальные слоты из пакета
 * не заполняются никогда — reject-страж 276 защищал несуществующий канал и
 * удалён. Паузы удалены раньше (вердикт автора).</p>
 *
 * <p>Гипотеза «клик не доходит до обработчика» (в логах — ни одной строки при
 * живых кликах) проверяется unconditional-счётчиком: первые 3 клика и каждый
 * 25-й печатаются БЕЗ гейта конфига — отсутствие строк = обработчик вранти
 * не вызывается, это факт о рантайме, а не о фазе лога.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerClickRadiationMixin {

    @Shadow
    public ServerPlayer player;

    @Unique
    private static final Logger GONZOTECH$LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");

    /** Доказательство живости: класс миксина загружен JVM ⇒ трансформация
     *  целевого класса состоялась (apply-ошибка упала бы крашем, require=1). */
    @Unique
    private static final boolean GONZOTECH$LIVE = GONZOTECH$announce();

    @Unique
    private static boolean GONZOTECH$announce() {
        GONZOTECH$LOG.info("[RAD-INV] миксин контейнерных кликов ПРИМЕНЁН (класс загружен, сервер = vanilla ServerGamePacketListenerImpl)");
        return true;
    }

    /** Снимок меню до клика: [предметы, биты Σ радиации, carried-штук]. */
    @Unique
    private long[] gonzotech$radBefore;

    /** Unconditional-пробник: «клики доходят» отделяется от «клики видны в логе». */
    @Unique
    private static final java.util.concurrent.atomic.AtomicLong GONZOTECH$CLICKS =
            new java.util.concurrent.atomic.AtomicLong();

    /** Реалтаймовый шлюз клик-строк: не чаще одной в 150 мс (anti-flood при спам-ЛКМ). */
    @Unique
    private static final java.util.concurrent.atomic.AtomicLong GONZOTECH$LAST_CLICK_LOG_NS =
            new java.util.concurrent.atomic.AtomicLong();

    @Unique
    private static boolean gonzotech$allowClickLog() {
        long now = System.nanoTime();
        long prev = GONZOTECH$LAST_CLICK_LOG_NS.get();
        return now - prev > 150_000_000L && GONZOTECH$LAST_CLICK_LOG_NS.compareAndSet(prev, now);
    }

    @Inject(
            method = "handleContainerClick",
            at = @At("HEAD"))
    private void gonzotech$snapshotContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (this.player == null) return;
        long n = GONZOTECH$CLICKS.incrementAndGet();
        if (n <= 3 || n % 25 == 0) {
            GONZOTECH$LOG.info("[RAD-INV] обработчик кликов жив: клик №{} от {} (slot={}, carried={} шт)",
                n, this.player.getGameProfile().getName(), packet.getSlotNum(),
                packet.getCarriedItem().getCount());
        }
        if (GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) {
            this.gonzotech$radBefore = gonzotech$snapshot(this.player.containerMenu);
            if (gonzotech$allowClickLog()) {
                GONZOTECH$LOG.info("[RAD-INV] {} клик t={} slot={} carried={} шт | до: {} шт, rad={}",
                    this.player.getGameProfile().getName(),
                    this.player.getServer() != null ? this.player.getServer().getTickCount() : -1L,
                    packet.getSlotNum(),
                    packet.getCarriedItem().getCount(),
                    this.gonzotech$radBefore[0],
                    formatNzt(this.gonzotech$radBefore[1]));
            }
        }
    }

    @Inject(
            method = "handleContainerClick",
            at = @At("RETURN"))
    private void gonzotech$reportContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (this.player == null) return;
        if (!GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get() || this.gonzotech$radBefore == null) return;
        long[] after = gonzotech$snapshot(this.player.containerMenu);
        long beforeItems = this.gonzotech$radBefore[0];
        long afterItems = after[0];
        double beforeRad = Double.longBitsToDouble(this.gonzotech$radBefore[1]);
        double afterRad = Double.longBitsToDouble(after[1]);
        // Дельта предметов = серверное доказательство дюпа (или его отсутствия:
        // нулевые дельты при живом миксине = «дюп» есть расхождение картинки).
        // Дельта радиации = ловит «carried теряет всю радиацию» на сервере.
        if (afterItems == beforeItems && Double.compare(beforeRad, afterRad) == 0
                && after[2] == this.gonzotech$radBefore[2]) {
            return;
        }
        GONZOTECH$LOG.info("[RAD-INV] {} итог клика: {} -> {} предмета(ов) (Δ{}), радиация {} -> {} (Δ{}), carried {} -> {} шт, меню {}",
            this.player.getGameProfile().getName(),
            beforeItems, afterItems, afterItems - beforeItems,
            formatNzt(Double.doubleToRawLongBits(beforeRad)), formatNzt(Double.doubleToRawLongBits(afterRad)),
            formatNzt(Double.doubleToRawLongBits(afterRad - beforeRad)),
            this.gonzotech$radBefore[2], after[2],
            this.player.containerMenu.getClass().getSimpleName());
    }

    @Unique
    private static String formatNzt(long bits) {
        return String.format("%.2f", Double.longBitsToDouble(bits));
    }

    /**
     * Снимок серверного состояния открытого меню: сумма предметов (все слоты +
     * курсор), сумма наведённой радиоактивности (per-item × count, та же
     * дискретность, что пишет проход) и размер carried.
     */
    @Unique
    private static long[] gonzotech$snapshot(AbstractContainerMenu menu) {
        int total = 0;
        double rad = 0.0D;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            total += stack.getCount();
            rad += ItemRadioactivity.getInduced(stack) * Math.max(1, stack.getCount());
        }
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            total += carried.getCount();
            rad += ItemRadioactivity.getInduced(carried) * carried.getCount();
        }
        return new long[]{total, Double.doubleToRawLongBits(rad), carried.getCount()};
    }
}
