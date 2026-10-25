package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.DropTraceFormat;
import com.gonzotech.radiation.ItemRadioactivity;
import com.gonzotech.radiation.RadDoseIndex;
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
 *
 * <p>0.3.291 (таблица автора диагностики дюпа): к суммам добавлены линии
 * {@code [CLICK]} (дактилопечать пакета: menu/cid/state/type/button/затронутые
 * слоты/carried «до» + флаг расхождения state), {@code [QC]} (фазы QUICK_CRAFT
 * вычисляются из buttonNum пакета (0.3.293: приватные поля меню и @Accessor
     * не используются — тот же риск APPLY, что и shadow поля из предка)
 * {@code [CLICK-E]} (то же «после», печатается и при нулевой дельте Σ — окно
 * призраков). Прочие точки таблицы — отдельные миксины/слушатели под тем же
 * TOML-гейтом: [CREATIVE]/[CREATIVE-DROP], [SET-SLOT], [SLOT-WRITE],
 * [ENT+]/[ENT-MERGE]/[ENT-PICK]/[ENT−], [PASS]/[COUNT]/[CLAMP].</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerClickRadiationMixin {

    @Shadow
    public ServerPlayer player;

    /** Объявлено в самом целевом классе — field-shadow легален (правило 293). */
    @Shadow
    private net.minecraft.server.MinecraftServer server;

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

    /** 0.3.291 (п.1 таблицы автора): «до» затронутых пакетом слотов и carried. */
    @Unique
    private java.util.Map<Integer, String> gonzotech$slotsBefore;
    @Unique
    private String gonzotech$carriedBefore;

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
        // 0.3.294: любое действие по инвентарю закрывает окно flush — сервер не
        // должен менять компоненты, пока сортировочный клиентский софт держит
        // снапшот сетки (см. RadDoseIndex).
        RadDoseIndex.noteInteraction(this.player);
        long n = GONZOTECH$CLICKS.incrementAndGet();
        if (n <= 3 || n % 25 == 0) {
            GONZOTECH$LOG.info("[RAD-INV] обработчик кликов жив: клик №{} от {} (slot={}, carried={} шт)",
                n, this.player.getGameProfile().getName(), packet.getSlotNum(),
                packet.getCarriedItem().getCount());
        }
        // 0.3.295 (debug2.log: NPE gonzotech$slotsBefore на Netty IO #1 — кик
        // клиента): handleContainerClick исполняется ДВАЖДЫ на пакет — вход на
        // netty-потоке, затем перевызов на серверном после ensureRunningOnSameThread
        // (netty-проход бросает RunningOnDifferentThreadException и RETURN-инъект
        // на нём НЕ выполняется). HEAD следующего пакета на netty успевал
        // перезатереть/обнулить снапшот-поля между HEAD и RETURN серверного
        // прохода предыдущего — FieldMap.put на null. Диагностика живёт только на
        // серверном потоке: там пакеты идут строго последовательно, HEAD→RETURN
        // пара на пакет ровно одна; «дубли [CLICK]» из логов 291–294 были именно
        // этой двойной исполняемой цепочкой, а не внепотоковыми пакетами.
        // noteInteraction остаётся над гейтом — штамп
        // активности должен ловить и netty-вход (RadDoseIndex синхронизирован).
        if (!this.server.isSameThread()) return;
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
            // 0.3.291 (пункты 1 и 4 таблицы автора): полный дактилопечатающий
            // след каждого клика — меню, containerId, stateId, тип, кнопка,
            // затронутые слоты и курсор «до». Расхождение state — собственный
            // флаг: клики по устаревшему снапшоту — классическое окно дюпа.
            AbstractContainerMenu menu = this.player.containerMenu;
            this.gonzotech$slotsBefore = new java.util.HashMap<>();
            StringBuilder gonzotech$affected = new StringBuilder();
            for (java.util.Map.Entry<Integer, ItemStack> change : packet.getChangedSlots().entrySet()) {
                int at = change.getKey();
                String before = at >= 0 && at < menu.slots.size()
                    ? DropTraceFormat.fmt(menu.getSlot(at).getItem()) : "нет слота";
                this.gonzotech$slotsBefore.put(at, before);
                gonzotech$affected.append(at).append(':').append(before)
                    .append("→").append(DropTraceFormat.fmt(change.getValue())).append(' ');
            }
            this.gonzotech$carriedBefore = DropTraceFormat.fmt(packet.getCarriedItem());
            GONZOTECH$LOG.info(
                "[RAD-INV][CLICK] {} t={} menu={} cid={} slot={} btn={} type={} state={}/{}{} | carried {} | затронуто [{}]",
                this.player.getGameProfile().getName(),
                this.player.getServer() != null ? this.player.getServer().getTickCount() : -1L,
                menu.getClass().getSimpleName(), packet.getContainerId(), packet.getSlotNum(),
                packet.getButtonNum(), packet.getClickType(), packet.getStateId(), menu.getStateId(),
                packet.getStateId() != menu.getStateId() ? " ⚠РАСХОЖДЕНИЕ" : "",
                this.gonzotech$carriedBefore, gonzotech$affected);
            if (packet.getClickType() == net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                // 0.3.293: приватные quickcraft*-поля меню не трогаем (accessor
                // удалён — тот же класс APPLY-риска, что и shadow-поле). Фаза
                // полностью выводится из buttonNum пакета, ширину выделения
                // видно по changedSlots: 0..2 = НАЧАЛО/ДОБАВЛЕНИЕ/ЗАВЕРШЕНИЕ,
                // 3..5 = ЗАБОР/СБОР/РАЗЛОЖИТЬ.
                GONZOTECH$LOG.info("[RAD-INV][QC] {} btn={}({}) в пакете {} слот(ов), carried={}",
                    this.player.getGameProfile().getName(), packet.getButtonNum(),
                    gonzotech$phase(packet.getButtonNum()), packet.getChangedSlots().size(),
                    DropTraceFormat.fmt(menu.getCarried()));
            }
        }
    }

    @Inject(
            method = "handleContainerClick",
            at = @At("RETURN"))
    private void gonzotech$reportContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (this.player == null || !this.server.isSameThread()) return;
        if (!GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get() || this.gonzotech$radBefore == null) return;
        long[] after = gonzotech$snapshot(this.player.containerMenu);
        // 0.3.291 (п.1): «после» курсора и затронутых слотов — печатается и при
        // нулевой дельте Σ: призрак живёт именно там, где суммы не шевельнулись.
        if (this.gonzotech$slotsBefore != null) {
            StringBuilder gonzotech$after = new StringBuilder();
            for (java.util.Map.Entry<Integer, String> seen : this.gonzotech$slotsBefore.entrySet()) {
                int at = seen.getKey();
                String now = at >= 0 && at < this.player.containerMenu.slots.size()
                    ? DropTraceFormat.fmt(this.player.containerMenu.getSlot(at).getItem())
                    : "меню сменилось";
                gonzotech$after.append(at).append(": ").append(seen.getValue())
                    .append(" → ").append(now).append("; ");
            }
            GONZOTECH$LOG.info("[RAD-INV][CLICK-E] {} slot={} carried {} → {} | слоты [{}]",
                this.player.getGameProfile().getName(), packet.getSlotNum(),
                this.gonzotech$carriedBefore,
                DropTraceFormat.fmt(this.player.containerMenu.getCarried()), gonzotech$after);
            this.gonzotech$slotsBefore = null;
        }
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
    private static String gonzotech$phase(int button) {
        return switch (button) {
            case 0 -> "НАЧАЛО";
            case 1 -> "ДОБАВЛЕНИЕ СЛОТА";
            case 2 -> "ЗАВЕРШЕНИЕ";
            case 3 -> "ЗАБОР ПО ОДНОМУ";
            case 4 -> "СБОР ПОРОВНУ";
            case 5 -> "РАЗЛОЖИТЬ ПО ОДНОМУ";
            default -> "ФАЗА" + button;
        };
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
