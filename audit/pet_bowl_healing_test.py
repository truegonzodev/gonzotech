#!/usr/bin/env python3
"""A successful pet-bowl bite heals cats and wolves by four health points."""
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
_ver.at_least("0.3.231", "pet bowl healing")

goal = (JAVA / "swag/PetBowlGoal.java").read_text(encoding="utf-8")
cat = (JAVA / "mixin/CatFatnessMixin.java").read_text(encoding="utf-8")
wolf = (JAVA / "mixin/WolfFatnessMixin.java").read_text(encoding="utf-8")

bite = goal.split("if (bowl.tryEatOne())", 1)[1].split("} else {", 1)[0]
assert "this.pet.heal(4.0F);" in bite
assert "FatPetLogic.onEat(this.fat, level.getGameTime());" in bite
assert "new PetBowlGoal((Cat) (Object) this" in cat
assert "new PetBowlGoal((Wolf) (Object) this" in wolf

print("Pet bowl bite healing checks passed")
