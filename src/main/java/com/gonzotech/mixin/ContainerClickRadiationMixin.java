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
 * (0.3.276) Диагностика + защитник всасывания предсказания.
 *
 * <p>Диагностика: с каждого клика по контейнеру печатает серверные суммы
 * (предметы и наведённая радиация, слоты + carried) до и после — рост суммы
 * = физический дюп, стабильная = расхождение картинки. Guard'ы-паузы
 * (271–274) удалены по вердикту автора: «пауза — неверное вмешательство»
 * (замороженные интервалы ленивый догон считал покоем и сжигал наведёнку,
 * а дюп живость стражи не остановила).</p>
 *
 * <p>Защитник (0.3.276): в 1.21.4 handleContainerClick выполняет clicked(),
 * а ЗАТЕМ поглощает changedSlots клиента через setRemoteSlotNoCopy без каких-либо
 * проверок — stale-копия из раздутой картинки становится серверной правдой
 * (дюп, переживающий перезаход; тот же механизм откатывает наши NBT-записи).
 * Легальный клик не увеличивает сумму предметов меню (крафт только уносит),
 * CLONE — единственный штатный рост, и он исключение. Заявлено больше, чем
 * содержит сервер, либо слот раздут выше собственного лимита — пакет
 * отвергается ДО clicked() и клиент ресинхронизируется broadcastFullState.
 * Отказ фиксируется unconditional-WARN (без гейта конфига): его нечем
 * «не заметить».</p>
 *
 * <p>Живость трансформации доказана прогоном автора 23.10 (строка «ПРИМЕНЁН»
 * + mixin-debug о обоих инжекторах); unconditional-блок при загрузке класса
 * оставлен: он отделяет «трансформация не применилась» от «строк не было».
 * Аномалию «клики drag&drop были — клик-строк ноль» (автор подтвердил клики
 * и подвешенный carried, лог «carried 0» при этом неверен) новая unconditional
 * WARN-строка отказа должна разрешить окончательно.</p>
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
        if (this.gonzotech$rejectInflatedClick(packet)) {
            callback.cancel();
            return;
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

    /** Сумма-инвариант предсказания: см. javadoc. true = пакет отвергнут. */
    @Unique
    private boolean gonzotech$rejectInflatedClick(ServerboundContainerClickPacket packet) {
        AbstractContainerMenu menu = this.player.containerMenu;
        if (menu == null || menu.containerId != packet.getContainerId()) return false;
        // CLONE — штатный «творческий» набор (middle-click): сумма легально
        // растёт серверным clicked(), не трогаем.
        if (packet.getClickType() == net.minecraft.world.inventory.ClickType.CLONE) return false;
        long claimed = packet.getCarriedItem().getCount();
        for (var entry : packet.getChangedSlots().int2ObjectEntrySet()) {
            if (!menu.isValidSlotIndex(entry.getIntKey())) continue; // vanilla и так игнорит
            claimed += ((ItemStack) entry.getValue()).getCount();
        }
        long actual = menu.getCarried().getCount();
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) actual += stack.getCount();
        }
        if (claimed <= actual) return false;
        GONZOTECH$LOG.warn("[RAD-INV] {} клик ОТВЕРГНУТ (slot={}, carried={} шт): клиент заявляет {} шт, сервер содержит {} (+{}) — поглощение раздутого предсказания запрещено, принудительный ресинк",
            this.player.getGameProfile().getName(), packet.getSlotNum(),
            packet.getCarriedItem().getCount(), claimed, actual, claimed - actual);
        menu.broadcastFullState();
        return true;
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
