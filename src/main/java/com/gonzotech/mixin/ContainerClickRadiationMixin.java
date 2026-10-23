package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.ItemRadioactivity;
import com.gonzotech.radiation.RadiationClickWatch;
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
 * (0.3.271, автор 23.10) Помечает клик по контейнеру до исполнения меню-логики,
 * чтобы радиационный пересчёт не переписывал стаки в окне drag&drop (дюп через
 * carried). Никаких изменений самих кликов.
 *
 * <p>0.3.273: ран 23.10 показал нулевой вывод обоих хендлеров при живом
 * дебаг-флаге — молчание хендлера больше нельзя отличить от «кликов не было».
 * Поэтому (а) статический блок пишет одну unconditional-строку при загрузке
 * класса: появление «миксин контейнерных кликов ПРИМЕНЁН» в логе доказывает,
 * что трансформация сработала; (б) при включённом флаге каждый клик пишет и
 * дельту РАДИАЦИИ (по Σ per-item×count всех слотов + курсору), а не только
 * дельту предметов — обнуление carried видно именно так.</p>
 *
 * <p>0.3.274: прогон автора показал нулевой вывод хендлеров при живом флаге и
 * активных кликах — стража переехала на детерминированный сигнал (открытое меню
 * в RadiationSystem). Миксин остаётся как чистая диагностика: строка «ПРИМЕНЁН»
 * в полном логе решает, удалить ли его в 0.3.275.</p>
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

    @Inject(
            method = "handleContainerClick",
            at = @At("HEAD"))
    private void gonzotech$markContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (this.player == null) return;
        RadiationClickWatch.markClick(this.player);
        if (GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) {
            this.gonzotech$radBefore = gonzotech$snapshot(this.player.containerMenu);
            if (RadiationClickWatch.allowClickLog()) {
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
