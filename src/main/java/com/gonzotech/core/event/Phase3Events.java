package com.gonzotech.core.event;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

/**
 * Обработчики «мелких фишек» Фазы 3 (регистрируются на NeoForge.EVENT_BUS):
 * <ol>
 *   <li><b>Гейт крафта</b> ({@link PlayerEvent.ItemCraftedEvent}) — если игрок
 *       крафтит «закрытую» машину (эл. печь) до нужного «Открытия», ингредиенты
 *       всё равно тратятся, но вместо результата он получает бесполезный
 *       {@code botched_mechanism} и сообщение в чат. Задел под механику стресса.</li>
 *   <li><b>Свинец-побочка ванильных печей</b> ({@link PlayerEvent.ItemSmeltedEvent})
 *       — при заборе результата плавки железа из ванильной печи/плавильни/коптильни
 *       с шансом 5% за каждый переплавленный предмет добавляется свинцовый слиток.
 *       (В наших машинах это делает {@code SmeltSideEffects}.)</li>
 *   <li><b>Цезий в воде</b> ({@link PlayerTickEvent.Post}) — если в инвентаре есть
 *       цезиевая руда/поллуцит и игрок в воде, каждые 8 тиков — взрыв силой 1 в игроке.</li>
 *   <li><b>Ведро лавы в воде</b> — если в инвентаре ведро лавы и игрок в воде, оно
 *       заменяется на бесполезное ведро обсидиана.</li>
 * </ol>
 */
public final class Phase3Events {

    /**
     * Ленивая карта гейта item -> требуемый номер «Открытия»: строится при первом
     * обращении, т.к. {@code Item}-инстансы недоступны до завершения регистрации.
     * Сюда попадают ТОЛЬКО «физически закрытые» машины (сейчас — эл. печь).
     * Котёл/топка/стирлинг/конденсатор здесь НЕ фигурируют: их можно крафтить до
     * открытия, рецепт лишь скрыт в книге (см. reward-advancement).
     * Будущие закрытые машины (ядерный реактор и т.п.) добавлять здесь.
     */
    private static Map<net.minecraft.world.item.Item, Integer> craftGate;

    private static final float CESIUM_WATER_EXPLOSION = 1.0F;
    private static final float LEAD_CHANCE = 0.05F;
    private static final int WATER_EFFECT_INTERVAL = 8;

    private Phase3Events() {
    }

    private static Map<net.minecraft.world.item.Item, Integer> gate() {
        if (craftGate == null) {
            craftGate = Map.of(
                com.gonzotech.machines.registry.ModMachines.ELECTRIC_FURNACE_ITEM.get(), 1
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

    // ─────────────────── 2. Свинец-побочка ванильных печей ───────────────────

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack smelted = event.getSmelting();
        if (!smelted.is(Items.IRON_INGOT)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

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

        // 4. Ведро лавы → ведро обсидиана (каждый тик в воде, чтобы не убегало).
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

        // 3. Цезий/поллуцит в инвентаре → взрыв силой 1 в игроке каждые 8 тиков.
        if (level.getGameTime() % WATER_EFFECT_INTERVAL == 0 && hasCesium(inv)) {
            level.explode(null,
                player.getX(), player.getY(), player.getZ(),
                CESIUM_WATER_EXPLOSION, Level.ExplosionInteraction.NONE);
        }
    }

    /** Есть ли в инвентаре цезиевая руда (любой host-вариант) или поллуцит (raw_cesium). */
    private static boolean hasCesium(Inventory inv) {
        net.minecraft.world.item.Item raw = ModItems.RAW_ORE_ITEMS.get("cesium").get();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (st.is(raw)) return true;
            for (var byHost : ModItems.ORE_BLOCK_ITEMS.get("cesium").values()) {
                if (st.is(byHost.get())) return true;
            }
        }
        return false;
    }
}
