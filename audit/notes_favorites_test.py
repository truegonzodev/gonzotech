#!/usr/bin/env python3
"""0.3.308 source/resource contracts, NOT a Java or in-game execution claim.

The pure Java gate/order/scroll behavior also lives in notes_model_test.py.
These checks deliberately still run when that compile gate is SKIP(no JDK).
"""
from pathlib import Path
import json
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources/assets/gonzotech"

def text(path):
    return (JAVA / path).read_text()

creative = text("core/registry/ModCreativeTabs.java")
second = creative.index("ModMachines.SECOND_GRINDER_ITEM.get()")
tap = creative.index("ModMachines.THIRD_DISPENSING_TAP_ITEM.get()")
silicon = creative.index("ModItems.THIRD_SILICON_FACTORY.get()")
for machine in ("THIRD_GRINDER", "PLOTNICK", "WORK_BLOCK", "SEISMIC_SOURCE", "GEOPHONE", "MINER_DRILL", "BORE_PIPE"):
    needle = f"ModMachines.{machine}_ITEM.get()"
    assert creative.count(needle) == 1, machine
    assert second < tap < creative.index(needle) < silicon, machine

content = text("chalkboard/notes/ScholarNotesContent.java")
ids = [int(x) for x in re.findall(r"new ScholarPage\((\d+),", content)]
assert len(ids) == 51 and sorted(ids) == list(range(1, 52))
# Original IDs retain relative order: saved favorites and the sketch trigger do not shift.
assert [id for id in ids if id <= 44] == list(range(1, 45))
assert ids.index(34) < ids.index(45) < ids.index(46) < ids.index(47) < ids.index(48) < ids.index(49) < ids.index(50) < ids.index(36)
assert ids[-1] == 51
for id, gate in [(45, "PLAYTIME_1H"), (46, "PLAYTIME_1H"), (47, "PLAYTIME_1H"), (48, "PLAYTIME_1H"),
                 (49, "FLAG_COBBLESTONE"), (50, "PLAYTIME_2H"), (51, "NECROSIS_AND_DISCOVERY_3")]:
    assert f"new ScholarPage({id}, ScholarChapter.ERA_2, ScholarUnlock.{gate}," in content
# 0.3.309: one base recipe on the right; variants remain showcase-only.
from collections import Counter

def page_source(id):
    return content.split(f"new ScholarPage({id},", 1)[1].split("new ScholarPage(", 1)[0]

for id, recipe_name in [(47, "sculk_bricks"), (48, "noble_moss"), (49, "monolith"), (50, "pet_bowl")]:
    page = page_source(id)
    assert "Layout.TEXT_LEFT" in page and page.count("NoteIllustration.craftingRight(") == 1
    assert "craftingCycle" not in page
    match = re.search(r'NoteIllustration.craftingRight\(\s*List.of\((.*?)\),\s*"([^"]+)"', page, re.S)
    assert match, recipe_name
    grid = re.findall(r'"([^"]*)"', match.group(1))
    assert len(grid) == 9
    recipe = json.loads((ROOT / f"src/main/resources/data/gonzotech/recipe/{recipe_name}.json").read_text())
    count = recipe["result"].get("count", 1)
    expected_result = recipe["result"]["id"] + (f":{count}" if count != 1 else "")
    assert match.group(2) == expected_result, (recipe_name, "wrong output/count")
    if recipe["type"] == "minecraft:crafting_shaped":
        rows = recipe["pattern"] + [""] * (3 - len(recipe["pattern"]))
        expected_grid = [recipe["key"].get(char, "") for row in rows for char in row.ljust(3)]
        assert grid == expected_grid, (recipe_name, grid, expected_grid)
    else:
        assert Counter(item for item in grid if item) == Counter(recipe["ingredients"])

expected_showcases = {
    47: ["sculk_bricks", "chiseled_sculk_bricks", "smooth_sculk_bricks", "sculk_brick_stairs", "sculk_brick_slab", "sculk_brick_wall"],
    48: ["noble_moss", "smooth_noble_moss", "polished_noble_moss", "chiseled_noble_moss", "noble_moss_slab", "noble_moss_stairs", "noble_moss_pillar"],
    49: ["monolith"],
}
for id, expected in expected_showcases.items():
    showcase = page_source(id).split("List.of(", 1)[1].split(")", 1)[0]
    assert re.findall(r'"([^"]*)"', showcase) == ["gonzotech:" + item for item in expected]
