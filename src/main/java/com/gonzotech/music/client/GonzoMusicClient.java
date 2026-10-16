package com.gonzotech.music.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModSounds;
import com.gonzotech.mixin.client.MusicManagerAccessor;
import com.gonzotech.space.SpaceDimensions;
import com.gonzotech.sunevent.client.SunEventClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.UUID;

/** Client-side music pools, conditional OST triggers, and the debug music roll. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class GonzoMusicClient {

    private static final int DEFAULT_MIN_DELAY_TICKS = 12_000;
    private static final int DEFAULT_MAX_DELAY_TICKS = 24_000;
    private static final long UNDERGROUND_CHECK_INTERVAL_TICKS = 1_200L;
    private static final long DEFAULT_MUSIC_WINDOW_NANOS = 5L * 60L * 1_000_000_000L;
    private static final long RAIDIN_BONES_WINDOW_NANOS = 8L * 60L * 1_000_000_000L;
    private static final int OVERWORLD_BITTER_CHANCE_PERCENT = 50;
    private static final int RAIDIN_BONES_CHANCE_PERCENT = 20;
    private static final int UNDERGROUND_CHANCE_PERCENT = 10;

    private record MusicRoll(List<DeferredHolder<SoundEvent, SoundEvent>> pool, int chancePercent) {
    }

    private static long musicWindowUntilNanos;
    private static Object trackedConnection;
    private static UUID trackedPlayer;
    private static long handledCrimsonEventDay = Long.MIN_VALUE;
    private static ResourceKey<Level> undergroundDimension;
    private static long nextUndergroundAttemptTick = Long.MIN_VALUE;

    private GonzoMusicClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Object connection = minecraft.getConnection();
        if (connection != trackedConnection) {
            reset();
            trackedConnection = connection;
        }

        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            trackedPlayer = null;
            undergroundDimension = null;
            nextUndergroundAttemptTick = Long.MIN_VALUE;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!playerId.equals(trackedPlayer)) {
            trackedPlayer = playerId;
            undergroundDimension = null;
            nextUndergroundAttemptTick = Long.MIN_VALUE;
        }

        checkCrimsonSunStart(minecraft, level, player);
        checkUndergroundAttempt(minecraft, level, player);
    }

    /**
     * Suppress a start request while another track is active or its minimum play
     * window has not elapsed. The existing track is left untouched.
     */
    public static boolean shouldSuppressMusicStart(Minecraft minecraft) {
        return isMusicPlaying(minecraft);
    }

    /** Start a track-specific cooldown after MusicManager actually starts playback. */
    public static void recordMusicStart(Minecraft minecraft, MusicInfo startedMusic) {
        if (startedMusic == null || !isMusicPlayingFromManager(minecraft)) {
            return;
        }

        SoundEvent sound = startedMusic.music().getEvent().value();
        long window = sound == ModSounds.GT_OST_RAIDIN_BONES.get()
                ? RAIDIN_BONES_WINDOW_NANOS
                : DEFAULT_MUSIC_WINDOW_NANOS;
        musicWindowUntilNanos = System.nanoTime() + window;
    }

    /** Run one context-appropriate music roll requested by the server debug command. */
    public static void debugTriggerMusic() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        MusicRoll roll = debugMusicRoll(level, player);
        // Выполняем именно бросок даже при занятом плеере: блокировка playback
        // меняет итог на провал, но не скрывает шанс/пул тестируемого броска.
        boolean chanceSucceeded = player.getRandom().nextInt(100) < roll.chancePercent();
        String failureReason = musicStartBlockReason(minecraft);
        boolean success = false;
        if (failureReason == null && roll.pool().isEmpty()) {
            failureReason = "нет подходящей музыки в текущем контексте";
        } else if (failureReason == null && chanceSucceeded) {
            DeferredHolder<SoundEvent, SoundEvent> selected =
                    roll.pool().get(player.getRandom().nextInt(roll.pool().size()));
            playOst(minecraft, selected);
            success = true;
        }

        String result = success ? "успех" : "провал";
        if (failureReason != null) {
            result += " (причина: " + failureReason + ")";
        }
        player.displayClientMessage(Component.literal("Попытка создать музыку — "
                + roll.chancePercent() + "% шанс — " + result)
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        String pool = roll.pool().isEmpty() ? "(пусто)" : String.join(", ", roll.pool().stream()
                .map(sound -> BuiltInRegistries.SOUND_EVENT.getKey(sound.get()).toString())
                .toList());
        player.displayClientMessage(Component.literal("Пул музыки для этого броска был: " + pool)
                .withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * Pool/chance match the existing music contexts: 50% Bitter in the ordinary
     * Overworld, 20% Raid in Bones in the crimson-sun window, and 10% 40000FT
     * underground on the supported moons. Biome OSTs are unconditional entries
     * in their biome music pools, so their manual debug roll is 100%.
     */
    private static MusicRoll debugMusicRoll(ClientLevel level, Player player) {
        ResourceKey<Level> dimension = level.dimension();
        if (dimension.equals(SpaceDimensions.SOLAR_ORBIT)
                || dimension.equals(SpaceDimensions.ALPHA_CENTAURI_ORBIT)) {
            return new MusicRoll(List.of(ModSounds.GT_OST_CRADLE), 100);
        }
        if (dimension.equals(SpaceDimensions.DEEP_SPACE)
                || dimension.equals(SpaceDimensions.BLACKHOLE_YX989_K2)
                || dimension.equals(SpaceDimensions.BLACKHOLE_ZANGLER_11)) {
            return new MusicRoll(List.of(ModSounds.GT_OST_ETHEREAL), 100);
        }

        double y = player.getY();
        boolean moonOrMars = dimension.equals(SpaceDimensions.MOON)
                || dimension.equals(SpaceDimensions.MARS);
        if (moonOrMars && y >= 54.0D && y <= 124.0D
                && SunEventClient.nextEventDay > 0 && SunEventClient.crimsonSunWindow(level)) {
            return new MusicRoll(List.of(ModSounds.GT_OST_RAIDIN_BONES), RAIDIN_BONES_CHANCE_PERCENT);
        }
        boolean supportedUndergroundDimension = moonOrMars || dimension.equals(SpaceDimensions.EUROPA);
        if (supportedUndergroundDimension && y < 54.0D) {
            return new MusicRoll(List.of(ModSounds.GT_OST_40000FT), UNDERGROUND_CHANCE_PERCENT);
        }
        if (dimension.equals(Level.OVERWORLD) && !player.getAbilities().instabuild) {
            return new MusicRoll(List.of(ModSounds.GT_OST_BITTER), OVERWORLD_BITTER_CHANCE_PERCENT);
        }
        return new MusicRoll(List.of(), 0);
    }

    private static String musicStartBlockReason(Minecraft minecraft) {
        if (isMusicPlayingFromManager(minecraft)) {
            return "музыка уже играет";
        }
        return isMusicCooldownActive() ? "кд" : null;
    }

    /**
     * Add Bitter as an equal-weight alternative to an ordinary Overworld song.
     * Replacing the MusicInfo passed to vanilla keeps the MusicManager's normal
     * scheduling, pause handling and music-channel playback intact.
     */
    public static MusicInfo maybeAddOverworldBitter(MusicInfo vanillaMusic) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (vanillaMusic == null || minecraft.level == null || player == null
                || !minecraft.level.dimension().equals(Level.OVERWORLD)
                || player.getAbilities().instabuild
                || isOurOst(vanillaMusic.music().getEvent().value())) {
            return vanillaMusic;
        }

        ResourceLocation selectedMusicId = vanillaMusic.music().getEvent().unwrapKey()
                .map(key -> key.location()).orElse(null);
        if (selectedMusicId == null || !"minecraft".equals(selectedMusicId.getNamespace())
                || !("music.game".equals(selectedMusicId.getPath())
                || selectedMusicId.getPath().startsWith("music.overworld."))) {
            return vanillaMusic; // Keep underwater, creative, menu and other special music untouched.
        }

        if (player.getRandom().nextBoolean()) {
            Music vanilla = vanillaMusic.music();
            Music bitter = new Music(ModSounds.GT_OST_BITTER,
                    vanilla.getMinDelay(), vanilla.getMaxDelay(), vanilla.replaceCurrentMusic());
            return new MusicInfo(bitter, vanillaMusic.volume());
        }
        return vanillaMusic;
    }

    /** True while a MusicManager track is active or its programmed minimum window remains. */
    public static boolean isMusicPlaying(Minecraft minecraft) {
        return isMusicPlayingFromManager(minecraft) || isMusicCooldownActive();
    }

    private static boolean isMusicCooldownActive() {
        long until = musicWindowUntilNanos;
        return until != 0L && System.nanoTime() - until < 0L;
    }

    private static boolean isMusicPlayingFromManager(Minecraft minecraft) {
        SoundInstance current = ((MusicManagerAccessor) (Object) minecraft.getMusicManager())
                .gonzotech$getCurrentMusic();
        return current != null && minecraft.getSoundManager().isActive(current);
    }

    private static void checkCrimsonSunStart(Minecraft minecraft, ClientLevel level, Player player) {
        long eventDay = SunEventClient.nextEventDay;
        if (eventDay <= 0L || !SunEventClient.crimsonSunWindow(level)
                || handledCrimsonEventDay == eventDay) {
            return;
        }

        // Mark the transition even when this dimension, altitude or music state is ineligible:
        // entering another dimension during the same red-sun window must not reroll the event.
        handledCrimsonEventDay = eventDay;
        ResourceKey<Level> dimension = level.dimension();
        double y = player.getY();
        boolean moonOrMars = dimension.equals(SpaceDimensions.MOON)
                || dimension.equals(SpaceDimensions.MARS);
        if (moonOrMars && y >= 54.0D && y <= 124.0D
                && !isMusicPlaying(minecraft)
                && player.getRandom().nextFloat() < RAIDIN_BONES_CHANCE_PERCENT / 100.0F) {
            playOst(minecraft, ModSounds.GT_OST_RAIDIN_BONES);
        }
    }

    private static void checkUndergroundAttempt(Minecraft minecraft, ClientLevel level, Player player) {
        ResourceKey<Level> dimension = level.dimension();
        boolean supportedDimension = dimension.equals(SpaceDimensions.MOON)
                || dimension.equals(SpaceDimensions.MARS)
                || dimension.equals(SpaceDimensions.EUROPA);
        if (!supportedDimension || player.getY() >= 54.0D) {
            undergroundDimension = null;
            nextUndergroundAttemptTick = Long.MIN_VALUE;
            return;
        }

        long gameTime = level.getGameTime();
        if (!dimension.equals(undergroundDimension)
                || nextUndergroundAttemptTick == Long.MIN_VALUE
                || gameTime < nextUndergroundAttemptTick - UNDERGROUND_CHECK_INTERVAL_TICKS) {
            undergroundDimension = dimension;
            nextUndergroundAttemptTick = gameTime + UNDERGROUND_CHECK_INTERVAL_TICKS;
            return;
        }
        if (gameTime < nextUndergroundAttemptTick) {
            return;
        }

        nextUndergroundAttemptTick = gameTime + UNDERGROUND_CHECK_INTERVAL_TICKS;
        if (!isMusicPlaying(minecraft)
                && player.getRandom().nextFloat() < UNDERGROUND_CHANCE_PERCENT / 100.0F) {
            playOst(minecraft, ModSounds.GT_OST_40000FT);
        }
    }

    private static void playOst(Minecraft minecraft,
                                DeferredHolder<SoundEvent, SoundEvent> sound) {
        // MusicManager creates a non-positional MusicInstance on SoundSource.MUSIC;
        // the OGG assets are stereo streams and obey the vanilla music volume control.
        Music music = new Music(sound, DEFAULT_MIN_DELAY_TICKS, DEFAULT_MAX_DELAY_TICKS, false);
        minecraft.getMusicManager().startPlaying(new MusicInfo(music, 1.0F));
    }

    private static boolean isOurOst(SoundEvent sound) {
        return sound == ModSounds.GT_OST_40000FT.get()
                || sound == ModSounds.GT_OST_BITTER.get()
                || sound == ModSounds.GT_OST_CRADLE.get()
                || sound == ModSounds.GT_OST_ETHEREAL.get()
                || sound == ModSounds.GT_OST_RAIDIN_BONES.get();
    }

    private static void reset() {
        trackedPlayer = null;
        handledCrimsonEventDay = Long.MIN_VALUE;
        undergroundDimension = null;
        nextUndergroundAttemptTick = Long.MIN_VALUE;
        musicWindowUntilNanos = 0L;
    }
}
