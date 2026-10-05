#!/usr/bin/env python3
"""Static contract: fermented alcoholic drinks reset the Telifon no-mash timer."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/gonzotech"
checks = 0


def read(relative):
    return (SRC / relative).read_text(encoding="utf-8")


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


drink = read("core/item/DrinkItem.java")
stress = read("core/psyche/PsycheStress.java")
registry = read("core/registry/ModItems.java")

consume = drink.index("AlcoholEffects.consume(serverPlayer, dose);")
reset = drink.index("PsycheStress.resetMashTimer(serverPlayer);")
pin(consume < reset,
    "the no-mash timer resets only after a drink is fully consumed")

for dose in ("BEER_MUG", "BEER_BUCKET", "VODKA"):
    pin(f"AlcoholDose.{dose}" in registry,
        f"{dose} remains wired to the shared DrinkItem path")

reset_method = stress.split("public static void resetMashTimer(ServerPlayer player)", 1)[1].split(
    "public static void onDiscoveryUsed", 1
)[0]
pin("psyche.setMashTick(player.serverLevel().getGameTime());" in reset_method
    and "save(player, psyche);" in reset_method
    and "relieve(" not in reset_method,
    "the shared timer reset persists the current tick without extra stress relief")

mash_handler = stress.split("public static void onMashDrunk(ServerPlayer player)", 1)[1].split(
    "public static void resetMashTimer", 1
)[0]
pin("relieve(player, MASH_RELIEF);" in mash_handler
    and "resetMashTimer(player);" in mash_handler,
    "mash itself retains its existing stress relief and timer reset")

pin("mod_version=0.3.164" in (ROOT / "gradle.properties").read_text(encoding="utf-8"),
    "micropatch version is 0.3.164")

print(f"drink mash timer audit: {checks} pins passed")
