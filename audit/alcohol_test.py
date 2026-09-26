#!/usr/bin/env python3
"""Compile/run the actual pure-Java alcohol-dose core, without Gradle/Minecraft.

Needs JDK 21 (JAVA_HOME or PATH). Alternatively a Java 21 runtime + ECJ_JAR.
Generated classes are temporary and never enter Git.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
JAVA_HOME = os.environ.get("JAVA_HOME")
java = str(Path(JAVA_HOME) / "bin/java") if JAVA_HOME else shutil.which("java")
javac = str(Path(JAVA_HOME) / "bin/javac") if JAVA_HOME else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")
src = ROOT / "src/main/java/com/gonzotech/core/psyche"
sources = [src / (name + ".java") for name in ("AlcoholDose",)]
sources.append(ROOT / "audit/AlcoholSelfTest.java")
with tempfile.TemporaryDirectory(prefix="gonzotech-alcohol-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "AlcoholSelfTest"], check=True)


# Static wiring/integration policy checks, not a Minecraft/mixin runtime test.
import json
src = ROOT / 'src/main/java/com/gonzotech'
def read(path): return (src / path).read_text()
drink = read('core/item/DrinkItem.java')
effects = read('core/psyche/AlcoholEffects.java')
faint = read('core/psyche/AlcoholFainting.java')
player = read('mixin/PlayerFaintingMixin.java')
pose = read('mixin/LivingFaintPoseMixin.java')
clue = read('chalkboard/ResonanceClue.java')
config = read('core/config/GonzoClientConfig.java')
foam = read('core/fluid/client/FluidFoamClient.java')
registry = read('core/registry/ModItems.java')
assert drink.index('instanceof ServerLevel') < drink.index('AlcoholEffects.consume') < drink.index('instabuild')
assert drink.count('AlcoholEffects.consume') == 1 and 'USE_TICKS = 32' in drink
assert 'ItemUtils.createFilledResult' in drink and 'stack.shrink(1)' in drink
for kind in ['BEER_MUG','BEER_BUCKET','VODKA']:
    assert 'AlcoholDose.' + kind in registry
assert 'POINT_MAX = 1_000_000' in read('core/psyche/PlayerPsyche.java')
assert all('psyche.set'+x in effects for x in ['Addiction','Stress','Crisis'])
assert 'dose.foodAfter' in effects and 'dose.saturationAfter' in effects and '.eat(' not in effects
assert 'roll(player, dose.clueChance)' in effects and 'ResonanceClue.giveNatural(player)' in effects
assert 'roll(player, dose.faintChance)' in effects and 'AlcoholFainting.start(player)' in effects
assert 'dose.slownessTicks, 0)' in effects and 'dose.poisonTicks, 0)' in effects and 'dose.nauseaTicks, 1)' in effects
assert 'player.startSleeping(anchor)' in faint and 'player.startSleepInBed(' not in faint
rest = read('mixin/ServerPlayerFaintingMixin.java')
assert 'Stats.TIME_SINCE_REST' in rest and '!AlcoholFainting.isFainting(self)' in rest and 'original.call(self, stat)' in rest
assert 'setRespawnPosition(' not in faint and 'awardStat(' not in faint and 'setBlock(' not in faint
assert 'CanContinueSleepingEvent' in faint and 'setContinueSleeping(true)' in faint
assert 'AlcoholDose.BLINDNESS_TICKS, 0)' in faint and 'AlcoholDose.FAINT_TICKS' in faint
assert 'stopSleepInBed(true, true)' in faint
assert all(x in faint for x in ['onLogout','onTravel','onDeath','onStop','onStopped'])
assert 'SynchedEntityData.defineId(Player.class' in player and 'sleepCounter = 0' in player
assert 'isSleepingLongEnough' in player and 'cir.setReturnValue(false)' in player
assert 'stopSleepInBed' in player and 'AlcoholFainting.afterWake(player)' in player
assert 'setPosToBed' in pose and 'faint.gonzotech$faintFloorOffset()' in pose
assert 'hurtServer' in pose and 'damageDoesNotCancelFaint' in pose and 'original.call(self)' in pose
assert 'self.getPose() == Pose.SLEEPING' in pose and 'faint.gonzotech$isFaintData(key)' in pose
assert 'AlcoholFainting.isFainting(player)' in read('core/psyche/PsycheStress.java')
assert '!AlcoholFainting.isFainting(player)' in read('core/psyche/PsycheStressEvents.java')
natural = clue[clue.index('public static Quantity giveNatural'):clue.index('private static Quantity give(ServerPlayer')]
assert natural.index('if (board == null) return null') < natural.index('give(player, board)')
assert 'if (hinted != null)' in natural and 'ChatFormatting.RED' in natural
assert 'AlcoholDose.withinClueRadius(distance)' in clue and 'if (!level.isLoaded(pos)) continue' in clue
assert 'ChalkboardNetwork.sendClue' in clue and 'spawnBoardParticles(level, board)' in clue
assert '.define("cosmeticFluidParticles", true)' in config
assert 'GonzoClientConfig.SPEC.isLoaded() && !GonzoClientConfig.COSMETIC_FLUID_PARTICLES.get()' in foam
assert foam.index('COSMETIC_FLUID_PARTICLES.get()') < foam.index('scan.advance(')
mixins = json.loads((ROOT / 'src/main/resources/gonzotech.mixins.json').read_text())['mixins']
assert 'ServerPlayerFaintingMixin' in mixins
assert 'PlayerFaintingMixin' in mixins and 'LivingFaintPoseMixin' in mixins
recipe = json.loads((ROOT / 'src/main/resources/data/gonzotech/recipe/pet_bowl.json').read_text())
assert recipe['pattern'] == ['n n','ibi','iii']
assert recipe['key'] == {'n':'minecraft:iron_nugget','i':'minecraft:iron_ingot','b':'minecraft:bone'}
assert recipe['result'] == {'id':'gonzotech:pet_bowl','count':1}
for lang, message in [('ru_ru','Элементарно...'),('en_us','Piece of cake...')]:
    data = json.loads((ROOT / f'src/main/resources/assets/gonzotech/lang/{lang}.json').read_text())
    assert data['message.gonzotech.clue.elementary'] == message
    assert 'gonzotech.configuration.cosmeticFluidParticles' in data
print('Alcohol / native faint / clue / foam toggle / pet bowl wiring checks passed (static)')
