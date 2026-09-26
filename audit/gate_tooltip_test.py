#!/usr/bin/env python3
"""Headless production-core checks + static policy/resource wiring checks.

Needs JDK 21 (JAVA_HOME/PATH), or Java 21 + ECJ_JAR, like cleanroom_test.py.
Does not simulate NeoForge networking, config UI, or actual in-game tooltips.
"""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
checks = 0


def check(value, message):
    global checks
    checks += 1
    assert value, message


def source(path):
    return (SRC / path).read_text()


def uncomment(text):
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)


def ids(text):
    return re.findall(r'"(gonzotech:[a-z0-9_/]+)"', text)


book = uncomment(source("chalkboard/advancement/RecipeUnlocks.java"))
policy = {}


def add_policy(recipe_ids, tier, extra="NONE"):
    for recipe in recipe_ids:
        check(recipe not in policy or policy[recipe] == (tier, extra), "conflicting recipe-book policy: " + recipe)
        policy[recipe] = (tier, extra)


tier_map = re.search(r"RECIPES_BY_TIER = Map.of\((.*?)\n    \);", book, re.S).group(1)
for tier, entries in re.findall(r"(\d+), List.of\((.*?)\)", tier_map, re.S):
    add_policy(ids(entries), int(tier))
for tier, name in [(2, "TierTwoCrafting"), (3, "TierThreeCrafting")]:
    text = uncomment(source(f"machines/crafting/{name}.java"))
    add_policy(ids(re.search(r"RECIPE_IDS = List.of\((.*?)\);", text, re.S).group(1)), tier)
    check("List<String> recipeIds() { return RECIPE_IDS; }" in text, name + " shares the actual grant list")
add_policy(ids(re.search(r"RECIPES_AFTER_TWENTY_MINUTES = List.of\((.*?)\);", book, re.S).group(1)), 0, "PLAY_TIME_20_MINUTES")
solar = re.search(r'SOLAR_WATCH_RECIPE = "([^"]+)"', book).group(1)
add_policy([solar], 2, "SUN_EVENT")
check("grant(player, List.of(SOLAR_WATCH_RECIPE))" in book, "solar diagnostic and grant share recipe id")
check("RECIPES_BY_TIER.entrySet()" in book and "TierTwoCrafting.recipeIds().contains(id)" in book
      and "TierThreeCrafting.recipeIds().contains(id)" in book, "diagnostics query authoritative grant lists")

fixtures = []
for path in (ROOT / "src/main/resources/data/gonzotech/recipe").rglob("*.json"):
    recipe = json.loads(path.read_text())
    result = recipe.get("result")
    output = result.get("id") if isinstance(result, dict) else result
    if not isinstance(output, str):
        continue
    tier, extra = policy.get("gonzotech:" + path.stem, (0, "NONE"))
    fixtures.append(f"{output}\t{tier}\t{extra}\n")

layout = source("core/client/UniversalTooltip.java")
check(layout.index("addAll(advanced)") < layout.index("GateTooltips.append(event)"), "gates follow vanilla id/components")
client = source("core/client/GateTooltips.java")
check("player == null" in client and "player.isCreative()" in client and "event.getFlags().isAdvanced()" in client,
      "search-tree null guard and creative/F3+H gates")
check("EXTENDED_ADVANCED_TOOLTIPS.get()" in client and "GonzoClientConfig.SPEC.isLoaded()" in client
      and 'define("extendedAdvancedTooltips", true)' in source("core/config/GonzoClientConfig.java"),
      "client toggle read only after load, default on")
check(client.count("withStyle(ChatFormatting.DARK_GRAY)") == 2, "both lines dark gray")
check("Phase3Events.requiredTierFor(item)" in client and "Phase3Events.requiresSunEvent(item)" in client,
      "physical gate shares actual crafting policy")
check("LoggingOut" in client and "GateTooltipNetwork.clearClient()" in client, "logout clears server-specific metadata")
network = source("core/tooltip/GateTooltipNetwork.java")
check("OnDatapackSyncEvent" in network and "holder.value().display()" in network
      and "display.result().resolveForStacks(context)" in network, "join/reload index uses actual server recipe outputs")
check("current == null ? null" in network, "unsynchronized metadata does not masquerade as no gate")

home = os.environ.get("JAVA_HOME")
java = str(Path(home) / "bin/java") if home else shutil.which("java")
javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")
with tempfile.TemporaryDirectory(prefix="gonzotech-gate-tooltips-") as output:
    fixture = Path(output) / "recipes.tsv"
    fixture.write_text("".join(fixtures))
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    sources = [SRC / "core/tooltip/GateRequirement.java", SRC / "core/tooltip/RecipeGateIndex.java", ROOT / "audit/GateTooltipSelfTest.java"]
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "GateTooltipSelfTest", str(fixture)], check=True)
print(f"Gate tooltip policy/wiring checks passed: {checks}; recipe-output fixtures: {len(fixtures)}")
