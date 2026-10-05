#!/usr/bin/env python3
"""Static regression contract for the Energy advancement tree and event triggers."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
DATA = ROOT / "src/main/resources/data/gonzotech/advancement"
LANG = ROOT / "src/main/resources/assets/gonzotech/lang"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


def load_advancement(name):
    return json.loads((DATA / f"{name}.json").read_text(encoding="utf-8"))


advancements = {
    name: load_advancement(name)
    for name in (
        "root",
        "discovery_1",
        "discovery_2",
        "discovery_3",
        "discovery_4",
        "discovery_5",
        "discovery_6",
        "discovery_7",
        "discovery_8",
        "discovery_9",
        "discovery_10",
        "discovery_11",
        "discovery_12",
        "discovery_13",
        "discovery_14",
        "discovery_15",
        "discovery_16",
        "first_mash",
        "crimson_day",
        "cesium_interaction",
        "leaky_bucket",
        "forbidden_bath",
    )
}
ru = json.loads((LANG / "ru_ru.json").read_text(encoding="utf-8"))
en = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))

root = advancements["root"]
pin("parent" not in root, "gonzotech:root is the sole root of the advancement tree")
pin(root["display"]["icon"]["id"] == "gonzotech:chalkboard",
    "the root uses the chalkboard icon")
pin(root["display"]["title"]["translate"] == "advancements.gonzotech.root.title"
    and ru["advancements.gonzotech.root.title"] == "Недобро пожаловать!",
    "the root achievement title matches the requested Russian text")
pin(root["display"]["description"]["translate"] == "advancements.gonzotech.root.description"
    and ru["advancements.gonzotech.root.description"] == "Начните всё с чистого листа.",
    "the root achievement description matches the requested Russian text")
pin(root["display"]["show_toast"] is False
    and root["display"]["announce_to_chat"] is False,
    "the root has neither an advancement toast nor a chat announcement")
pin(root["criteria"]["joined_world"]["trigger"] == "minecraft:impossible",
    "the root is granted manually rather than by a noisy vanilla trigger")

for index in range(1, 17):
    advancement = advancements[f"discovery_{index}"]
    expected_parent = "gonzotech:root" if index == 1 else f"gonzotech:discovery_{index - 1}"
    pin(advancement.get("parent") == expected_parent,
        f"discovery_{index} remains in the sequential chain under {expected_parent}")
    title_key = f"advancements.gonzotech.discovery_{index}.title"
    description_key = f"advancements.gonzotech.discovery_{index}.description"
    pin(advancement["display"]["title"]["translate"] == title_key
        and advancement["display"]["description"]["translate"] == description_key
        and ru[title_key].startswith(f"Открытие {index}:")
        and en[title_key].startswith(f"Discovery {index}:"),
        f"discovery_{index} title and description keys remain the original chain entries")

side_branches = {
    "first_mash": ("gonzotech:root", "gonzotech:the_fruit_mash"),
    "crimson_day": ("gonzotech:root", "gonzotech:solar_watch"),
    "cesium_interaction": ("gonzotech:root", "gonzotech:cesium_ingot"),
    "leaky_bucket": ("gonzotech:cesium_interaction", "gonzotech:leaky_bucket"),
    "forbidden_bath": ("gonzotech:cesium_interaction", "gonzotech:obsidian_bucket"),
}
for name, (parent, icon) in side_branches.items():
    advancement = advancements[name]
    pin(advancement.get("parent") == parent,
        f"{name} is attached below the intended parent")
    pin(advancement["display"]["icon"]["id"] == icon,
        f"{name} uses its existing item icon")
    for locale, translations in (("ru_ru", ru), ("en_us", en)):
        display = advancement["display"]
        pin(display["title"]["translate"] in translations
            and display["description"]["translate"] in translations,
            f"{name} title and description are localized in {locale}")

pin(ru["advancements.gonzotech.energy_tab"] == "Энергетика",
    "the advancement tab has the requested Russian name")
mixins = json.loads((ROOT / "src/main/resources/gonzotech.mixins.json").read_text())
pin("client.AdvancementTabMixin" in mixins["client"],
    "the tab-renaming mixin is registered as client-only")
tab_mixin = (JAVA / "mixin/client/AdvancementTabMixin.java").read_text(encoding="utf-8")
pin('method = "getTitle"' in tab_mixin
    and '"root".equals(this.getRootNode().holder().id().getPath())' in tab_mixin
    and "advancements.gonzotech.energy_tab" in tab_mixin,
    "only the gonzotech:root tab title is renamed")

mod_advancements = (JAVA / "chalkboard/advancement/ModAdvancements.java").read_text(encoding="utf-8")
pin('awardAchievement(player, "root")' in mod_advancements
    and 'public static void awardFirstMash' in mod_advancements
    and 'public static void awardCrimsonDay' in mod_advancements
    and 'public static void awardCesiumInteraction' in mod_advancements
    and 'public static void awardLeakyBucket' in mod_advancements
    and 'public static void awardForbiddenBath' in mod_advancements,
    "root and all side-branch achievements have idempotent server grant helpers")

phase3 = (JAVA / "core/event/Phase3Events.java").read_text(encoding="utf-8")
sun = (JAVA / "sunevent/SunEventServer.java").read_text(encoding="utf-8")
corrosive_bucket = (JAVA / "core/item/CorrosiveBucketItem.java").read_text(encoding="utf-8")
corrosive_fluid_bucket = (JAVA / "core/item/CorrosiveFluidBucketItem.java").read_text(encoding="utf-8")
events = (JAVA / "core/event/AchievementEvents.java").read_text(encoding="utf-8")
mod_entry = (JAVA / "GonzoTechMod.java").read_text(encoding="utf-8")

pin("ModAdvancements::onPlayerLoggedIn" in mod_entry,
    "the silent root is granted through the existing player-login event")
pin("ModAdvancements.awardFirstMash(player)" in phase3
    and "ModItems.THE_PROTO_MASH" in phase3
    and "ModItems.THE_FRUIT_MASH" in phase3,
    "eating either prototype or fruit mash awards the mash achievement")
pin("ModAdvancements.awardCrimsonDay(p)" in sun
    and "ModAdvancements.awardCrimsonDay(player)" in sun
    and "data.lastEventDay > 0" in sun,
    "the crimson-day achievement is awarded at event start and to players joining during it")
pin("PlayerInteractEvent.RightClickItem" in events
    and "PlayerInteractEvent.RightClickBlock" in events
    and "PlayerInteractEvent.LeftClickBlock" in events
    and 'contains("cesium")' in events
    and "AchievementEvents.checkCesiumInventory(serverPlayer)" in phase3,
    "cesium interactions cover item use, block interaction, and inventory-acquired forms")
pin("ModAdvancements.awardLeakyBucket(serverPlayer)" in corrosive_bucket
    and "ModAdvancements.awardLeakyBucket(serverPlayer)" in corrosive_fluid_bucket
    and "ModAdvancements.awardLeakyBucket(owner)" in phase3,
    "leaky-bucket rewards cover both player timers, use-on conversion, and water conversion")
pin("ModAdvancements.awardForbiddenBath(serverPlayer)" in phase3
    and "ModAdvancements.awardForbiddenBath(owner)" in phase3
    and "player.isInWater()" in phase3,
    "the obsidian-bucket achievement is tied to the submerged-player conversion")

leaky_criteria = advancements["leaky_bucket"]["criteria"]
pin(any(value.get("trigger") == "minecraft:inventory_changed"
        and value.get("conditions", {}).get("items", [{}])[0].get("items") == "gonzotech:leaky_bucket"
        for value in leaky_criteria.values()),
    "leaky-bucket achievement also recognizes output from automated conversions")
pin(set(advancements["forbidden_bath"]["criteria"]) == {"converted_while_submerged"}
    and advancements["forbidden_bath"]["criteria"]["converted_while_submerged"]["trigger"] == "minecraft:impossible",
    "forbidden-bath is only awarded for the actual submerged-player conversion event")

updated_notes = (ROOT / "docs/UPDATED_TEMP_NOTES.md").read_text(encoding="utf-8")
deferred = (ROOT / "docs/DEFERRED-2026-09-22.md").read_text(encoding="utf-8")
for old_open in ("Выкуси", "Выкуси нахрен", "Слон в посудной лавке", "Счастье",
                 "Босс KFC", "Ферментация"):
    pin(old_open in deferred,
        f"the previously deferred achievement remains recorded: {old_open}")
pin("Недобро пожаловать!" in updated_notes
    and "Взрывной характер" in updated_notes
    and "Промышленное недержание" in updated_notes
    and "Купание запрещено" in updated_notes
    and "Оставлять все шесть отдельными открытыми пунктами" in deferred,
    "the planned-achievement notes list the new tree while retaining the six old open items")

version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
pin("mod_version=0.3.164" in version, "micropatch version is 0.3.164")

print(f"achievement tree audit: {checks} pins passed")
