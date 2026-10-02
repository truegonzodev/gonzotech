package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.blastfurnace.BlastFurnaceStructure;
import com.gonzotech.machines.energy.ComparatorOutput;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.menu.FireboxMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Топка — печка (вход-нагрузка, топливо, выход) + шкала GTH. С 0.3.65 топка —
 * ещё и контроллер ДОМЕННОЙ ПЕЧИ: в структуре 3×3×3 (шамот + узлы + котёл)
 * она получает 5 топливных слотов, 34 GTH/t, жжение топлива ×4 и вывод GTH
 * через узлы; см. {@link com.gonzotech.machines.blastfurnace.BlastFurnaceStructure}.
 * <p>
 * <ul>
 *   <li>Топливо горит «ванильную» длительность (уголь 80с и т.д.) и наполняет
 *       буфер {@code GTH} на {@link MachineDefs#FIREBOX_GTH_PER_TICK}/t
 *       (независимо от вида топлива).</li>
 *   <li>Нагрузка плавится ВСЕГДА, пока горит топливо — даже при полной шкале GTH
 *       и когда GTH никуда не вытекает. Скорость плавки зависит от запаса GTH
 *       (см. {@link MachineDefs#fireboxSpeedPermille}). GTH на переплавку не тратится.</li>
 *   <li>GTH раздаётся соседям равномерно, максимум
 *       {@link MachineDefs#FIREBOX_GTH_OUTPUT}/t.</li>
 *   <li>Паразитная потеря {@link MachineDefs#FIREBOX_GTH_LOSS} GTH/t — всегда.</li>
 * </ul>
 */
public class FireboxBlockEntity extends BaseMachineBlockEntity
    implements GthSink, WorldlyContainer, ExperienceOutput {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    /** Доменный режим: слоты 3..6 — дополнительное топливо (слот 1 общий). */
    public static final int SLOT_FUEL_2 = 3;
    public static final int SLOT_FUEL_3 = 4;
    public static final int SLOT_FUEL_4 = 5;
    public static final int SLOT_FUEL_5 = 6;

    /** Все топливные слоты (доменный режим: 5; слот 1 — общий с обычной топкой). */
    public static final int[] FUEL_SLOTS = {SLOT_FUEL, SLOT_FUEL_2, SLOT_FUEL_3, SLOT_FUEL_4, SLOT_FUEL_5};

    // Грани для автоматизации. Слот топлива стоит ПЕРВЫМ везде, где принимаем
    // вставку — чтобы воронка пыталась положить топливо туда раньше, чем в сырьё
    // (окончательный приоритет всё равно решает canPlaceItemThroughFace).
    private static final int[] SLOTS_TOP = {SLOT_FUEL, SLOT_INPUT};
    private static final int[] SLOTS_SIDE = {SLOT_FUEL, SLOT_INPUT};
    private static final int[] SLOTS_BOTTOM = {SLOT_OUTPUT, SLOT_FUEL};
    // Доменная печь: трубы и воронки могут ТОЛЬКО закладывать топливо (любая сторона).
    private static final int[] SLOTS_BLAST = FUEL_SLOTS;

    // GTH хранится в GtBuffer (BigInteger, милли): у топки капа мала, но единый
    // тип буфера с эндгейм-машинами и отсутствие int-потолка того стоят.
    // Ёмкость буфера — доменная (34 016); в обычном режиме приём клампится до топки.
    private final GtBuffer gth = new GtBuffer((long) MachineDefs.BLAST_FURNACE_GTH_CAPACITY);

    /** Доменный режим: печь собрана в структуру 3×3×3 (проверка раз в 20 тиков). */
    private boolean blastFormed;
    /** Тик последней проверки структуры; Long.MIN_VALUE — кэш сброшен. */
    private long blastCheckTick = Long.MIN_VALUE;

    /** Эффективная ёмкость буфера в текущем режиме, mGTH. */
    public long gthCapacityMilli() {
        return blastFormed
            ? (long) MachineDefs.BLAST_FURNACE_GTH_CAPACITY
            : (long) MachineDefs.FIREBOX_GTH_CAPACITY;
    }

    /** Сбросить кэш структуры (часть изменена) — перепроверка в ближайший тик. */
    public void invalidateBlastCache() {
        blastCheckTick = Long.MIN_VALUE;
    }

    /**
     * Немедленная перепроверка структуры, БЕЗ троттлинга: вызывается из
     * {@link BlastFurnaceStructure#partChanged} при постановке/ломании части —
     * сборка и разбор происходят в тот же игровой тик (как у турбины/парогена).
     */
    public void revalidateNow() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        blastCheckTick = Long.MIN_VALUE;
        revalidateBlast(server);
    }

    /** Собрана ли доменная печь вокруг этой топки. */
    public boolean isBlastFormed() {
        return blastFormed;
    }

    private void revalidateBlast(ServerLevel server) {
        long tick = server.getGameTime();
        if (blastCheckTick != Long.MIN_VALUE && tick - blastCheckTick < 20) return;
        // Ждём загрузки чанков куба: не заставляем генерировать мир при входе (0.3.66).
        if (!BlastFurnaceStructure.chunksLoaded(server, worldPosition)) return;
        blastCheckTick = tick;
        boolean formed = BlastFurnaceStructure.isFormed(server, worldPosition);
        if (formed != blastFormed) {
            blastFormed = formed;
            if (formed) {
                absorbIntoBlast(server); // 0.3.75: работавшая топка вошла в печь
            } else {
                dropAndResetAfterDeform(server); // 0.3.75: разбор — всё выпадает
            }
            BlastFurnaceStructure.applyFormedFlags(server, worldPosition, formed);
            setChanged();
        }
    }

    /**
     * Сборка печи вокруг РАБОТАВШЕЙ топки (0.3.75): содержимое штатных слотов
     * топки (нагрузка/топливо/выход) переносится в доступные 5 топливных
     * слотов печи, после чего шкала GTH, burnout и процессы обнуляются —
     * печь всегда стартует чистой, состояние обычной топки не «утаскивается»
     * в механизм.
     */
    private void absorbIntoBlast(ServerLevel server) {
        for (int slot : new int[] {SLOT_INPUT, SLOT_OUTPUT}) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            ItemStack leftover = insertIntoFuelSlots(stack);
            items.set(slot, leftover);
            if (!leftover.isEmpty()) {
                Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, leftover);
            }
        }
        gth.set(0);
        litTime = 0;
        litDuration = 0;
        cookProgress = 0;
        cookTotal = 0;
        cookAccum = 0;
    }

    /** Доливает стопку в существующие топливные стопки, затем в пустые; возвращает остаток. */
    private ItemStack insertIntoFuelSlots(ItemStack stack) {
        for (int pass = 0; pass < 2; pass++) {
            for (int slot : FUEL_SLOTS) {
                ItemStack cur = items.get(slot);
                if (pass == 0) {
                    if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, stack)) continue;
                    int room = Math.min(cur.getMaxStackSize(), getMaxStackSize()) - cur.getCount();
                    int move = Math.min(stack.getCount(), room);
                    if (move <= 0) continue;
                    cur.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) return ItemStack.EMPTY;
                } else if (cur.isEmpty()) {
                    items.set(slot, stack.copy());
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }

    /**
     * Разбор печи (0.3.75): ВСЁ содержимое выпадает наружу, шкала GTH,
     * burnout и процессы обнуляются — освободившаяся топка абсолютно пуста,
     * никакой привязки состояния механизма к блоку топки не остаётся.
     */
    private void dropAndResetAfterDeform(ServerLevel server) {
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
                Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack);
            }
        }
        gth.set(0);
        litTime = 0;
        litDuration = 0;
        cookProgress = 0;
        cookTotal = 0;
        cookAccum = 0;
    }

    /** Последнее опубликованное значение компаратора; вычисляется заново после загрузки. */
    private int lastComparatorOutput;

    /** Накопленный опыт за переплавку — выдаётся игроку при заборе результата. */
    private float storedXp;

    /** Играл ли уже звук горения в этом «сеансе» (чтобы не спамить каждый тик). */
    private int soundCooldown;

    /** Оставшееся время горения текущей единицы топлива, тиков. */
    private int litTime;
    /** Полное время горения текущей единицы топлива (для шкалы пламени). */
    private int litDuration;
    /** Прогресс переплавки нагрузки, тиков. */
    private int cookProgress;
    /** Полное время переплавки нагрузки, тиков. */
    private int cookTotal;
    /** Дробный аккумулятор скорости плавки (промилле), серверный, не синкается. */
    private int cookAccum;

    /** Интервал между звуками горящих угольков, тиков (ванильный crackle звучит ~редко). */
    private static final int SOUND_INTERVAL = 60;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                // GTH синкается в ЦЕЛЫХ единицах (÷1000): ContainerData — это short,
                // милли (до 24млн) в него не влезли бы. У этой машины капа мала, так
                // что единицы точны; эндгейм-машины пойдут через мантиссу+exp.
                case 0 -> gth.amountUnitsInt();
                case 1 -> (int) (gthCapacityMilli() / MachineDefs.MILLI);
                case 2 -> litTime;
                case 3 -> litDuration;
                case 4 -> cookProgress;
                case 5 -> cookTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gth.set(MachineDefs.toMilli(v));
                case 2 -> litTime = v;
                case 3 -> litDuration = v;
                case 4 -> cookProgress = v;
                case 5 -> cookTotal = v;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public FireboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIREBOX.get(), pos, state, 3 + MachineDefs.BLAST_FURNACE_FUEL_SLOTS);
    }

    public GtBuffer gth() {
        return gth;
    }

    /** Аналоговый выход по заполненности внутреннего GTH-буфера. */
    public int comparatorOutput() {
        return ComparatorOutput.from(gth);
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

    public ContainerData data() {
        return data;
    }

    public boolean isLit() {
        return litTime > 0;
    }

    // ─────────────────────────── GthSink ───────────────────────────

    @Override
    public long receiveGth(long amount, boolean simulate) {
        long space = gthCapacityMilli() - gth.amountAsLong();
        long accepted = gth.receive(Math.min(amount, Math.max(0, space)), simulate);
        if (!simulate && accepted > 0) {
            setChanged();
            updateComparatorOutput();
        }
        return accepted;
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FireboxBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 0. Доменная печь: перепроверка структуры раз в 20 тиков (или сразу после
        // сброса кэша из BlastFurnaceStructure.partChanged).
        // 0.3.69: вызов был потерян при чистке троттлинга — печь не собиралась никогда.
        be.revalidateBlast(server);

        // 1. Горение топлива → наполняем GTH.
        if (be.litTime > 0) {
            // 0.3.76: дым горения — вверх над топкой (у собранной печи — прямо
            // из котла), как у очистителя воздуха, но в 3–4 раза реже: одна
            // частица за тик, тип чередуется (campfire-дым / пыль цвета тлеющих
            // углей). Счёт 0 = точная скорость, а не гауссов разброс.
            boolean smoke = (server.getGameTime() & 1L) == 0L;
            server.sendParticles(smoke ? ParticleTypes.CAMPFIRE_COSY_SMOKE
                    : new DustParticleOptions(0x78726B, 1.0F),
                pos.getX() + 0.5 + (server.random.nextDouble() - 0.5) * 0.4,
                pos.getY() + 1.05,
                pos.getZ() + 0.5 + (server.random.nextDouble() - 0.5) * 0.4,
                0, 0.0, 0.18, 0.0, 1.0);
            be.litTime--;
            long perTick = be.blastFormed
                ? (long) MachineDefs.BLAST_FURNACE_GTH_PER_TICK
                : (long) MachineDefs.FIREBOX_GTH_PER_TICK;
            long space = be.gthCapacityMilli() - be.gth.amountAsLong();
            be.gth.receive(Math.min(perTick, Math.max(0, space)), false);
            changed = true;

            // Звук горящих угольков, пока топка горит — периодически, чтобы не спамить.
            if (be.soundCooldown <= 0) {
                server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
                be.soundCooldown = SOUND_INTERVAL;
            } else {
                be.soundCooldown--;
            }
        } else {
            be.soundCooldown = 0;
        }

        // Разжечь новую единицу топлива, если погасло. ВАЖНО: разжигаем даже при
        // полной шкале GTH — топка обязана плавить всегда (лишний GTH просто
        // не влезает в буфер).
        if (be.litTime <= 0 && be.wantsToBurn(server)) {
            for (int slot : FUEL_SLOTS) {
                ItemStack fuel = be.items.get(slot);
                int burn = fuel.getBurnTime(RecipeType.SMELTING, server.fuelValues());
                if (burn <= 0) continue;
                if (be.blastFormed) {
                    // Доменная печь: топливо горит в 4 раза быстрее ванили.
                    burn = Math.max(1, burn / MachineDefs.BLAST_FURNACE_BURN_SPEED_DIVISOR);
                }
                be.litTime = burn;
                be.litDuration = burn;
                ItemStack container = fuel.getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty() && !container.isEmpty()) {
                    be.items.set(slot, container);
                }
                changed = true;
                break;
            }
        }

        // 2. Паразитная потеря GTH — всегда.
        if (!be.gth.isEmpty()) {
            be.gth.extract(MachineDefs.FIREBOX_GTH_LOSS, false);
            changed = true;
        }

        // 3. Плавка нагрузки — идёт, пока горит топка (GTH не тратится).
        //    Скорость зависит от запаса GTH.
        if (be.isLit() && !be.blastFormed && SmeltHelper.canOutput(server, be.items.get(SLOT_INPUT), be.items.get(SLOT_OUTPUT))) {
            if (be.cookTotal == 0) {
                be.cookTotal = SmeltHelper.cookTime(server, be.items.get(SLOT_INPUT), MachineDefs.FIREBOX_BASE_COOK_TIME);
            }
            be.cookAccum += MachineDefs.fireboxSpeedPermille(be.gth.amountAsLong());
            while (be.cookAccum >= 1000) {
                be.cookAccum -= 1000;
                be.cookProgress++;
            }
            if (be.cookProgress >= be.cookTotal) {
                SmeltHelper.Result r = SmeltHelper.finish(server, be.items, SLOT_INPUT, SLOT_OUTPUT);
                be.storedXp += r.experience();
                be.cookProgress = 0;
                be.cookTotal = 0;
                be.cookAccum = 0;
                // Фаза 3: побочки плавки (цезий → взрыв, железо → шанс свинца).
                boolean exploded = SmeltSideEffects.apply(server, pos, r.produced(), be);
                if (exploded) {
                    return; // блок печи уничтожен взрывом — дальше работать нечем
                }
            }
            changed = true;
        } else if (be.cookProgress != 0 || be.cookTotal != 0 || be.cookAccum != 0) {
            be.cookProgress = 0;
            be.cookTotal = 0;
            be.cookAccum = 0;
            changed = true;
        }

        // 4. Раздать GTH соседям равномерно (макс. отдача за тик).
        if (be.pushGth(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
            be.updateComparatorOutput();
        }
    }

    /**
     * Стоит ли разжигать новую единицу топлива: есть смысл, если можно плавить
     * нагрузку ИЛИ есть куда девать GTH (буфер не полон, либо рядом приёмник).
     * На практике достаточно проверить «есть работа по плавке или буфер не полон».
     */
    private boolean wantsToBurn(ServerLevel server) {
        if (blastFormed) {
            // Доменная печь плавит только воздух: смысл гореть есть, пока буфер не полон.
            return gth.amountAsLong() < gthCapacityMilli();
        }
        boolean canSmelt = SmeltHelper.canOutput(server, items.get(SLOT_INPUT), items.get(SLOT_OUTPUT));
        return canSmelt || gth.amountAsLong() < gthCapacityMilli();
    }

    /** Равномерно раздать GTH соседям-приёмникам (котлам). */
    private boolean pushGth(Level level, BlockPos pos) {
        if (gth.isEmpty()) return false;
        if (level instanceof ServerLevel server && blastFormed) {
            // Доменная печь: GTH выходит через узлы теплотруб структуры (4 маршрута),
            // каждый узел — максимум BLAST_FURNACE_NODE_GTH_OUTPUT за тик (0.3.75:
            // «через каждый из 4х узлов она может отдавать 144/т» — и никак не
            // весь буфер в одну трубу).
            long remaining = gth.amountAsLong();
            long movedTotal = 0;
            for (BlockPos node : BlastFurnaceStructure.nodePositions(server, worldPosition)) {
                if (remaining <= 0) break;
                long budget = Math.min((long) MachineDefs.BLAST_FURNACE_NODE_GTH_OUTPUT, remaining);
                long moved = PipeRouting.drain(server, node, PipeType.HEAT, budget,
                    server.getGameTime(), (be, p) -> {
                        if (be instanceof FireboxBlockEntity) return null;
                        if (be instanceof GthSink sink) return sink::receiveGth;
                        return null;
                    });
                remaining -= moved;
                movedTotal += moved;
            }
            if (movedTotal > 0) {
                gth.extract(movedTotal, false);
                return true;
            }
            return false;
        }
        long budget = Math.min((long) MachineDefs.FIREBOX_GTH_OUTPUT, gth.amountAsLong());
        // Слив тепла: прямым соседям-котлам ИЛИ через теплотрубы дальше по цепи.
        long moved = PipeRouting.drain(level, pos, PipeType.HEAT, budget, level.getGameTime(), (be, p) -> {
            if (be instanceof FireboxBlockEntity) return null;
            if (be instanceof GthSink sink) return sink::receiveGth;
            return null;
        });
        if (moved > 0) {
            gth.extract(moved, false);
            return true;
        }
        return false;
    }

    // ─────────────────────────── ExperienceOutput ───────────────────────────

    @Override
    public void awardExperienceTo(Player player) {
        if (level instanceof ServerLevel server) {
            storedXp = SmeltHelper.awardExperience(server, player, storedXp);
            setChanged();
        }
    }

    // ─────────────────────── WorldlyContainer (автоматизация) ───────────────────────
    //
    // Ключевое правило для ВОРОНОК (не для ручной закладки): топливо всегда идёт
    // в слот топлива. Ручная укладка через меню использует FuelSlot/обычный слот
    // и этих ограничений не касается.

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (blastFormed) return SLOTS_BLAST; // доменная печь: топливо с любой стороны
        return switch (side) {
            case DOWN -> SLOTS_BOTTOM;
            case UP -> SLOTS_TOP;
            default -> SLOTS_SIDE;
        };
    }

    /**
     * Куда воронка может ВСТАВИТЬ предмет:
     * <ul>
     *   <li>слот топлива — только валидное топливо;</li>
     *   <li>слот сырья — только НЕ-топливо; если предмет одновременно и топливо,
     *       и сырьё (напр. бревно), приоритет у слота топлива, а в сырьё он
     *       попадёт лишь когда слот топлива уже забит под завязку (64) — это
     *       обеспечивается тем, что воронка сначала пробует вставить в слот
     *       топлива, а сюда придёт только если тот полон.</li>
     * </ul>
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (blastFormed) {
            // Трубы/воронки только ЗАКЛАДЫВАЮТ топливо; забирать нельзя.
            for (int fuel : FUEL_SLOTS) {
                if (slot == fuel) return isFuel(stack);
            }
            return false;
        }
        if (slot == SLOT_OUTPUT) return false;
        if (slot == SLOT_FUEL) {
            return isFuel(stack);
        }
        if (slot == SLOT_INPUT) {
            // Сырьё принимаем всегда, КРОМЕ случая, когда предмет — топливо и в
            // слоте топлива ещё есть место: тогда пусть воронка сначала набьёт
            // слот топлива (getSlotsForFace ставит FUEL раньше INPUT только для
            // боков; поэтому здесь явно перенаправляем топливо в слот топлива).
            if (isFuel(stack) && fuelSlotHasRoomFor(stack)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (blastFormed) return false; // доменная печь: только закладка
        // Результат забираем всегда. Из слота ТОПЛИВА можно вытащить только
        // «отработанную тару» — то, что уже не является топливом (пустое ведро от
        // лавы, ведро с водой). Настоящее топливо (уголь, брёвна) труба НЕ
        // высасывает — иначе труба снизу забирала бы и топливо, и результат.
        // Это повторяет поведение ванильной печи с воронкой снизу.
        if (slot == SLOT_OUTPUT) return true;
        if (slot == SLOT_FUEL) return !isFuel(stack);
        return false;
    }

    /** Валидно ли это топливо (по ванильным burn-таблицам сервера). */
    private boolean isFuel(ItemStack stack) {
        if (level instanceof ServerLevel server) {
            return stack.getBurnTime(RecipeType.SMELTING, server.fuelValues()) > 0;
        }
        return false;
    }

    /** Есть ли в слоте топлива место под ещё одну единицу такого топлива. */
    private boolean fuelSlotHasRoomFor(ItemStack stack) {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(fuel, stack)) return false;
        return fuel.getCount() < Math.min(fuel.getMaxStackSize(), getMaxStackSize());
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gth.save(tag, "Gth");
        // 0.3.75: сформированность хранится, чтобы восстановившаяся после
        // загрузки печь НЕ проходила цикл «поглощения/сброса» заново.
        tag.putBoolean("BlastFormed", blastFormed);
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("CookTotal", cookTotal);
        tag.putInt("CookAccum", cookAccum);
        tag.putFloat("StoredXp", storedXp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Сохранения до 0.3.65 несут 3 слота — дополняем до 8 (5 топливных).
        while (items.size() < 3 + MachineDefs.BLAST_FURNACE_FUEL_SLOTS) items.add(ItemStack.EMPTY);
        gth.load(tag, "Gth");
        blastFormed = tag.getBoolean("BlastFormed");
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        cookProgress = tag.getInt("CookProgress");
        cookTotal = tag.getInt("CookTotal");
        cookAccum = tag.getInt("CookAccum");
        storedXp = tag.getFloat("StoredXp");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable(blastFormed
            ? "block.gonzotech.blast_furnace"
            : "block.gonzotech.firebox");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        if (blastFormed) {
            return new com.gonzotech.machines.menu.BlastFurnaceMenu(id, inv, this, blastData);
        }
        return new FireboxMenu(id, inv, this, data);
    }

    /**
     * Доменные данные (0.3.65): GTH и горение двухпартно (units + milli),
     * ёмкость 34 016 не влезает в один int-слот клиентского отображения надёжно.
     */
    private final ContainerData blastData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) (gth.amountAsLong() % MachineDefs.MILLI);
                case 1 -> (int) (gth.amountAsLong() / MachineDefs.MILLI);
                case 2 -> litTime % MachineDefs.MILLI;
                case 3 -> litTime / MachineDefs.MILLI;
                case 4 -> litDuration % MachineDefs.MILLI;
                case 5 -> litDuration / MachineDefs.MILLI;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            // только чтение: сервер — источник истины
        }

        @Override
        public int getCount() {
            return 6;
        }
    };
}
