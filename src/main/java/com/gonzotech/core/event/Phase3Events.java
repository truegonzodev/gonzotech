package com.gonzotech.core.event;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Обработчики «мелких фишек» Фазы 3 (регистрируются на NeoForge.EVENT_BUS):
 * <ol>
 *   <li><b>Гейт крафта</b> ({@link PlayerEvent.ItemCraftedEvent}) — если игрок
 *       крафтит «закрытую» машину (эл. печь) до нужного «Открытия», ингредиенты
 *       всё равно тратятся, но вместо результата он получает бесполезный
 *       {@code botched_mechanism} и сообщение в чат. Задел под механику стресса.</li>
 *   <li><b>Свинец в ванильных печах</b> ({@link PlayerEvent.ItemSmeltedEvent}) —
 *       при заборе результата плавки железа из ванильной печи/плавильни/коптильни:
 *       5% свинца за предмет. (Взрыв цезия в ванильных печах делает миксин
 *       {@code AbstractFurnaceBlockEntityMixin} прямо в тике печи; в наших машинах —
 *       {@code SmeltSideEffects}.)</li>
 *   <li><b>Цезий в воде</b> ({@link PlayerTickEvent.Post}) — если в инвентаре есть
 *       цезиевая руда, поллуцит, слиток, самородок, пыль или блок цезия и игрок в
 *       воде, каждые 8 тиков — взрыв силой 1 в игроке. Выброшенный реактивный stack в
 *       воде также взрывается один раз и расходуется.</li>
 *   <li><b>Ведро лавы в воде</b> — если у игрока в инвентаре ведро лавы и он в воде,
 *       оно превращается в бесполезное ведро обсидиана; аналогично — если ведро
 *       лавы <i>выброшено</i> предметом в воду ({@link EntityTickEvent.Post}).</li>
 *   <li><b>Заметки учёного</b> ({@link PlayerEvent.PlayerLoggedInEvent}) — выдаются
 *       игроку ОДИН раз при первом входе в мир.</li>
 *   <li><b>Брожение фруктов</b> ({@link PlayerTickEvent.Post}) — ванильные фрукты
 *       (яблоко, светящиеся/сладкие ягоды, ломтик арбуза), пролежавшие в инвентаре
 *       15 минут, тихо превращаются в фруктовое сусло 1:1. Без NBT: состояние
 *       (базис количеств + очередь отложенных конверсий) держится в памяти сервера
 *       и чистится на выходе ({@link PlayerEvent.PlayerLoggedOutEvent}).</li>
 * </ol>
 */
public final class Phase3Events {

    /**
     * Ленивая карта гейта item -> требуемый номер «Открытия»: строится при первом
     * обращении, т.к. {@code Item}-инстансы недоступны до завершения регистрации.
     * <p>
     * Сюда попадают «физически закрытые» рецепты: до нужного «Открытия» крафт
     * тратит ингредиенты, но выдаёт бесполезный {@code botched_mechanism}. Кроме
     * эл. печи (задел Фазы 3) здесь ВСЯ логистика первого тира и станки Открытия 1
     * (помпа/аккумулятор/генератор булыжника). ИСКЛЮЧЕНИЕ — гаечный ключ: он
     * крафтится всегда (рецепт лишь скрыт в книге до Открытия 1).
     * <p>
     * Котёл/топка/стирлинг/конденсатор здесь НЕ фигурируют: их можно крафтить до
     * открытия, рецепт лишь скрыт в книге (см. reward-advancement).
     */
    private static Map<net.minecraft.world.item.Item, Integer> craftGate;

    private static final float CESIUM_WATER_EXPLOSION = 1.0F;
    private static final float LEAD_CHANCE = 0.05F;
    private static final int WATER_EFFECT_INTERVAL = 8;

    private Phase3Events() {
    }