bowl_showcase = page_source(50).split("List.of(", 1)[1].split(")", 1)[0]
assert re.findall(r'"([^"]*)"', bowl_showcase) == ["gonzotech:pet_bowl"] + [
    "minecraft:" + item for item in ("chicken", "cooked_chicken", "beef", "cooked_beef", "salmon", "cooked_salmon", "cod", "cooked_cod")]
for id in (45, 46):
    assert "Layout.TEXT_FULL, null" in page_source(id), "world-structure page changed"

necrosis = content.split("new ScholarPage(51,", 1)[1].split("    );", 1)[0]
assert "List.of(), Layout.TEXT_FULL, null" in necrosis
unlock = text("chalkboard/notes/ScholarUnlock.java")
assert "state.playtimeTicks() >= 60L * 60L * 20L" in unlock
assert "state.playtimeTicks() >= 2L * 60L * 60L * 20L" in unlock
assert re.search(r"case NECROSIS_AND_DISCOVERY_3 -> state.hasFlag\(ScholarNoteFlags.NECROSIS\)\s*&& state.tier3Unlocked\(\)", unlock)
events = text("core/event/NotesKnowledgeEvents.java")
assert "ItemEntityPickupEvent.Post" in events and "event.getOriginalStack().is(Items.COBBLESTONE)" in events
assert "MobEffectEvent.Added" in events and "player.hasEffect(ModEffects.NECROSIS)" in events
assert "player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);" in events
assert "NotesNetwork.sendToPlayer(player);" in events
assert "addEffect(" not in events and "removeEffect(" not in events

progress = text("chalkboard/progress/PlayerChalkboardProgress.java")
assert 'Codec.INT.listOf().optionalFieldOf("favoriteNotes", List.of())' in progress
assert "NotesFavorites favoriteNotes" in progress
assert "getFavoriteNotes()" in progress and "favoriteNotes.pages()" in progress
attachments = text("chalkboard/progress/ModAttachments.java")
assert ".serialize(PlayerChalkboardProgress.CODEC)" in attachments and ".copyOnDeath()" in attachments
model = text("chalkboard/notes/NotesFavorites.java")
assert "List<Integer> ids = new ArrayList<>()" in model
assert "List.copyOf(ids)" in model
assert "if (ids.contains(id)) return false;" in model
assert "ids.remove(Integer.valueOf(id))" in model
assert "ScholarNotesContent.indexOfNumber(id) < 0" in model
assert "FLAG_HEIGHT = 14" in model and "GAP = 4" in model
assert "Math.max(0, Math.min(scroll, maxScroll" in model
assert "if (scroll > 0)" in model and "if (scroll < maxScroll)" in model

network = text("chalkboard/network/NotesNetwork.java")
assert "record FavoriteNotePayload(int pageId, boolean favorite)" in network
assert 'PayloadThrottle.allow(player, "notesFavorite", 20)' in network
assert "if (index < 0) return;" in network
assert "payload.favorite() && !" in network and "ScholarNotesContent.isUnlocked(" in network
assert "progress.setFavoriteNote(payload.pageId(), payload.favorite())" in network
assert "count < 0 || count >" in network
assert "NotesDataPayload::tier3Unlocked" in network and "progress.isRecipeTierUnlocked(3)" in network
assert "NotesDataPayload::favorites" in network and "progress.getFavoriteNotes()" in network
assert "favoriteOnly" not in network and "favoriteOnly" not in progress
session = text("chalkboard/client/NotesClientSession.java")
assert "ClientPlayerNetworkEvent.LoggingOut" in session and "NotesNetwork.CLIENT_DATA = null" in session

screen = text("chalkboard/client/ScholarNotesScreen.java")
assert "FEATURE_SIZE = 9" in screen
assert "EnumSet<ScholarChapter> favoriteOnly = EnumSet.noneOf" in screen
assert "static final EnumSet" not in screen
assert "firstPageOfChapter" in screen and "favoriteOnly.contains(chapter)" in screen
assert "drawPageCounter(g, \"0 / 0\")" in screen
assert "button == 0 || button == 1" in screen
assert "gotoPage(r[4]);" in screen
assert "new NotesNetwork.FavoriteNotePayload(id, !isFavorite(id))" in screen
assert "favoriteOnly.remove(chapter)" in screen and "favoriteOnly.add(chapter)" in screen
assert "NotesFavorites.clampScroll(favoriteScroll - scrollY * NotesFavorites.STEP" in screen
scroll_handler = screen.split("public boolean mouseScrolled(", 1)[1].split("private void clampScroll", 1)[0]
assert scroll_handler.index("favoriteScroll") < scroll_handler.index("bodyContentHeight")
assert "g.enableScissor(x, topPos, x + TAB_W, topPos + FRAME_H)" in screen
assert "NotesFavorites.edgeAlpha(" in screen and "(alpha << 24) | rgb" in screen
assert "setShaderColor" not in screen
assert "page.chapter() == activeChapter() && isFavorite(page.number())" in screen
assert "Shader" not in model

