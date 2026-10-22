package com.gonzotech.machines.reactor;

import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * (0.3.270, автор 22.10) «Угольки» реакторов: реакция идёт и после сноса панели.
 *
 * <p>Расформировка не спасает того, кто перекормил реактор, — она забирает
 * контроль. Уголёк — это четыре скаляра (температура, инерция нагрева, наличие
 * воды, полный NBT рантайма для приёмки); он никогда не смотрит в мир: тик —
 * арифметика, в этом вся оптимизация. Нагрев идёт замороженной инерцией
 * (вода замедляет втрое, но не останавливает); при ≥10 000 °C уголёк сам
 * наводит аварию по прежнему адресу — топливо в чехлах выжигает авария.
 * Остыв до среды, уголёк гаснет сам: «призрачных BE» не существует by design.
 * Пересобранная панель с ровно теми же границами принимает состояние уголька —
 * снос панели не способ сбросить параметры.</p>
 */
public final class ReactorEmberData extends SavedData {

    public static final String DATA_NAME = "gonzotech_reactor_embers";
    /** Допуск догорания: на уровне среды ± COOL_TOLERANCE уголёк считается погасшим. */
    private static final double EXTINGUISHED_MARGIN = ReactorHeatDynamics.COOL_TOLERANCE;

    private static final class Ember {
        long minPos;
        long maxPos;
        double hot;
        double velocity;
        int water;
        CompoundTag state;

        boolean alive() {
            return hot > ReactorHeatDynamics.AMBIENT + EXTINGUISHED_MARGIN;
        }
    }

    private final List<Ember> embers = new ArrayList<>();

    public static ReactorEmberData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(ReactorEmberData::new,
                (tag, provider) -> load(tag), null),
            DATA_NAME);
    }

    private static ReactorEmberData load(CompoundTag tag) {
        ReactorEmberData data = new ReactorEmberData();
        ListTag list = tag.getList("Embers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            Ember ember = new Ember();
            ember.minPos = e.getLong("Min");
            ember.maxPos = e.getLong("Max");
            ember.hot = e.getDouble("Hot");
            ember.velocity = e.getDouble("Velocity");
            ember.water = e.getInt("Water");
            if (e.contains("State", Tag.TAG_COMPOUND)) ember.state = e.getCompound("State");
            data.embers.add(ember);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Ember ember : embers) {
            CompoundTag e = new CompoundTag();
            e.putLong("Min", ember.minPos);
            e.putLong("Max", ember.maxPos);
            e.putDouble("Hot", ember.hot);
            e.putDouble("Velocity", ember.velocity);
            e.putInt("Water", ember.water);
            if (ember.state != null) e.put("State", ember.state);
            list.add(e);
        }
        tag.put("Embers", list);
        return tag;
    }

    /** При сносе панели: горячий рантайм уходит в уголёк (та же сборка — заменяет). */
    public static void capture(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (!panel.emberWorthy()) return;
        BlockPos min = panel.min();
        BlockPos max = panel.max();
        if (min == null || max == null) return;
        ReactorEmberData data = get(level);
        Ember ember = new Ember();
        ember.minPos = min.asLong();
        ember.maxPos = max.asLong();
        ember.hot = panel.emberHot();
        ember.velocity = panel.emberVelocity();
        ember.water = panel.emberWater();
        ember.state = panel.captureEmberState();
        data.embers.removeIf(e -> e.minPos == ember.minPos && e.maxPos == ember.maxPos);
        data.embers.add(ember);
        data.setDirty();
    }

    /** Пересборка ровно на том же месте: приёмка состояния уголька. */
    public static CompoundTag adopt(ServerLevel level, BlockPos min, BlockPos max) {
        ReactorEmberData data = get(level);
        for (Iterator<Ember> it = data.embers.iterator(); it.hasNext(); ) {
            Ember e = it.next();
            if (e.minPos == min.asLong() && e.maxPos == max.asLong()) {
                it.remove();
                data.setDirty();
                return e.state;
            }
        }
        return null;
    }

    /** Тик угольков: ноль чтений мира. */
    void tick(ServerLevel level, long gameTime) {
        if (embers.isEmpty()) return;
        boolean dirty = false;
        for (Iterator<Ember> it = embers.iterator(); it.hasNext(); ) {
            Ember e = it.next();
            double before = e.hot;
            if (e.velocity > 0.0) {
                // Замороженная инерция разгона: вода (её больше некому подливать)
                // втрое тормозит, но не спасает.
                e.hot += Math.max(1.0, e.velocity * (e.water > 0 ? 0.35 : 1.0));
            } else {
                e.hot -= Math.max(1.0, (e.hot - ReactorHeatDynamics.AMBIENT) * 0.02);
            }
            if (e.hot < ReactorHeatDynamics.AMBIENT) e.hot = ReactorHeatDynamics.AMBIENT;
            if (e.hot >= ReactorRuntime.MELTDOWN_TEMPERATURE) {
                // «Ярдо плавится»: уголёк наводит аварию по прежнему адресу,
                // топливо в чехлах выжигает уже авария.
                BlockPos min = BlockPos.of(e.minPos);
                BlockPos max = BlockPos.of(e.maxPos);
                BlockPos hottest = new BlockPos((min.getX() + max.getX()) / 2,
                    (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
                ReactorAccidentData.get(level).enqueue(level, new ReactorAccident(
                    ReactorAccident.nearestShellBlock(hottest, min, max), hottest, min, max, gameTime));
                it.remove();
                dirty = true;
                continue;
            }
            if (!e.alive()) {
                it.remove();
                dirty = true;
                continue;
            }
            if (e.hot != before) dirty = true;
        }
        if (dirty) setDirty();
    }
}