    private static Map<net.minecraft.world.item.Item, Integer> gate() {
        if (craftGate == null) {
            craftGate = Map.ofEntries(
                // Станки Открытия 1 (гаечный КЛЮЧ НЕ гейтим — он крафтится всегда).
                Map.entry(com.gonzotech.machines.registry.ModMachines.ELECTRIC_FURNACE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.PUMP_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.ACCUMULATOR_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.COBBLE_GENERATOR_ITEM.get(), 1),
                // Трубы
                Map.entry(com.gonzotech.machines.registry.ModMachines.WIRE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.HEAT_PIPE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.WATER_PIPE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.STEAM_PIPE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.ITEM_PIPE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.UNIVERSAL_FLUID_PIPE_ITEM.get(), 1),
                // Узлы
                Map.entry(com.gonzotech.machines.registry.ModMachines.WIRE_NODE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.HEAT_NODE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.WATER_NODE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.STEAM_NODE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.ITEM_NODE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.UNIVERSAL_FLUID_NODE_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.UNIVERSAL_NODE_ITEM.get(), 1),
                // Сортировка предметов
                Map.entry(com.gonzotech.machines.registry.ModMachines.ITEM_FILTER_ITEM.get(), 1),
                Map.entry(com.gonzotech.machines.registry.ModMachines.ITEM_SCAVENGER_ITEM.get(), 1)
            );
        }
        return craftGate;
    }

    // ─────────────────────── 1. Гейт крафта закрытых машин ───────────────────────

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        Integer requiredTier = gate().get(crafted.getItem());
        if (requiredTier == null) return;

        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (progress.isRecipeTierUnlocked(requiredTier)) return;

