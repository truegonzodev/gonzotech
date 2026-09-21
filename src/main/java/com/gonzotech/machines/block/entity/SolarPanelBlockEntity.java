package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.ComparatorOutput;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.gonzotech.sunevent.SunEventData;
import com.gonzotech.sunevent.SunEventNetwork;
import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Солнечная панель (тир 1, открытие 4 — четвёртое открытие, не паровая эра).
 * Автор (2026-09-18): пик <b>15 GTU/t</b> в зените ясного дня, нулевого дня мира;
 * буфер <b>126 GTU</b>, макс. отдача <b>64 GTU/t</b>. «Выше только небо» —
 * обязательно; любой блок над панелью глушит выработку полностью.
 *
 * <p>Выработка за тик = {@code 15 GTU × light × crimson × weather × age}:
 * <ul>
 *   <li>{@code light} — кривая неба по возвышению солнца
 *       {@code e = cos(2π·(timeOfDay/24000 − 0.25))} (e=1 в полдень, 0 у
 *       горизонта, −1 в полночь): днём {@code 0.6 + 0.4·e} (~60% на
 *       рассвете/закате, плавно из зенита); ночью плавный откат
 *       (кубический smoothstep) от 0.6 у горизонта до 0.02 к моменту, когда
 *       солнце опустилось на ~0.13 (=~540 тиков после захода), глубже — 0.02;</li>
 *   <li>{@code crimson} — ×{@value MachineDefs#SOLAR_PANEL_CRIMSON_FACTOR}
 *       в багровый день E (тусклое солнце суневета);</li>
 *   <li>{@code weather} — ×{@value MachineDefs#SOLAR_PANEL_WEATHER_FACTOR}
 *       при любых осадках (дождь/гроза/снег);</li>
 *   <li>{@code age} — {@link SunEventData#solarMultiplier()}: −1% за полный
 *       день мира, пол 10%; «Икар» обнуляет (молодое солнце снова даёт 100%).
 *       Читается из данных Оверворлда в любом измерении.</li>
 * </ul>
 *
 * <p>GTU накапливается в буфере (BigInteger, милли) и раздаётся соседям и по
 * проводам равномерно — как у стирлинга, той же механикой {@link PipeRouting}.
 * Панель панели напрямую заряжать не умеет (однотип к однотипу не идёт).
 */
public class SolarPanelBlockEntity extends BlockEntity {

    /** GTU — в GtBuffer (BigInteger, милли), как у остальной энергетики. */
    private final GtBuffer gtu = new GtBuffer(MachineDefs.SOLAR_PANEL_GTU_CAPACITY);

    /** Последнее опубликованное значение компаратора; вычисляется заново после загрузки. */
    private int lastComparatorOutput;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    /** Аналоговый выход по заполненности буфера GTU. */
    public int comparatorOutput() {
        return ComparatorOutput.from(gtu);
    }

    /** Уведомляет компараторы только при пересечении очередной ступени 0..15. */
    private void updateComparatorOutput() {
        int next = comparatorOutput();
        if (next == lastComparatorOutput) return;
        lastComparatorOutput = next;
        if (level != null && !level.isClientSide()) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateComparatorOutput();
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        long generated = generationMilliPerTick(server, pos);
        if (generated > 0 && be.gtu.receive(generated, false) > 0) {
            changed = true;
        }
        if (be.pushGtu(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
            be.updateComparatorOutput();
        }
    }

    /**
     * Выработка за тик в mGTU по авторской формуле (см. шапку). Ноль — если над
     * панелью не небо.
     */
    static long generationMilliPerTick(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }

        double timeOfDay = (double) (level.getDayTime() % 24000L);
        double elevation = Math.cos(2.0D * Math.PI * (timeOfDay / 24000.0D - 0.25D));
        double light;
        if (elevation >= 0) {
            // День: от горизонта (0.6) к зениту (1.0), плавно по возвышению.
            light = MachineDefs.SOLAR_PANEL_HORIZON_FACTOR
                + (1.0D - MachineDefs.SOLAR_PANEL_HORIZON_FACTOR) * elevation;
        } else if (elevation >= MachineDefs.SOLAR_PANEL_NIGHT_ELEVATION) {
            // Сумерки: smoothstep от горизонтного 0.6 к ночному 0.02.
            double s = (elevation - MachineDefs.SOLAR_PANEL_NIGHT_ELEVATION)
                / (0.0D - MachineDefs.SOLAR_PANEL_NIGHT_ELEVATION);
            s = s * s * (3.0D - 2.0D * s);
            light = MachineDefs.SOLAR_PANEL_NIGHT_FACTOR
                + (MachineDefs.SOLAR_PANEL_HORIZON_FACTOR - MachineDefs.SOLAR_PANEL_NIGHT_FACTOR) * s;
        } else {
            light = MachineDefs.SOLAR_PANEL_NIGHT_FACTOR;
        }

        // Багровый день E — только явление Оверворлда.
        double crimson = level.dimension() == Level.OVERWORLD && SunEventServer.eventDayNow(level)
            ? MachineDefs.SOLAR_PANEL_CRIMSON_FACTOR : 1.0D;
        double weather = level.isRaining() ? MachineDefs.SOLAR_PANEL_WEATHER_FACTOR : 1.0D;

        // Деградация умирающего Солнца читается из данных Оверворлда глобально.
        double age = 1.0D;
        if (level.getServer() != null) {
            SunEventData data = SunEventNetwork.getData(level.getServer().overworld());
            age = data.solarMultiplier();
        }

        return (long) (MachineDefs.SOLAR_PANEL_GTU_PEAK_PER_TICK * light * crimson * weather * age);
    }

    /** Равномерно раздать GTU соседям-приёмникам и по проводам (как стирлинг). */
    private boolean pushGtu(Level level, BlockPos pos) {
        if (gtu.isEmpty()) return false;
        long budget = Math.min((long) MachineDefs.SOLAR_PANEL_GTU_OUTPUT, gtu.amountAsLong());
        long moved = PipeRouting.drain(level, pos, PipeType.WIRE, budget, level.getGameTime(), (be, p) -> {
            if (be instanceof SolarPanelBlockEntity) return null;
            if (be instanceof GtuSink sink) return sink::receiveGtu;
            return null;
        });
        if (moved > 0) {
            gtu.extract(moved, false);
            return true;
        }
        return false;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
    }
}
