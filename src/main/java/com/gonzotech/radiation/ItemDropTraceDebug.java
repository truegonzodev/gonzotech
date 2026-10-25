package com.gonzotech.radiation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * 0.3.291 (пункт 5 таблицы автора): жизнь ItemEntity — создание, подбор и
 * удаление с id сущности. Слияния печатает миксин {@code
 * ItemEntityMergeDebugMixin} (в 1.21.4 события merge нет). Удаление
 * логируется только для сущностей, записанных этим же отладчиком в текущем
 * сеансе (TRACED): нормальные чанки не шумят, а «исчезновение» дюп-кандидата
 * видно. Создание из диска (загрузка чанка) помечается, но в TRACED не
 * попадает — счёт живёт только у свежих дропов.
 */
@EventBusSubscriber(modid = "gonzotech")
public final class ItemDropTraceDebug {
    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Long> TRACED =
        new java.util.concurrent.ConcurrentHashMap<>();

    private ItemDropTraceDebug() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!com.gonzotech.core.config.GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ItemEntity drop)) return;
        if (event.loadedFromDisk()) {
            LOG.info("[RAD-INV][ENT+] #{} {} — догружен из диска, след не ведём",
                drop.getId(), DropTraceFormat.fmt(drop.getItem()));
            return;
        }
        if (TRACED.size() > 8192) TRACED.clear();
        TRACED.put(drop.getId(), System.currentTimeMillis());
        Entity owner = drop.getOwner();
        LOG.info("[RAD-INV][ENT+] #{} {} в {} на {} (владелец {})",
            drop.getId(), DropTraceFormat.fmt(drop.getItem()), level.dimension().location(),
            drop.blockPosition(), owner == null ? "нет" : owner.getName().getString());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!com.gonzotech.core.config.GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) return;
        if (!(event.getEntity() instanceof ItemEntity drop)) return;
        Long born = TRACED.remove(drop.getId());
        if (born == null) return; // не наш след — чужая рутина, не шумим
        LOG.info("[RAD-INV][ENT−] #{} убрана; жила {} мс, стак {}",
            drop.getId(), System.currentTimeMillis() - born, DropTraceFormat.fmt(drop.getItem()));
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!com.gonzotech.core.config.GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) return;
        if (!(event.getPlayer().level() instanceof ServerLevel)) return;
        LOG.info("[RAD-INV][ENT-PICK] #{} поднят {}: {} → {}",
            event.getItemEntity().getId(), event.getPlayer().getName().getString(),
            DropTraceFormat.fmt(event.getOriginalStack()), DropTraceFormat.fmt(event.getCurrentStack()));
    }
}