        // Открытие ещё не активировано: ингредиенты уже потрачены (не откатываем —
        // это часть «прикола»), результат заменяем на бесполезный механизм.
        int count = crafted.getCount();
        crafted.setCount(0);
        for (int i = 0; i < count; i++) {
            ItemStack botched = new ItemStack(ModItems.BOTCHED_MECHANISM.get());
            if (!player.getInventory().add(botched)) {
                player.drop(botched, false);
            }
        }
        player.displayClientMessage(
            Component.translatable("message.gonzotech.botched_craft").withStyle(ChatFormatting.RED),
            false
        );
        // TODO(Фаза X): здесь начислять «стресс» игроку за преждевременный крафт.
    }

    // ─────────────────── 2. Ванильные печи: свинец + взрыв цезия ───────────────────

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack smelted = event.getSmelting();

        // Взрыв цезия в ванильных печах обрабатывается миксином прямо в тике печи
        // (AbstractFurnaceBlockEntityMixin) — чтобы рвануло в БЛОКЕ печи при появлении
        // слитка в слоте результата, как в наших машинах, а не при заборе у игрока.

        // Железо → 5% свинца за каждый переплавленный предмет (при заборе результата).
        if (!smelted.is(Items.IRON_INGOT)) return;
        int made = Math.max(1, smelted.getCount());
        int lead = 0;
        for (int i = 0; i < made; i++) {
            if (level.random.nextFloat() < LEAD_CHANCE) lead++;
        }
        if (lead > 0) {
            ItemStack stack = new ItemStack(ModItems.INGOT_ITEMS.get("lead_ingot").get(), lead);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    // ─────────────────── 3+4. Эффекты в воде (цезий / ведро лавы) ───────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.isInWater()) return;

        Inventory inv = player.getInventory();

        // 4. Ведро лавы в инвентаре → ведро обсидиана (каждый тик в воде).
        boolean replaced = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.LAVA_BUCKET)) {
                inv.setItem(i, new ItemStack(ModItems.OBSIDIAN_BUCKET.get(), st.getCount()));
                replaced = true;
            }
        }
        if (replaced) {
            player.containerMenu.broadcastChanges();
        }

        // 3. Любая реактивная форма цезия в инвентаре → взрыв силой 1 в игроке
        // каждые 8 тиков. Это оставляет старое поведение inventory-стаков без
        // расхода материала; выброшенный stack обрабатывается ниже и расходуется.
        if (level.getGameTime() % WATER_EFFECT_INTERVAL == 0 && hasWaterReactiveCesium(inv)) {
            level.explode(null,
                player.getX(), player.getY(), player.getZ(),
                CESIUM_WATER_EXPLOSION, Level.ExplosionInteraction.NONE);
        }
    }

    // ─────────────── 4b. Выброшенные предметы в воде ───────────────────────

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(itemEntity.level() instanceof ServerLevel level) || !itemEntity.isInWater()) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // У выброшенного материала реакция должна быть одноразовой: весь stack
        // химически расходуется до взрыва, поэтому на следующем EntityTick нет
        // источника для бесконечной цепочки взрывов.
        if (isWaterReactiveCesium(stack)) {
            double x = itemEntity.getX();
            double y = itemEntity.getY();
            double z = itemEntity.getZ();
            itemEntity.discard();
            level.explode(null, x, y, z, CESIUM_WATER_EXPLOSION, Level.ExplosionInteraction.NONE);
            return;
        }

        // Ведро лавы в воде остаётся прежним безвредным «приколом».
        if (stack.is(Items.LAVA_BUCKET)) {
            itemEntity.setItem(new ItemStack(ModItems.OBSIDIAN_BUCKET.get(), stack.getCount()));
        }
    }

    // ─────────────────── 5. Заметки учёного при первом входе ───────────────────

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (progress.hasReceivedScholarNotes()) return;

        progress.setReceivedScholarNotes(true);
        player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);

        ItemStack notes = new ItemStack(ModItems.SCHOLAR_NOTES.get());
        if (!player.getInventory().add(notes)) {
            player.drop(notes, false);
        }
    }

    // ─────────────────── 6. Психика: зависимость от сусла ───────────────────

    /**
     * Прибавка зависимости за одно съеденное сусло: +0.1% = +1 тысячная
     * (см. {@link PlayerPsyche#MAX}).
     */
    private static final int MASH_ADDICTION_PER_EAT = 1;

    /** Синк трёх HUD-шкал и состояния космического неба при входе в мир. */
    @SubscribeEvent
    public static void onPsycheLoginSync(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PsycheNetwork.sendToPlayer(player);
            com.gonzotech.space.SpaceSkyNetwork.syncToPlayer(player);
        }
    }

    /** При съедании сусла (прото/фруктовое) — зависимость +0.1%. */
    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack eaten = event.getItem();
        if (eaten.isEmpty()) return;
        if (!eaten.is(ModItems.THE_PROTO_MASH.get()) && !eaten.is(ModItems.THE_FRUIT_MASH.get())) return;

        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.addAddiction(MASH_ADDICTION_PER_EAT);
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);
    }

    // ─────────────────── 7. Брожение фруктов в инвентаре ───────────────────

    /**
     * «Прикол»: ванильные фрукты, пролежавшие в инвентаре игрока 15 минут,
     * тихо превращаются в {@link ModItems#THE_FRUIT_MASH фруктовое сусло} 1:1.
     *
     * <p>NBT сознательно НЕ используем (иначе фрукты перестанут стакаться и это
     * будет мешать игре). Вместо тега держим лёгкое состояние в памяти сервера:
     * раз в секунду сканируем инвентарь, и на КАЖДЫЙ прирост числа отслеживаемого
     * фрукта (подобрал / скрафтил / получил любым способом) заводим отложенную
     * задачу «через 15 минут забрать N этих фруктов и выдать N сусла». В момент
     * срабатывания берём {@code min(запланировано, реально в наличии)} — т.е. если
     * фрукты успели съесть/выкинуть, конвертируем лишь остаток; итог всегда 1:1.
     *
     * <p>Состояние живёт до выхода из мира (см. {@link #onPlayerLoggedOut}); при
     * повторном входе таймер для уже лежащих фруктов запускается заново — для
     * гэг-фичи это допустимо.
     */
    private static final Set<net.minecraft.world.item.Item> FERMENT_FRUITS = Set.of(
        Items.APPLE, Items.GLOW_BERRIES, Items.SWEET_BERRIES, Items.MELON_SLICE);

    /** 15 минут = 15 · 60 · 20 тиков. */
    private static final long FERMENT_DELAY_TICKS = 15L * 60L * 20L;
    /** Как часто сканировать инвентарь (тиков). Раз в секунду достаточно. */
    private static final int FERMENT_SCAN_INTERVAL = 20;

    /** Отложенная конверсия: {@code count} штук {@code fruit} в игру в тик {@code dueTick}. */
    private static final class FermentTask {
        final net.minecraft.world.item.Item fruit;
        int count;
        final long dueTick;
        FermentTask(net.minecraft.world.item.Item fruit, int count, long dueTick) {
            this.fruit = fruit;
            this.count = count;
            this.dueTick = dueTick;
        }
    }

    /** Последние замеченные количества фруктов на игрока (базис для diff). */
    private static final Map<UUID, Map<net.minecraft.world.item.Item, Integer>> lastFruitCounts =
        new HashMap<>();
    /** Очередь отложенных конверсий на игрока. */
    private static final Map<UUID, List<FermentTask>> fermentTasks = new HashMap<>();

    @SubscribeEvent
    public static void onFruitFermentTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // Сканируем не каждый тик, а раз в секунду.
        if (level.getGameTime() % FERMENT_SCAN_INTERVAL != 0) return;

        UUID id = serverPlayer.getUUID();
        Inventory inv = serverPlayer.getInventory();
        long now = level.getGameTime();

        // 1) Сначала выполняем «дозревшие» задачи — это меняет содержимое инвентаря.
        List<FermentTask> tasks = fermentTasks.get(id);
        boolean changed = false;
        if (tasks != null && !tasks.isEmpty()) {
            Iterator<FermentTask> it = tasks.iterator();
            while (it.hasNext()) {
                FermentTask task = it.next();
                if (task.dueTick > now) continue;
                it.remove();
                int held = countItem(inv, task.fruit);
                int convert = Math.min(task.count, held);
                if (convert <= 0) continue;
                removeItems(inv, task.fruit, convert);
                ItemStack mash = new ItemStack(ModItems.THE_FRUIT_MASH.get(), convert);
                if (!serverPlayer.getInventory().add(mash)) {
                    serverPlayer.drop(mash, false);
                }
                changed = true;
            }
            if (tasks.isEmpty()) fermentTasks.remove(id);
        }

        // 2) Пересчитываем фрукты ПОСЛЕ конверсий и заводим задачи на прирост.
        Map<net.minecraft.world.item.Item, Integer> last =
            lastFruitCounts.computeIfAbsent(id, k -> new HashMap<>());
        for (net.minecraft.world.item.Item fruit : FERMENT_FRUITS) {
            int current = countItem(inv, fruit);
            int prev = last.getOrDefault(fruit, 0);
            int gained = current - prev;
            if (gained > 0) {
                fermentTasks.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(new FermentTask(fruit, gained, now + FERMENT_DELAY_TICKS));
            }
            last.put(fruit, current);
        }

        if (changed) serverPlayer.containerMenu.broadcastChanges();
    }

    /** Освобождаем память по выходу игрока. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            lastFruitCounts.remove(id);
            fermentTasks.remove(id);
        }
    }

    /** Сколько всего данного предмета лежит в инвентаре игрока. */
    private static int countItem(Inventory inv, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(item)) total += st.getCount();
        }
        return total;
    }

    /** Забирает до {@code n} штук предмета из инвентаря; возвращает сколько реально забрано. */
    private static int removeItems(Inventory inv, net.minecraft.world.item.Item item, int n) {
        int remaining = n;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !st.is(item)) continue;
            int take = Math.min(remaining, st.getCount());
            st.shrink(take);
            remaining -= take;
            if (st.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        return n - remaining;
    }

    /** Есть ли в инвентаре хотя бы одна форма цезия, реагирующая с водой. */
    private static boolean hasWaterReactiveCesium(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isWaterReactiveCesium(inventory.getItem(slot))) return true;
        }
        return false;
    }

    /**
     * Полный и намеренно явный список реактивных форм цезия. Поллуцит — это
     * {@code raw_cesium}; рудные BlockItem'ы и цезиевый блок-хранилище также
     * считаются реактивными переносимыми формами.
     */
    private static boolean isWaterReactiveCesium(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModItems.RAW_ORE_ITEMS.get("cesium").get())
            || stack.is(ModItems.INGOT_ITEMS.get("cesium_ingot").get())
            || stack.is(ModItems.NUGGET_ITEMS.get("cesium_nugget").get())
            || stack.is(ModItems.DUST_ITEMS.get("cesium_dust").get())
            || stack.is(ModItems.METAL_BLOCK_ITEMS.get("cesium_block").get())) {
            return true;
        }
        for (var oreBlock : ModItems.ORE_BLOCK_ITEMS.get("cesium").values()) {
            if (stack.is(oreBlock.get())) return true;
        }
        return false;
    }
}