for lang in ("ru_ru", "en_us"):
    data = json.loads((RES / f"lang/{lang}.json").read_text())
    for key in ("debris", "waste", "sculk", "moss", "monolith", "pet_bowl", "necrosis"):
        assert data[f"gui.gonzotech.notes.{key}.title"]
        assert len(data[f"gui.gonzotech.notes.{key}.body"]) > 100
    for key in ("add", "saved", "add_click", "remove_click", "only_click", "show_all_click", "empty"):
        assert data[f"gui.gonzotech.notes.favorites.{key}"]
    assert "/n" not in data["gui.gonzotech.notes.favorites.only_click"]

# Validate author PNG dimensions + alpha, accepting all standard PNG row filters.
def png_pixels(name, expected):
    raw = (RES / f"textures/gui/notes/{name}").read_bytes()
    assert raw[:8] == b"\x89PNG\r\n\x1a\n"
    w, h, depth, color = struct.unpack(">IIBB", raw[16:26])
    assert depth == 8 and color == 6
    if expected is None:
        assert w > 0 and h > 0 and w * 200 == h * 256
    else:
        assert (w, h) == expected
    offset = 8
    compressed = b""
    while offset < len(raw):
        size = struct.unpack(">I", raw[offset:offset+4])[0]
        kind = raw[offset+4:offset+8]
        if kind == b"IDAT": compressed += raw[offset+8:offset+8+size]
        offset += size + 12
    pixels = zlib.decompress(compressed)
    assert len(pixels) == h * (w * 4 + 1)
    stride = w * 4
    previous = bytearray(stride)
    decoded = bytearray()
    for y in range(h):
        offset = y * (stride + 1)
        filter_type = pixels[offset]
        assert 0 <= filter_type <= 4
        row = bytearray(pixels[offset + 1:offset + 1 + stride])
        for x in range(stride):
            left = row[x - 4] if x >= 4 else 0
            up = previous[x]
            upper_left = previous[x - 4] if x >= 4 else 0
            predictor = 0
            if filter_type == 1: predictor = left
            elif filter_type == 2: predictor = up
            elif filter_type == 3: predictor = (left + up) // 2
            elif filter_type == 4:
                estimate = left + up - upper_left
                distances = [abs(estimate - value) for value in (left, up, upper_left)]
                predictor = (left, up, upper_left)[distances.index(min(distances))]
            row[x] = (row[x] + predictor) & 255
        decoded.extend(row)
        previous = row
    alphas = decoded[3::4]
    assert 0 in alphas and 255 in alphas
    return decoded
assert png_pixels("feature_icon.png", (9, 9)) != png_pixels("featured_icon.png", (9, 9))
png_pixels("notes_favorite_tab.png", (28, 14))
# 0.3.310: every full-width body gets the new template, before header and body.
png_pixels("page_plain_text.png", None)
assert '"textures/gui/notes/page_plain_text.png"' in screen
page_draw = screen.split("private void drawPage(", 1)[1].split("private void drawPageCounter", 1)[0]
assert "page.layout() == ScholarPage.Layout.TEXT_FULL && page.hasBody()" in page_draw
background = page_draw.index("drawTemplate(g, TEX_PLAIN_TEXT);")
assert background < page_draw.index("g.flush();", background) < page_draw.index("drawFavoriteHeader(")
assert background < page_draw.index("this.font.split(bodyComponent(")
template_draw = screen.split("private void drawTemplate(", 1)[1].split("private void drawIllustration(", 1)[0]
assert "pngSize(tex)" in template_draw and "blitScaled(g, tex" in template_draw
assert "drawString" not in template_draw and "renderItem" not in template_draw
print("notes pages / gates / creative order / persistent favorites / UI / placeholder contracts passed")
