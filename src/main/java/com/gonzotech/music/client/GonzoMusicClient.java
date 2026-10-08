package com.gonzotech.music.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModSounds;
import com.gonzotech.mixin.client.MusicManagerAccessor;
import com.gonzotech.space.SpaceDimensions;
import com.gonzotech.sunevent.client.SunEventClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicInfo;
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

import java.util.UUID;

/** Client-side music pool additions and the two conditional OST triggers. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class GonzoMusicClient {

    private static final int DEFAULT_MIN_DELAY_TICKS = 12_000;
    private static final int DEFAULT_MAX_DELAY_TICKS = 24_000;
    private static final long UNDERGROUND_CHECK_INTERVAL_TICKS = 1_200L;
    private static final long DEFAULT_MUSIC_WINDOW_NANOS = 5L * 60L * 1_000_000_000L;
    private static final long RAIDIN_BONES_WINDOW_NANOS = 8L * 60L * 1_000_000_000L;

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
        if (isMusicPlayingFromManager(minecraft)) {
            return true;
        }

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
                && !isMusicPlaying(minecraft) && player.getRandom().nextFloat() < 0.20F) {
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
        if (!isMusicPlaying(minecraft) && player.getRandom().nextFloat() < 0.10F) {
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
