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
        self.assertIn('PATTERN = { " AA", "TAT", "RA " }', source)
        pattern = (" AA", "TAT", "RA ")
        self.assertEqual(sum(row.count("A") for row in pattern), 4)
        self.assertEqual(sum(row.count("T") for row in pattern), 2)
        self.assertEqual(sum(row.count("R") for row in pattern), 1)
        self.assertIn("composition.equals(candidate)", source)
        self.assertIn("GreatswordItem.createAlloyStack(composition)", source)
        advancement = read_json(RES / "data/gonzotech/advancement/recipes/alloy_greatsword.json")
        self.assertEqual(advancement["rewards"]["recipes"], ["gonzotech:alloy_greatsword"])

    def test_item_tags_keep_other_sword_enchantments_enabled(self):
        expected = {f"gonzotech:{item}" for item in GREATWORDS}
        for tag in ("swords", "enchantable/sword", "enchantable/weapon", "enchantable/sharp_weapon", "enchantable/fire_aspect", "enchantable/durability"):
            with self.subTest(tag=tag):
                values = set(read_json(RES / f"data/minecraft/tags/item/{tag}.json")["values"])
                self.assertTrue(expected.issubset(values))
        source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("!isSharpness(enchantment)", source)
        self.assertIn("stored.keySet()", source)

    def test_static_stats_and_charge_formula_are_pinned(self):
        source = (ROOT / "src/main/java/com/gonzotech/core/registry/GreatswordContent.java").read_text(encoding="utf-8")
        for text in ("113, 11.0F, 0.35F", "280, 13.0F, 0.32F", "23, 14.0F, 0.29F", "432, 15.0F, 0.44F", "881, 17.0F, 0.38F"):
            self.assertIn(text, source)
        combat = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordCombat.java").read_text(encoding="utf-8")
        self.assertIn("charge <= 0.10F", combat)
        self.assertIn("baseDamage * (1.0D + 0.5D * charge)", combat)
        self.assertIn("look.scale(DASH_IMPULSE * charge)", combat)
        self.assertIn("DASH_IMPULSE = 0.20D", combat)
        self.assertIn("player.getDeltaMovement().add(impulse)", combat)
        self.assertIn("player.hurtMarked = true", combat)
        self.assertIn("ParticleTypes.DUST_PILLAR", combat)
        microstep = (ROOT / "src/main/java/com/gonzotech/core/psyche/PsycheCrisis.java").read_text(encoding="utf-8")
        self.assertIn("MICROSTEP_IMPULSE = 0.14", microstep)
        self.assertEqual(10.0 * (1.0 + 0.5 * 0.5), 12.5)
        self.assertEqual(10.0 * (1.0 + 0.5), 15.0)

    def test_greatsword_overrides_use_the_1_21_4_interaction_api(self):
        item_source = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordItem.java").read_text(encoding="utf-8")
        self.assertIn("public InteractionResult use(Level level, Player player, InteractionHand hand)", item_source)
        self.assertIn("return InteractionResult.CONSUME", item_source)
        self.assertIn("return InteractionResult.PASS", item_source)
        self.assertNotIn("InteractionResultHolder", item_source)
        self.assertIn("public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft)", item_source)

        combat = (ROOT / "src/main/java/com/gonzotech/core/item/GreatswordCombat.java").read_text(encoding="utf-8")
        self.assertIn("public static boolean release(", combat)
        self.assertIn("if (charge <= 0.10F) return false", combat)
        self.assertIn("return true;", combat)

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

    def test_greatsword_models_have_requested_gui_transform(self):
        for item in GREATWORDS:
            with self.subTest(item=item):
                model = read_json(ASSETS / f"models/item/{item}.json")
                self.assertEqual(model["parent"], "item/handheld")
                self.assertEqual(model["textures"]["layer0"], f"gonzotech:item/{item}")
                self.assertEqual(model["display"]["gui"], {
                    "rotation": [0, 0, 0],
                    "translation": [0, 0, 0],
                    "scale": [2, 2, 2],
                })

    def test_alloy_example_and_localizations_exist(self):
        docs = (ROOT / "docs/GREATSWORD-AUDIT-ROADMAP.md").read_text(encoding="utf-8")
        self.assertIn("3 порции кальция + 1 порция ванильного железа", docs)
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
