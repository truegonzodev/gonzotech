package com.gonzotech.machines.network;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;
import com.gonzotech.core.block.entity.TungstenAbsorberBlockEntity;
import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Синхронизация живого потока провода в HUD гаечного ключа.
 * <p>
 * Провод пассивен и потока не хранит — фактический объём за тик считает
 * {@link FlowTracker} на СЕРВЕРЕ (только там реально идёт слив). Клиент не может
 * знать поток сам, поэтому пока игрок держит ключ и смотрит на конкретную трубу
 * пучка, он раз в несколько тиков шлёт {@link RequestPayload} с позицией и ТИПОМ
 * трубы; сервер отвечает {@link FlowPayload} с двумя числами — сколько ушло в
 * каждый конец ОСИ по мировым сторонам (напр. для оси X → восток и запад).
 * Направления, а не «лево/право», чтобы показания не зависели от того, откуда
 * смотрит игрок. В связке типы делят позицию, поэтому запрос адресный по типу.
 */
public final class PipeFlowNetwork {

    private PipeFlowNetwork() {
    }

    /** Клиент → сервер: «покажи поток трубы типа {@code typeId} в этой позиции». */
    public record RequestPayload(BlockPos pos, int typeId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pipe_flow_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, RequestPayload::pos,
                ByteBufCodecs.VAR_INT, RequestPayload::typeId,
                RequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Значение {@code axis3d} для блока-узла: показываем не два конца, а сумму. */
    public static final int AXIS_NODE_SUM = -1;

    /**
     * Сервер → клиент: поток трубы типа {@code typeId}. Для обычной трубы
     * {@code axis3d} — {@link Direction.Axis#ordinal()} (0=X,1=Y,2=Z),
     * {@code posAmount} — объём в положительный конец оси (+X=восток, +Y=верх,
     * +Z=юг), {@code negAmount} — в отрицательный. Для узла ({@link #AXIS_NODE_SUM})
     * {@code posAmount} — суммарный поток через все грани, {@code negAmount}=0.
     */
    public record FlowPayload(BlockPos pos, int typeId, int axis3d, long posAmount, long negAmount,
                              long lossMilli, int clumpSize) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FlowPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pipe_flow"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FlowPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, FlowPayload::pos,
                ByteBufCodecs.VAR_INT, FlowPayload::typeId,
                ByteBufCodecs.VAR_INT, FlowPayload::axis3d,
                ByteBufCodecs.VAR_LONG, FlowPayload::posAmount,
                ByteBufCodecs.VAR_LONG, FlowPayload::negAmount,
                ByteBufCodecs.VAR_LONG, FlowPayload::lossMilli,
                ByteBufCodecs.VAR_INT, FlowPayload::clumpSize,
                FlowPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 0.3.335 HUD-NODE1: один запрос на ВСЕ типы позиции. Раньше ключ слал по
     * пакету на каждый PipeType.values() (до 13 у универсального узла) каждые
     * REQUEST_INTERVAL тиков, при этом общий ключ PayloadThrottle "pipeFlow"
     * допускает 8 запросов / 20 тиков. Это правдоподобная диагностика
     * нестабильности строки, НО доказанной причиной мигания её не объявляем
     * (см. CURRENT-STATE 334). Теперь один пакет на позицию, сервер сам отвечает
     * по каждому реально несомому типу.
     */
    public record AllFlowRequestPayload(BlockPos pos, boolean probe) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AllFlowRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pipe_flow_all_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AllFlowRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, AllFlowRequestPayload::pos,
                ByteBufCodecs.BOOL, AllFlowRequestPayload::probe,
                AllFlowRequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 0.3.335 (приёмка автора): серверный факт «чей это порт». ownerId: 0 —
     * обычный узел, 1..5 — владелец сборки (FormedPorts.OWNER_*). Клиент
     * кэширует ответ ПО ПОЗИЦИИ без срока жизни и до получения ответа вообще
     * не рисует классическую строку: тем самым убрано мерцание «не той
     * надписью» в первую миллисекунду наведения. probe-запрос дополнительно
     * присылает пачку ответов по всей видимой области.
     */
    public record PortInfoPayload(BlockPos pos, int ownerId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PortInfoPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pipe_port_info"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PortInfoPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, PortInfoPayload::pos,
                ByteBufCodecs.VAR_INT, PortInfoPayload::ownerId,
                PortInfoPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент → сервер: «покажи поток ПРЕДМЕТОВ в предметной трубе в этой позиции». */
    public record ItemFlowRequestPayload(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ItemFlowRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "item_flow_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ItemFlowRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, ItemFlowRequestPayload::pos,
                ItemFlowRequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Сервер → клиент: поток предметов в предметной трубе. Параллельные списки:
     * {@code items} — id предметов (ResourceLocation), {@code counts} — штук/тик.
     * HUD рисует по строке на предмет: «Имя — N/т».
     */
    public record ItemFlowPayload(BlockPos pos, java.util.List<ResourceLocation> items,
                                  java.util.List<Integer> counts) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ItemFlowPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "item_flow"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ItemFlowPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, ItemFlowPayload::pos,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), ItemFlowPayload::items,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ItemFlowPayload::counts,
                ItemFlowPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент → сервер: запрос contents транзитного буфера конкретного Фильтра. */
    public record FilterStorageRequestPayload(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FilterStorageRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "filter_storage_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FilterStorageRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, FilterStorageRequestPayload::pos,
                FilterStorageRequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Сервер → клиент: суммарное содержимое очереди Фильтра. Предметы передаются
     * registry id, а counts суммируются по точному Item (components в HUD не нужны).
     */
    public record FilterStoragePayload(BlockPos pos, java.util.List<ResourceLocation> items,
                                       java.util.List<Integer> counts) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FilterStoragePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "filter_storage"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FilterStoragePayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, FilterStoragePayload::pos,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), FilterStoragePayload::items,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), FilterStoragePayload::counts,
                FilterStoragePayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server request for the hidden GTH buffer of an active tungsten absorber. */
    public record TungstenAbsorberRequestPayload(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TungstenAbsorberRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "tungsten_absorber_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TungstenAbsorberRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, TungstenAbsorberRequestPayload::pos,
                TungstenAbsorberRequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client heat level and active heat-pipe connection of a tungsten absorber. */
    public record TungstenAbsorberPayload(BlockPos pos, int gth, boolean active) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TungstenAbsorberPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "tungsten_absorber"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TungstenAbsorberPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, TungstenAbsorberPayload::pos,
                ByteBufCodecs.VAR_INT, TungstenAbsorberPayload::gth,
                ByteBufCodecs.BOOL, TungstenAbsorberPayload::active,
                TungstenAbsorberPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Регистрация всех пакетов. Вызывается из {@code GonzoTechMod#registerPayloads}. */
    /**
     * 0.3.253 (автор): статус «мастерства ключа» (съеден однажды) — клиенту,
     * чтобы подсказки труб и поворот пучков работали без ключа в руке.
     */
    public record WrenchMasteryPayload(boolean mastered) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WrenchMasteryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "wrench_mastery"));

        public static final StreamCodec<RegistryFriendlyByteBuf, WrenchMasteryPayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, WrenchMasteryPayload::mastered,
                WrenchMasteryPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** При входе в мир (реальный статус) и сразу после поедания ключа. */
    public static void sendWrenchMastery(ServerPlayer player) {
        var progress = player.getData(com.gonzotech.chalkboard.progress.ModAttachments.CHALKBOARD_PROGRESS.get());
        boolean mastered = progress.hasNoteFlag(com.gonzotech.chalkboard.notes.ScholarNoteFlags.WRENCH_EATEN);
        PacketDistributor.sendToPlayer(player, new WrenchMasteryPayload(mastered));
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
            RequestPayload.TYPE,
            RequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "pipeFlow", 8)) return;
                    respond(player, payload.pos(), payload.typeId());
                }
            }));

        registrar.playToClient(
            FlowPayload.TYPE,
            FlowPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.client.WrenchHud.acceptFlow(payload)));

        registrar.playToServer(
            AllFlowRequestPayload.TYPE,
            AllFlowRequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "pipeFlowAll", FLOW_REQUESTS_PER_WINDOW)) return;
                    respondAll(player, payload.pos(), payload.probe());
                }
            }));

        registrar.playToClient(
            PortInfoPayload.TYPE,
            PortInfoPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.client.WrenchHud.acceptPortInfo(payload)));

        registrar.playToServer(
            ItemFlowRequestPayload.TYPE,
            ItemFlowRequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "pipeItemFlow", FLOW_REQUESTS_PER_WINDOW)) return;
                    respondItemFlow(player, payload.pos());
                }
            }));

        registrar.playToClient(
            ItemFlowPayload.TYPE,
            ItemFlowPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.client.WrenchHud.acceptItemFlow(payload)));

        registrar.playToServer(
            FilterStorageRequestPayload.TYPE,
            FilterStorageRequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "pipeFilter", 8)) return;
                    respondFilterStorage(player, payload.pos());
                }
            }));

        registrar.playToClient(
            FilterStoragePayload.TYPE,
            FilterStoragePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.client.WrenchHud.acceptFilterStorage(payload)));

        registrar.playToServer(
            TungstenAbsorberRequestPayload.TYPE,
            TungstenAbsorberRequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "pipeTungsten", 8)) return;
                    respondTungstenAbsorber(player, payload.pos());
                }
            }));

        registrar.playToClient(
            TungstenAbsorberPayload.TYPE,
            TungstenAbsorberPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.client.WrenchHud.acceptTungstenAbsorber(payload)));

        registrar.playToClient(
            WrenchMasteryPayload.TYPE,
            WrenchMasteryPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.item.WrenchItem.setClientMastery(payload.mastered())));
    }

    /** Максимальная дистанция (блоков), в пределах которой отвечаем на запрос. */
    private static final double MAX_DISTANCE_SQR = 8.0D * 8.0D;

    /** Радиус area-probe: 7×7×7 = 343 клетки, одна пачка ответов на область. */
    private static final int PORT_PROBE_RADIUS = 3;
    private static final int PORT_PROBE_LIMIT = 96;
    /**
     * 0.3.337: клиент запрашивает поток каждые 2 тика (цифры обновляются раз в
     * 1–2 тика), поэтому прежний лимит 8/20 тиков молча отбрасывал половину
     * запросов и строка «поток» мигала. 24/20 тиков с запасом на смену цели.
     */
    private static final int FLOW_REQUESTS_PER_WINDOW = 24;

    /** 0.3.335: один C2S-запрос → ответы по каждому реально несомому типу. */
    private static void respondAll(ServerPlayer player, BlockPos pos, boolean probe) {
        ServerLevel level = player.serverLevel();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_DISTANCE_SQR) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeCarrier carrier)) return;
        int owner = com.gonzotech.machines.FormedPorts.ownerId(level, pos, state);
        PacketDistributor.sendToPlayer(player, new PortInfoPayload(pos, owner));
        // При смене цели один раз разрешаем ВСЮ видимую область: клиент кэширует
        // каждую позицию, поэтому переключение между узлами уже не ждёт сети.
        if (probe && com.gonzotech.core.network.PayloadThrottle.allow(player, "pipePortArea", 4)) {
            int sent = 0;
            for (int dy = -PORT_PROBE_RADIUS; dy <= PORT_PROBE_RADIUS && sent < PORT_PROBE_LIMIT; dy++) {
                for (int dx = -PORT_PROBE_RADIUS; dx <= PORT_PROBE_RADIUS && sent < PORT_PROBE_LIMIT; dx++) {
                    for (int dz = -PORT_PROBE_RADIUS; dz <= PORT_PROBE_RADIUS && sent < PORT_PROBE_LIMIT; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos near = pos.offset(dx, dy, dz);
                        BlockState nearState = level.getBlockState(near);
                        if (!(nearState.getBlock() instanceof PipeCarrier)) continue;
                        PacketDistributor.sendToPlayer(player, new PortInfoPayload(near,
                            com.gonzotech.machines.FormedPorts.ownerId(level, near, nearState)));
                        sent++;
                    }
                }
            }
        }
        for (PipeType type : PipeType.values()) {
            if (carrier.carries(state, type)) respond(player, pos, type.ordinal());
        }
    }

    /**
     * 0.3.335: счётчик сшитых узлов для HUD. У вшитого порта собственный размер
     * всегда 0 (он исключён из клампов), поэтому показываем ближайший кламп
     * СНАРУЖИ корпуса: «поставил два узла на узел корпуса → Сшито 2».
     */
    private static int clumpSizeForHud(ServerLevel level, BlockPos pos, BlockState state) {
        int size = NodeClumpIndex.sizeAt(level, pos);
        if (size > 0 || !com.gonzotech.machines.FormedPorts.isEmbeddedPort(level, pos, state)) return size;
        for (Direction direction : Direction.values()) {
            int neighbour = NodeClumpIndex.sizeAt(level, pos.relative(direction));
            if (neighbour > size) size = neighbour;
        }
        return size;
    }

    private static void respond(ServerPlayer player, BlockPos pos, int typeId) {
        ServerLevel level = player.serverLevel();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_DISTANCE_SQR) return;
        BlockState state = level.getBlockState(pos);
        if (typeId < 0 || typeId >= PipeType.values().length) return;
        PipeType pipeType = PipeType.values()[typeId];
        if (!(state.getBlock() instanceof PipeCarrier carrier) || !carrier.carries(state, pipeType)) return;

        long[] flow = FlowTracker.get(level, pos, pipeType);

        // 0.3.102: hover ЛЮБОГО члена клампа показывает поток ВСЕГО клампа.
        // 0.3.101 считал сумму ГРАНИЧНЫХ граней — а в главном кейсе (порт
        // парогенератора = сам узел клампа) путь заканчивается ВНУТРИ клампа,
        // граничных записей нет вовсе, и поток «пропадал» (репорт автора
        // 02.10). Сохранение потока: каждый член НА пути несёт весь транзит
        // клампа своей суммой граней — берём максимум по членам (не сумму:
        // внутренние грани задвоили бы транзит). Потери — плоские клампа.
        if (NodeClumpIndex.isMember(level, pos)) {
            long clumpSum = 0;
            for (BlockPos m : NodeClumpIndex.membersOf(level, pos)) {
                long own = 0;
                for (long v : FlowTracker.get(level, m, pipeType)) own += v;
                if (own > clumpSum) clumpSum = own;
            }
            PacketDistributor.sendToPlayer(player, new FlowPayload(pos, typeId, AXIS_NODE_SUM, clumpSum, 0,
                NodeClumpIndex.lossMilliOfRoot(level, NodeClumpIndex.rootOf(level, pos)),
                clumpSizeForHud(level, pos, state)));
            return;
        }

        // 0.3.104: «влево/вправо» мертво с 0.3.73 (клиент всё равно суммирует
        // концы) — отвечаем суммой ВСЕХ ШЕСТИ граней трубы. Это устраняет целый
        // класс «нулевых» ответов, когда ось блока не совпала с направлением
        // фактической записи потока (репорт автора: теплотруба II к парогену
        // «по ней 100% есть ток», HUD молчал).
        long total = 0;
        for (long v : flow) total += v;
        PacketDistributor.sendToPlayer(player, new FlowPayload(pos, typeId, AXIS_NODE_SUM, total, 0,
            FlowTracker.getLoss(level, pos, pipeType), clumpSizeForHud(level, pos, state)));
    }

    /** Отвечает на запрос потока ПРЕДМЕТОВ в предметной трубе (топ по количеству). */
    private static void respondItemFlow(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_DISTANCE_SQR) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeCarrier carrier) || !carrier.carries(state, PipeType.ITEM)) return;

        java.util.Map<net.minecraft.world.item.Item, Integer> flow = ItemFlowTracker.get(level, pos);
        // Топ по количеству, максимум 5 строк — чтобы HUD не разрастался.
        java.util.List<java.util.Map.Entry<net.minecraft.world.item.Item, Integer>> entries =
            new java.util.ArrayList<>(flow.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        java.util.List<ResourceLocation> items = new java.util.ArrayList<>();
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        int limit = Math.min(5, entries.size());
        for (int i = 0; i < limit; i++) {
            items.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entries.get(i).getKey()));
            counts.add(entries.get(i).getValue());
        }
        PacketDistributor.sendToPlayer(player, new ItemFlowPayload(pos, items, counts));
    }

    /** Отвечает содержимым транзитной очереди Filter I/II для HUD гаечного ключа. */
    private static void respondFilterStorage(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_DISTANCE_SQR) return;
        if (!(level.getBlockState(pos).getBlock() instanceof ItemFilterBlock)) return;
        if (!(level.getBlockEntity(pos) instanceof ItemFilterBlockEntity filter)) return;

        // LinkedHashMap сохраняет порядок первых занятых слотов в очереди: HUD
        // читается как реальное содержимое буфера, а одинаковые Item суммируются.
        java.util.Map<net.minecraft.world.item.Item, Integer> queued = new java.util.LinkedHashMap<>();
        for (int slot = 0; slot < filter.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack stack = filter.getItem(slot);
            if (!stack.isEmpty()) queued.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }

        java.util.List<ResourceLocation> items = new java.util.ArrayList<>();
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        for (java.util.Map.Entry<net.minecraft.world.item.Item, Integer> entry : queued.entrySet()) {
            items.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()));
            counts.add(entry.getValue());
        }
        PacketDistributor.sendToPlayer(player, new FilterStoragePayload(pos, items, counts));
    }

    /** Sends an authoritative wrench reading only for the special tungsten storage block. */
    private static void respondTungstenAbsorber(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_DISTANCE_SQR) return;
        if (!level.getBlockState(pos).is(ModBlocks.TUNGSTEN_ABSORBER.get())) return;
        if (!(level.getBlockEntity(pos) instanceof TungstenAbsorberBlockEntity absorber)) return;
        PacketDistributor.sendToPlayer(player, new TungstenAbsorberPayload(
            pos, absorber.storedGth(), absorber.hasHeatCarrierConnection()));
    }

    }
