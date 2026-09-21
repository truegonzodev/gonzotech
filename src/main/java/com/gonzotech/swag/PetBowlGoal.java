package com.gonzotech.swag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * ИИ «подойти к миске и пожрать» (кот приоритет 7, волк приоритет 9 — см.
 * CatFatnessMixin/WolfFatnessMixin; ниже FollowOwner/лежания, выше прогулок).
 *
 * <p>Гейты входа (автор): торс &lt; ×2.0, с последнего приёма &gt;140 с,
 * питомец не сидит/не в транспорте, миска в 12 блоках. Успешный заход:
 * обязательный приём + цепочка шансов (см. {@link FatPetLogic#rollMaxBites}),
 * пауза 12 тиков между кусками. Пустая миска: стоим 2–3 с, потом частица
 * {@code damage_indicator} и кулдаун пустого захода 30 с.
 */
public class PetBowlGoal extends Goal {

    /** Пауза между кусками, тиков. */
    private static final int BITE_PAUSE_TICKS = 12;
    /** Повторная прокладка пути, тиков. */
    private static final int REPATH_TICKS = 25;
    /** Кулдаун повторного поиска миски при отказе, тиков. */
    private static final int SEARCH_COOLDOWN_TICKS = 20;
    /** Максимальное расстояние до миски для начала. */
    private static final int BOWL_RANGE = 12;
    /** «Дошёл»: горизонталь 1.2 б., вертикаль 1.5 б. */
    private static final double ARRIVE_XZ = 1.2D;

    private final TamableAnimal pet;
    private final PetFatness fat;
    private final double speed;

    @Nullable private BlockPos bowlPos;
    private int searchCooldown;
    private int repathTimer;
    private int biteTimer;
    private int bitesDone;
    private int maxBites;
    private int emptyWaitTicks;

    public PetBowlGoal(TamableAnimal pet, double speed) {
        this.pet = pet;
        this.fat = (PetFatness) pet;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.pet.isOrderedToSit() || this.pet.isPassenger()
            || !(this.pet.level() instanceof ServerLevel)) {
            return false;
        }
        if (this.searchCooldown > 0) {
            this.searchCooldown--;
            return false;
        }
        this.searchCooldown = SEARCH_COOLDOWN_TICKS;
        long now = this.pet.level().getGameTime();
        if (this.fat.gonzotech$fatness() >= FatPetLogic.WALK_FATNESS) {
            return false;
        }
        if (now - this.fat.gonzotech$lastEatAt() < FatPetLogic.EAT_COOLDOWN_TICKS) {
            return false;
        }
        if (now - this.fat.gonzotech$lastEmptyVisitAt() < FatPetLogic.EMPTY_COOLDOWN_TICKS) {
            return false;
        }
        BlockPos found = PetBowlBlockEntity.findNearestBowl(this.pet, BOWL_RANGE);
        if (found == null) {
            return false;
        }
        this.bowlPos = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.bowlPos != null
            && !this.pet.isOrderedToSit()
            && PetBowlBlockEntity.getAt(this.pet.level(), this.bowlPos) != null;
    }

    @Override
    public void start() {
        this.bitesDone = 0;
        this.maxBites = FatPetLogic.rollMaxBites(this.pet.getRandom());
        this.biteTimer = 0;
        this.emptyWaitTicks = 0;
        this.repathTimer = 0;
    }

    @Override
    public void stop() {
        this.pet.getNavigation().stop();
        this.bowlPos = null;
        this.bitesDone = 0;
        this.emptyWaitTicks = 0;
    }

    @Override
    public void tick() {
        if (this.bowlPos == null) {
            return;
        }
        this.pet.getLookControl().setLookAt(
            this.bowlPos.getX() + 0.5D, this.bowlPos.getY() + 0.4D, this.bowlPos.getZ() + 0.5D);

        // Простаиваем у пустой миски 2–3 секунды (автор) и уходим с искрой разочарования.
        if (this.emptyWaitTicks > 0) {
            if (--this.emptyWaitTicks == 0) {
                ServerLevel level = (ServerLevel) this.pet.level();
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    this.bowlPos.getX() + 0.5D, this.bowlPos.getY() + 0.6D, this.bowlPos.getZ() + 0.5D,
                    5, 0.1D, 0.15D, 0.1D, 0.05D);
                this.fat.gonzotech$setLastEmptyVisitAt(level.getGameTime());
                this.bowlPos = null;
            }
            return;
        }

        double dx = this.bowlPos.getX() + 0.5D - this.pet.getX();
        double dz = this.bowlPos.getZ() + 0.5D - this.pet.getZ();
        double dyAbs = Math.abs(this.bowlPos.getY() + 0.5D - this.pet.getY());
        boolean arrived = dx * dx + dz * dz <= ARRIVE_XZ * ARRIVE_XZ && dyAbs <= 1.5D;

        if (!arrived) {
            if (--this.repathTimer <= 0) {
                this.repathTimer = REPATH_TICKS;
                this.pet.getNavigation().moveTo(
                    this.bowlPos.getX() + 0.5D, this.bowlPos.getY() + 0.5D, this.bowlPos.getZ() + 0.5D, this.speed);
            }
            return;
        }

        PetBowlBlockEntity bowl = PetBowlBlockEntity.getAt(this.pet.level(), this.bowlPos);
        if (bowl == null) {
            this.bowlPos = null;
            return;
        }
        if (!bowl.hasFood()) {
            this.pet.getNavigation().stop();
            this.emptyWaitTicks = 40 + this.pet.getRandom().nextInt(21); // 2–3 секунды
            return;
        }
        if (this.biteTimer > 0) {
            this.biteTimer--;
            return;
        }
        if (this.bitesDone >= this.maxBites) {
            this.bowlPos = null; // наелся за этот заход, кулдаун уже от onEat
            return;
        }
        ItemStack mouthful = bowl.peekFood().copyWithCount(1);
        if (bowl.tryEatOne()) {
            this.bitesDone++;
            this.biteTimer = BITE_PAUSE_TICKS;
            ServerLevel level = (ServerLevel) this.pet.level();
            FatPetLogic.onEat(this.fat, level.getGameTime());
            level.playSound(null, this.bowlPos, SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL,
                0.7F, 1.0F + level.getRandom().nextFloat() * 0.2F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, mouthful),
                this.bowlPos.getX() + 0.5D, this.bowlPos.getY() + 0.45D, this.bowlPos.getZ() + 0.5D,
                8, 0.15D, 0.15D, 0.15D, 0.02D);
        } else {
            this.emptyWaitTicks = 40 + this.pet.getRandom().nextInt(21);
        }
    }
}
