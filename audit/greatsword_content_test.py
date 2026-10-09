#!/usr/bin/env python3
"""Static content/regression checks for the monolith and greatsword feature."""

import json
import struct
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
GREATWORDS = (
    "stone_greatsword",
    "iron_greatsword",
    "golden_greatsword",
    "diamond_greatsword",
    "netherite_greatsword",
    "alloy_greatsword",
)


def read_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


class GreatswordContentTest(unittest.TestCase):
    def test_monolith_is_rotatable_and_uses_requested_end_and_side_textures(self):
        state = read_json(ASSETS / "blockstates/monolith.json")
        self.assertEqual(set(state["variants"]), {"axis=x", "axis=y", "axis=z"})
        model = read_json(ASSETS / "models/block/monolith.json")
        self.assertEqual(model["parent"], "minecraft:block/cube_column")
        self.assertEqual(model["textures"]["side"], "gonzotech:block/monolyth_side")
        self.assertEqual(model["textures"]["end"], "gonzotech:block/monolyth_top")
        recipe = read_json(RES / "data/gonzotech/recipe/monolith.json")
        self.assertEqual(recipe["pattern"], ["C C", "CCC", "C C"])
        self.assertEqual(sum(row.count("C") for row in recipe["pattern"]), 7)
        self.assertEqual(recipe["result"]["id"], "gonzotech:monolith")

    def test_vanilla_greatsword_recipes_use_exact_3_by_3_shapes(self):
        expected = {
            "stone_greatsword": [" CM", "MCM", "RC "],
            "iron_greatsword": [" IB", "BIB", "RI "],
            "golden_greatsword": [" IB", "BIB", "RI "],
            "diamond_greatsword": [" IB", "BIB", "RI "],
            "netherite_greatsword": [" IB", "BIB", "RI "],
        }
        for recipe_id, pattern in expected.items():
            with self.subTest(recipe=recipe_id):
                recipe = read_json(RES / f"data/gonzotech/recipe/{recipe_id}.json")
                self.assertEqual(recipe["pattern"], pattern)
                self.assertEqual(recipe["category"], "equipment")
                self.assertEqual(recipe["result"], {"id": f"gonzotech:{recipe_id}", "count": 1})
                advancement = read_json(RES / f"data/gonzotech/advancement/recipes/{recipe_id}.json")
                self.assertEqual(advancement["rewards"]["recipes"], [f"gonzotech:{recipe_id}"])

    def test_alloy_recipe_is_dynamic_and_composition_preserving(self):
        recipe = read_json(RES / "data/gonzotech/recipe/alloy_greatsword.json")
        self.assertEqual(recipe, {"type": "gonzotech:alloy_greatsword", "category": "equipment"})
        source = (ROOT / "src/main/java/com/gonzotech/core/recipe/AlloyGreatswordRecipe.java").read_text(encoding="utf-8")
        self.assertIn('PATTERN = { " IB", "BIB", "RI " }', source)
        pattern = (" IB", "BIB", "RI ")
        self.assertEqual(sum(row.count("I") for row in pattern), 3)
        self.assertEqual(sum(row.count("B") for row in pattern), 3)
        self.assertEqual(sum(row.count("R") for row in pattern), 1)
        self.assertIn("composition.equals(candidate)", source)
        self.assertIn("GreatswordItem.createAlloyStack(composition)", source)
        self.assertIn("GreatswordRecipeSerializers.ALLOY_GREATSWORD.get()", source)
        advancement = read_json(RES / "data/gonzotech/advancement/recipes/alloy_greatsword.json")
        self.assertEqual(advancement["rewards"]["recipes"], ["gonzotech:alloy_greatsword"])

    def test_alloy_greatsword_stats_use_requested_ranges_ignore_u_and_match_tooltip(self):
        source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("durability(properties, 3, 1_259)", source)
        self.assertIn("9.0D + 12.3D * properties.weight() / 100.0D", source)
        self.assertIn("-1.0D + 2.0D * properties.brittleness() / 100.0D", source)
        self.assertIn("0.9D - 0.8D * properties.weight() / 100.0D", source)
        self.assertNotIn("properties.toolTier()", source)
        self.assertIn("properties.inertness() >= INERTNESS_ENCHANTMENT_LOCK", source)
        self.assertIn("properties.heatResistance() >= LAVA_RESISTANCE_THRESHOLD", source)
        self.assertIn("new AlloyTint(properties.argbTint())", source)
        self.assertIn("AlloyEquipmentStats.adjustToolAttributes", source)

        stats = (ROOT / "src/main/java/com/gonzotech/core/item/AlloyEquipmentStats.java").read_text(encoding="utf-8")
        self.assertIn("public static int durability(AlloyProperties properties, int minimum, int maximum)", stats)
        self.assertIn("public static ItemAttributeModifiers adjustToolAttributes", stats)
        properties = (ROOT / "src/main/java/com/gonzotech/machines/processing/AlloyProperties.java").read_text(encoding="utf-8")
        self.assertIn("divideRound(brittleness, total) - averagePlasticity / 2", properties)

        self.assertEqual(round(3 + 1_256 * 0.0), 3)
        self.assertEqual(round(3 + 1_256 * 1.0), 1_259)
        self.assertEqual(round(1_259 * 0.25), 315)
        self.assertEqual(max(1, round(3 * 0.25)), 1)
        self.assertAlmostEqual(9 + 12.3 * 0 / 100 - 1 + 2 * 0 / 100, 8.0)
        self.assertAlmostEqual(9 + 12.3 * 100 / 100 - 1 + 2 * 100 / 100, 22.3)
        self.assertAlmostEqual(0.9 - 0.8 * 0 / 100, 0.9)
        self.assertAlmostEqual(0.9 - 0.8 * 100 / 100, 0.1)

    def test_item_tags_keep_other_sword_enchantments_enabled(self):
        expected = {f"gonzotech:{item}" for item in GREATWORDS}
        for tag in ("swords", "enchantable/sword", "enchantable/weapon", "enchantable/sharp_weapon", "enchantable/fire_aspect", "enchantable/durability"):
            with self.subTest(tag=tag):
                values = set(read_json(RES / f"data/minecraft/tags/item/{tag}.json")["values"])
                self.assertTrue(expected.issubset(values))
        source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("!isSharpness(enchantment)", source)
        self.assertIn("stored.keySet()", source)

    def test_density_feather_falling_and_quick_charge_have_greatsword_effects(self):
        item_source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("Enchantments.DENSITY", item_source)
        self.assertIn("Enchantments.FEATHER_FALLING", item_source)
        self.assertIn("Enchantments.QUICK_CHARGE", item_source)
        self.assertIn("isGreatswordSpecialEnchantment(enchantment)", item_source)
        self.assertIn("stack.has(DataComponents.ENCHANTABLE)", item_source)
        self.assertIn("QUICK_CHARGE_TIME_REDUCTION_PERCENT = { 0, 10, 21, 32 }", item_source)
        self.assertIn("FULL_CHARGE_TICKS * remainingPercent / 100.0F", item_source)
        self.assertIn("chargeDurationTicks(stack)", item_source)

        combat = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordCombat.java").read_text(encoding="utf-8")
        self.assertIn("chargeProgress(stack, elapsed)", combat)
        self.assertIn("ItemAttributeModifierEvent", combat)
        self.assertIn("event.addModifier(Attributes.ATTACK_SPEED", combat)
        self.assertIn("FEATHER_FALLING_ATTACK_SPEED_PER_LEVEL = 0.05D", combat)
        self.assertIn("return FEATHER_FALLING_ATTACK_SPEED_PER_LEVEL * level", combat)
        self.assertIn("featherFallingAttackSpeedBonus(level)", combat)
        self.assertIn("GreatswordItem.enchantmentLevel(stack, Enchantments.DENSITY)", combat)

        for level, expected_multiplier in ((0, 1.55), (1, 1.63), (5, 1.95)):
            actual = 1.0 + (0.55 + 0.08 * level)
            self.assertAlmostEqual(actual, expected_multiplier)
        quick_charge_ticks = [round(300 * (100 - reduction) / 100) for reduction in (0, 10, 21, 32)]
        self.assertEqual(quick_charge_ticks, [300, 270, 237, 204])
        for level, expected_bonus in enumerate((0.05, 0.10, 0.15, 0.20), start=1):
            self.assertAlmostEqual(0.05 * level, expected_bonus)

        docs = (ROOT / "docs/GREATSWORD-AUDIT-ROADMAP.md").read_text(encoding="utf-8")
        self.assertIn("1.55` без Плотности", docs)
        self.assertIn("1.63` с I", docs)
        self.assertIn("1.95` с V", docs)
        self.assertIn("0.05` к скорости атаки за уровень", docs)
        self.assertIn("270, 237 и 204 тика", docs)
        self.assertIn("Снимок реализации:** 0.3.181", docs)

    def test_static_stats_and_charge_formula_are_pinned(self):
        source = (ROOT / "src/main/java/com/gonzotech/core/registry/GreatswordContent.java").read_text(encoding="utf-8")
        for text in ("113, 11.0F, 0.35F", "23, 14.0F, 0.29F", "432, 15.0F, 0.44F", "881, 17.0F, 0.38F"):
            self.assertIn(text, source)
        self.assertIn("ALLOY_BASE_DAMAGE = 13.0F", source)
        self.assertIn("ALLOY_BASE_ATTACK_SPEED = 0.32F", source)
        self.assertIn("280,", source)
        combat = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordCombat.java").read_text(encoding="utf-8")
        self.assertIn("charge <= 0.10F", combat)
        self.assertIn("BASE_CHARGED_DAMAGE_BONUS = 0.55D", combat)
        self.assertIn("DENSITY_CHARGED_DAMAGE_BONUS_PER_LEVEL = 0.08D", combat)
        self.assertIn("chargedDamageMultiplier(charge, densityLevel)", combat)
        self.assertIn("return 1.0D + (BASE_CHARGED_DAMAGE_BONUS", combat)
        self.assertIn("LivingDamageEvent.Pre", combat)
        self.assertIn("event.setNewDamage(event.getNewDamage() * context.damageMultiplier())", combat)
        self.assertIn("player.attack(target)", combat)
        self.assertNotIn("temporaryBonus", combat)
        self.assertIn("getCurrentItemAttackStrengthDelay()", combat)
        self.assertIn("baseCooldownTicks * 1.5D", combat)
        self.assertIn("-1.0D / 3.0D", combat)
        self.assertIn("Operation.ADD_MULTIPLIED_TOTAL", combat)
        self.assertIn("player.resetAttackStrengthTicker()", combat)
        self.assertIn("double radius = (1.35D + 1.4D * charge) * 1.2D", combat)
        self.assertIn("int count = 50 + Math.round(70.0F * charge)", combat)
        self.assertIn("double radius = (0.45D + 1.1D * charge) * 2.0D", combat)
        self.assertIn("ParticleTypes.DUST_PILLAR", combat)
        self.assertIn("ParticleTypes.SWEEP_ATTACK", combat)
        self.assertIn("0, -2.0D, 0.0D, 0.0D, 0.0D", combat)
        self.assertIn("ModSounds.SWORD_IMPACT.get()", combat)
        self.assertIn("look.scale(DASH_IMPULSE * charge)", combat)
        self.assertIn("DASH_IMPULSE = 0.20D", combat)
        self.assertIn("player.getDeltaMovement().add(impulse)", combat)
        self.assertIn("player.hurtMarked = true", combat)
        microstep = (ROOT / "src/main/java/com/gonzotech/core/psyche/PsycheCrisis.java").read_text(encoding="utf-8")
        self.assertIn("MICROSTEP_IMPULSE = 0.14", microstep)
        self.assertEqual(10.0 * (1.0 + 0.55 * 0.5), 12.75)
        self.assertEqual(10.0 * 1.55, 15.5)
        self.assertLess(10.0 * (0.2 + 0.5 * 0.5 * 0.8) * 1.55, 10.0)

    def test_greatsword_overrides_use_the_1_21_4_interaction_api(self):
        item_source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("public InteractionResult use(Level level, Player player, InteractionHand hand)", item_source)
        self.assertIn("return InteractionResult.CONSUME", item_source)
        self.assertIn("return InteractionResult.PASS", item_source)
        self.assertNotIn("InteractionResultHolder", item_source)
        self.assertIn("public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft)", item_source)
        self.assertIn("player.resetAttackStrengthTicker()", item_source)
        self.assertIn("elapsed >= tenPercentTicks ? 1 : 0", item_source)
        self.assertIn("float pitch = stage == 1 ? 1.5F : 1.0F", item_source)
        self.assertIn("ModSounds.SWORD_READY.get()", item_source)

        combat = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordCombat.java").read_text(encoding="utf-8")
        self.assertIn("public static boolean release(", combat)
        self.assertIn("if (charge <= 0.10F) return false", combat)
        self.assertIn("return true;", combat)

    def test_impact_sequence_uses_requested_millisecond_phases_and_no_hit_stop(self):
        client = (ROOT / "src/main/java/com/gonzotech/core/client/GreatswordImpactClient.java").read_text(encoding="utf-8")
        renderer = (ROOT / "src/main/java/com/gonzotech/core/client/GreatswordImpactRenderer.java").read_text(encoding="utf-8")

        self.assertIn("WHITE_FLASH_START_NANOS = 41_000_000L", client)
        self.assertIn("WHITE_FLASH_DURATION_NANOS = 33_000_000L", client)
        self.assertIn("BLACK_FLASH_DURATION_NANOS = 18_000_000L", client)
        self.assertIn("TRACER_PHASE_DURATION_NANOS = 70_000_000L", client)
        self.assertIn("NEGATIVE_GRAIN_PHASE_DURATION_NANOS = 35_000_000L", client)
        self.assertIn("POST_IMPACT_START_NANOS = TRACER_PHASE_END_NANOS + NEGATIVE_GRAIN_PHASE_DURATION_NANOS", client)
        self.assertEqual(41 + 33 + 18 + 70 + 35, 197)
        self.assertIn("System.nanoTime()", client)
        self.assertNotIn("ClientTickEvent", client)
        self.assertNotIn("FROZEN", client)
        self.assertNotIn("minecraft.getTimer()", client)

        self.assertIn("ViewportEvent.ComputeCameraAngles", client)
        self.assertIn("event.getCamera().getEntity() != owner", client)
        self.assertIn("Filter.FULL_WHITE", client)
        self.assertIn("Filter.FULL_BLACK", client)
        self.assertIn("SHAKE_DURATION_NANOS = 300_000_000L", client)
        self.assertIn("event.setRoll(event.getRoll() + rollJolt * envelope)", client)
        self.assertIn("BRIGHTNESS_CONTRAST_FADE_NANOS = 730_000_000L", client)
        self.assertIn("FOV_FADE_NANOS = 1_000_000_000L", client)
        self.assertIn("1.0F - 0.40F * zoomStrength", client)
        self.assertIn("return remaining * remaining * remaining", client)
        client_config = (ROOT / "src/main/java/com/gonzotech/core/config/GonzoClientConfig.java").read_text(encoding="utf-8")
        self.assertIn("GonzoClientConfig.GREATSWORD_IMPACT_FRAMES.get()", client)
        self.assertIn("define(\"greatswordImpactFrames\", true)", client_config)
        for language in ("en_us", "ru_ru"):
            translations = read_json(ASSETS / f"lang/{language}.json")
            self.assertIn("gonzotech.configuration.greatswordImpactFrames", translations)
            self.assertIn("gonzotech.configuration.greatswordImpactFrames.tooltip", translations)

        self.assertIn("BINARY_THRESHOLD = 0.48", renderer)
        self.assertIn("POSITIVE_TRACE_CHANCE = 0.35", renderer)
        self.assertIn("NEGATIVE_TRACE_CHANCE = 0.55", renderer)
        self.assertIn("RAY_COUNT = 220", renderer)
        self.assertIn("MAX_TRACE_STEPS = 72", renderer)
        self.assertIn("negative ? 1.0 - originalBit : originalBit", renderer)
        self.assertIn("if (fineGrain < 0.12 || brokenBlock < 0.09)", renderer)
        self.assertIn("Filter.NEGATIVE_TRACERS", renderer)
        self.assertIn("Filter.GRAIN_OVERLAY", renderer)
        self.assertIn("drawTextureToTarget(screen.getColorTextureId(), workingFrame, screen,", renderer)
        self.assertIn("drawTextureToTarget(workingFrame.getColorTextureId(), screen, screen,", renderer)
        self.assertIn("NEGATIVE_GRAIN_HDR(-1)", renderer)
        self.assertLess(renderer.index("Filter.NEGATIVE_TRACERS"), renderer.index("Filter.GRAIN_OVERLAY"))
        trace_shader = renderer[
            renderer.index("} else if (u_filter == 3 || u_filter == 4)"):
            renderer.index("} else if (u_filter == 6)")
        ]
        self.assertNotIn("fineGrain", trace_shader)
        self.assertIn("mix(1.0, 0.70, u_intensity)", renderer)
        self.assertIn("1.0 + 0.30 * u_intensity", renderer)
        self.assertNotIn("captureFrozenFrame", renderer)
        self.assertNotIn("u_frozen", renderer)

        self.assertIn("workingFrame.resize(screen.width, screen.height);", renderer)
        self.assertNotIn("resize(screen.width, screen.height, Minecraft.ON_OSX)", renderer)

    def test_greatswords_are_main_hand_only_and_displace_offhand_items(self):
        item_source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity)", item_source)
        self.assertIn("slot != EquipmentSlot.OFFHAND", item_source)

        combat = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordCombat.java").read_text(encoding="utf-8")
        for contract in (
            "LivingSwapItemsEvent.Hands",
            "event.getItemSwappedToOffHand()",
            "event.setCanceled(true)",
            "PlayerTickEvent.Post",
            "inventory.getFreeSlot()",
            "inventory.setItem(freeSlot, displaced)",
            "player.drop(displaced, false, false)",
            "player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)",
        ):
            self.assertIn(contract, combat)

    def test_greatsword_models_double_length_and_width_but_keep_vanilla_depth(self):
        # Автор 08.10.2026: квад большого меча — ×2 по длине и ширине, а ГЛУБИНА
        # (локальная Z модели) остаётся ванильной, как у обычных предметов.
        # Эталон глубины — ваниль 1.21.4: item/handheld даёт firstperson 0.68 и
        # thirdperson 0.85, item/generated — ground 0.5; gui не задан нигде в
        # цепочке handheld → generated → builtin/generated, то есть identity 1.0.
        expected = {
            "firstperson_righthand": {
                "rotation": [0, -90, 25],
                "translation": [1.13, 3.2, 1.13],
                "scale": [1.36, 1.36, 0.68],
            },
            "firstperson_lefthand": {
                "rotation": [0, 90, -25],
                "translation": [1.13, 3.2, 1.13],
                "scale": [1.36, 1.36, 0.68],
            },
            "thirdperson_righthand": {
                "rotation": [0, -90, 55],
                "translation": [0, 4.0, 0.5],
                "scale": [1.7, 1.7, 0.85],
            },
            "thirdperson_lefthand": {
                "rotation": [0, 90, -55],
                "translation": [0, 4.0, 0.5],
                "scale": [1.7, 1.7, 0.85],
            },
            "ground": {
                "rotation": [0, 0, 0],
                "translation": [0, 2, 0],
                "scale": [1, 1, 0.5],
            },
            # Автор 08.10.2026 (ответ на вопрос): рамка и голова тоже ×2 по
            # длине/ширине. Rotation/translation — ванильные из item/generated.
            "head": {
                "rotation": [0, 180, 0],
                "translation": [0, 13, 7],
                "scale": [2, 2, 1],
            },
            "fixed": {
                "rotation": [0, 180, 0],
                "scale": [2, 2, 1],
            },
        }
        # Отрицательный пин: ни в одном контексте глубина не масштабируется.
        vanilla_depth = {
            "gui": 1.0,
            "firstperson_righthand": 0.68,
            "firstperson_lefthand": 0.68,
            "thirdperson_righthand": 0.85,
            "thirdperson_lefthand": 0.85,
            "ground": 0.5,
            "head": 1.0,
            "fixed": 1.0,
        }
        for item in GREATWORDS:
            with self.subTest(item=item):
                model = read_json(ASSETS / f"models/item/{item}.json")
                self.assertEqual(model["parent"], "item/handheld")
                self.assertEqual(model["textures"]["layer0"], f"gonzotech:item/{item}")
                self.assertEqual(model["display"]["gui"], {
                    "rotation": [0, 0, 0],
                    "translation": [0, 0, 0],
                    "scale": [2, 2, 1],
                })
                for context, transform in expected.items():
                    self.assertEqual(model["display"][context], transform)
                for context, depth in vanilla_depth.items():
                    self.assertAlmostEqual(
                        model["display"][context]["scale"][2],
                        depth,
                        msg=f"{item}: глубина {context} обязана остаться ванильной",
                    )

    def test_greatsword_sound_events_are_registered_and_have_audio(self):
        sounds = read_json(ASSETS / "sounds.json")
        registry = (ROOT / "src/main/java/com/gonzotech/core/registry/ModSounds.java").read_text(encoding="utf-8")
        for sound in ("sword_impact", "sword_ready"):
            with self.subTest(sound=sound):
                self.assertEqual(sounds[sound]["sounds"], [{"name": f"gonzotech:{sound}"}])
                self.assertTrue((ASSETS / f"sounds/{sound}.ogg").read_bytes().startswith(b"OggS"))
                self.assertIn(f'{sound.upper()} = sound("{sound}")', registry)
                subtitle = sounds[sound]["subtitle"]
                self.assertIn(subtitle, read_json(ASSETS / "lang/en_us.json"))
                self.assertIn(subtitle, read_json(ASSETS / "lang/ru_ru.json"))

        item_source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("elapsed >= tenPercentTicks ? 1 : 0", item_source)
        self.assertIn("float pitch = stage == 1 ? 1.5F : 1.0F", item_source)
        self.assertIn("ModSounds.SWORD_READY.get()", item_source)

    def test_alloy_example_and_localizations_exist(self):
        docs = (ROOT / "docs/GREATSWORD-AUDIT-ROADMAP.md").read_text(encoding="utf-8")
        self.assertIn("3 порции кальция + 1 порция ванильного железа", docs)
        self.assertIn("3 одинаковых по составу `custom_alloy`", docs)
        self.assertIn("9 + 12.3 × M / 100", docs)
        self.assertIn("0.9 − 0.8 × M / 100", docs)
        self.assertIn("U` не меняет", docs)
        self.assertIn("Магнитный привод для больших мечей", docs)
        self.assertIn("не реализована", docs)
        for locale in ("en_us", "ru_ru"):
            messages = read_json(ASSETS / f"lang/{locale}.json")
            for item in ("monolith", *GREATWORDS):
                key = f"item.gonzotech.{item}" if item != "monolith" else "block.gonzotech.monolith"
                self.assertTrue(messages.get(key), f"missing {locale}:{key}")
            self.assertIn("hud.gonzotech.greatsword_charge", messages)

    def test_png_assets_are_valid_rgba_images(self):
        paths = [
            ASSETS / "textures/block/monolyth_side.png",
            ASSETS / "textures/block/monolyth_top.png",
            *(ASSETS / f"textures/item/{item}.png" for item in GREATWORDS),
        ]
        for path in paths:
            with self.subTest(path=path.name):
                data = path.read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
                self.assertGreater(width, 0)
                self.assertGreater(height, 0)
                self.assertEqual(bit_depth, 8)
                self.assertEqual(color_type, 6)


if __name__ == "__main__":
    unittest.main(verbosity=2)
