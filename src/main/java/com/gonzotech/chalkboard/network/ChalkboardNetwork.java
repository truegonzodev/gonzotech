package com.gonzotech.chalkboard.network;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.core.Analysis;
import com.gonzotech.chalkboard.core.ChalkboardWorldData;
import com.gonzotech.chalkboard.core.DimVec;
import com.gonzotech.chalkboard.core.DiscoveryDef;
import com.gonzotech.chalkboard.core.Evaluator;
import com.gonzotech.chalkboard.core.Expr;
import com.gonzotech.chalkboard.core.GameSolver;
import com.gonzotech.chalkboard.core.Quantities;
import com.gonzotech.chalkboard.core.Quantity;
import com.gonzotech.chalkboard.core.Serde;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public class ChalkboardNetwork {

    // ───────────────────────── Payloads ─────────────────────────

    public record SyncRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "chalkboard_sync_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncRequestPayload> STREAM_CODEC =
                StreamCodec.unit(new SyncRequestPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveExprPayload(int discoveryIndex, String exprJson, String drawingJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SaveExprPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "chalkboard_save_expr"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SaveExprPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, SaveExprPayload::discoveryIndex,
                        ByteBufCodecs.stringUtf8(262144), SaveExprPayload::exprJson,
                        ByteBufCodecs.stringUtf8(262144), SaveExprPayload::drawingJson,
                        SaveExprPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SubmitPayload(int discoveryIndex, String exprJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SubmitPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "chalkboard_submit"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SubmitPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, SubmitPayload::discoveryIndex,
                        ByteBufCodecs.stringUtf8(262144), SubmitPayload::exprJson,
                        SubmitPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncDataPayload(
            int currentDiscoveryIndex,
            String titleRu,
            String titleEn,
            String targetId,
            String targetSymbol,
            String targetNameRu,
            String targetNameEn,
            String targetUnit,
            String exprJson,
            String drawingJson,
            int trayTier,
            List<String> unlockedSecrets,
            boolean cheatsEnabled
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncDataPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "chalkboard_sync_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncDataPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, val) -> {
                            buf.writeVarInt(val.currentDiscoveryIndex());
                            buf.writeUtf(val.titleRu(), 262144);
                            buf.writeUtf(val.titleEn(), 262144);
                            buf.writeUtf(val.targetId(), 262144);
                            buf.writeUtf(val.targetSymbol(), 262144);
                            buf.writeUtf(val.targetNameRu(), 262144);
                            buf.writeUtf(val.targetNameEn(), 262144);
                            buf.writeUtf(val.targetUnit(), 262144);
                            buf.writeUtf(val.exprJson(), 262144);
                            buf.writeUtf(val.drawingJson(), 262144);
                            buf.writeVarInt(val.trayTier());
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, val.unlockedSecrets());
                            buf.writeBoolean(val.cheatsEnabled());
                        },
                        buf -> new SyncDataPayload(
                                buf.readVarInt(),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readUtf(262144),
                                buf.readVarInt(),
                                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf),
                                buf.readBoolean()
                        )
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // Client-side listener handle
    public static volatile SyncDataPayload CLIENT_DATA = null;

    // ───────────────────────── Registration ─────────────────────────

    public static void register(PayloadRegistrar registrar) {
        // C2S Sync Request
        registrar.playToServer(
                SyncRequestPayload.TYPE,
                SyncRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        sendSyncToPlayer(player);
                    }
                })
        );

        // C2S Save Expr & Drawing
        registrar.playToServer(
                SaveExprPayload.TYPE,
                SaveExprPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
                        progress.setSavedExpr(payload.discoveryIndex(), payload.exprJson());
                        progress.setGlobalDrawingJson(payload.drawingJson());
                        player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
                    }
                })
        );

        // C2S Submit Solution
        registrar.playToServer(
                SubmitPayload.TYPE,
                SubmitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleSubmission(player, payload.discoveryIndex(), payload.exprJson());
                    }
                })
        );

        // S2C Sync Data
        registrar.playToClient(
                SyncDataPayload.TYPE,
                SyncDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    CLIENT_DATA = payload;
                })
        );
    }

    public static void sendSyncToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        int discIdx = progress.getCurrentDiscoveryIndex();

        ChalkboardWorldData worldData = ChalkboardWorldData.get(level);
        GameSolver.Puzzle puzzle = worldData.getPuzzle(discIdx);

        String titleRu;
        String titleEn;
        if (discIdx < 16) {
            DiscoveryDef def = DiscoveryDef.get(discIdx);
            titleRu = "Открытие " + (discIdx + 1) + "/16: " + def.titleRu();
            titleEn = "Discovery " + (discIdx + 1) + "/16: " + def.titleEn();
        } else {
            int stage = discIdx + 1;
            titleRu = "Бесконечный резонанс (Стадия " + stage + ")";
            titleEn = "Infinite Resonance (Stage " + stage + ")";
        }

        String savedExpr = progress.getSavedExpr(discIdx);
        String exprJson = (savedExpr != null && !savedExpr.isEmpty()) ? savedExpr : Serde.toJson(puzzle.expr());

        String drawingJson = progress.getGlobalDrawingJson();

        Quantity target = puzzle.target();
        boolean cheats = player.isCreative() || player.hasPermissions(2);

        SyncDataPayload payload = new SyncDataPayload(
                discIdx,
                titleRu,
                titleEn,
                target.id(),
                target.symbol(),
                target.nameRu(),
                target.nameEn(),
                target.unit(),
                exprJson,
                drawingJson,
                progress.getTrayQuantityTier(),
                new ArrayList<>(progress.getUnlockedSecretQuantities()),
                cheats
        );

        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void handleSubmission(ServerPlayer player, int discoveryIndex, String exprJson) {
        ServerLevel level = player.serverLevel();
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);

        Expr expr = Serde.fromJson(exprJson);
        if (expr == null) return;

        Analysis analysis = Evaluator.analyze(expr);
        if (analysis.sD != null && analysis.sD >= 90.0 && analysis.conflicts.isEmpty()) {
            checkSecretUnlocks(player, progress, analysis);

            if (discoveryIndex == progress.getCurrentDiscoveryIndex()) {
                boolean isInfiniteMode = progress.isInfiniteMode();
                int currentStage = progress.getCurrentDiscoveryIndex() + 1;

                progress.advanceDiscovery();
                player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);

                if (!isInfiniteMode && discoveryIndex == 15) {
                    // Just completed Discovery 16! (0-indexed 15)
                    // Award consumable Discovery item 16 ONCE
                    int awardNum = 16;
                    ItemStack awardStack = new ItemStack(ModItems.getDiscoveryItem(awardNum).get());
                    if (!player.getInventory().add(awardStack)) {
                        player.drop(awardStack, false);
                    }

                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);

                    player.displayClientMessage(
                            Component.translatable("gui.gonzotech.chalkboard.discovery_prefix", awardNum)
                                    .append(" ")
                                    .append(Component.translatable("item.gonzotech.discovery_" + awardNum))
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                } else if (!isInfiniteMode && discoveryIndex < 15) {
                    // Completed Discovery 1..15
                    int awardNum = discoveryIndex + 1;
                    ItemStack awardStack = new ItemStack(ModItems.getDiscoveryItem(awardNum).get());
                    if (!player.getInventory().add(awardStack)) {
                        player.drop(awardStack, false);
                    }

                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);

                    player.displayClientMessage(
                            Component.translatable("gui.gonzotech.chalkboard.discovery_prefix", awardNum)
                                    .append(" ")
                                    .append(Component.translatable("item.gonzotech.discovery_" + awardNum))
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                } else {
                    // Infinite Mode completion (Stage 17+)!
                    long xpSeed = level.getSeed() ^ ((long) currentStage * 0x5DEECE66DL);
                    java.util.Random xpRng = new java.util.Random(xpSeed);
                    int xpReward = 5000 + xpRng.nextInt(10001); // 5000 to 15000 XP

                    player.giveExperiencePoints(xpReward);

                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

                    player.displayClientMessage(
                            Component.translatable("gui.gonzotech.chalkboard.infinite_xp_award", currentStage, xpReward)
                                    .withStyle(ChatFormatting.GOLD),
                            true
                    );
                }

                sendSyncToPlayer(player);
            }
        }
    }

    private static void checkSecretUnlocks(ServerPlayer player, PlayerChalkboardProgress progress, Analysis analysis) {
        boolean unlockedAny = false;
        if (analysis.leftVec != null) unlockedAny |= checkVecForSecret(progress, analysis.leftVec);
        if (analysis.rightVec != null) unlockedAny |= checkVecForSecret(progress, analysis.rightVec);
        for (Analysis.NodeEval nodeEval : analysis.evalById.values()) {
            if (nodeEval != null && nodeEval.vec() != null) {
                unlockedAny |= checkVecForSecret(progress, nodeEval.vec());
            }
        }
        if (unlockedAny) {
            player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
        }
    }

    private static boolean checkVecForSecret(PlayerChalkboardProgress progress, DimVec vec) {
        boolean added = false;
        for (Quantity q : Quantities.ALL) {
            if ((q.tier() == 4 || q.tier() == 99) && q.vec().equals(vec)) {
                if (!progress.getUnlockedSecretQuantities().contains(q.id())) {
                    progress.unlockSecretQuantity(q.id());
                    added = true;
                }
            }
        }
        return added;
    }
}
