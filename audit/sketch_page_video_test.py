#!/usr/bin/env python3
"""Static regression contract for the Scholar Notes video and overlay ordering."""
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/gonzotech/chalkboard"
RES = ROOT / "src/main/resources/assets/gonzotech/textures/gui"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


screen = (SRC / "client/ScholarNotesScreen.java").read_text(encoding="utf-8")
content = (SRC / "notes/ScholarNotesContent.java").read_text(encoding="utf-8")
page = content.split("new ScholarPage(38, ScholarChapter.ERA_2", 1)[1].split("new ScholarPage(39,", 1)[0]
pin('"gui.gonzotech.notes.p44.title"' in page,
    "the trigger is attached to the Knowledge book's ‘Sketch’ page 5/11")
pin("SKETCH_PAGE_NUMBER = 38" in screen
    and "page.number() == SKETCH_PAGE_NUMBER && page.chapter() == ScholarChapter.ERA_2" in screen,
    "the timer matches the stable page identity, not a translated title or list offset")
pin("SKETCH_DELAY_TICKS = 5 * 20" in screen
    and "SKETCH_FADE_TICKS = 10 * 20" in screen,
    "playback waits five seconds and fades to black over ten seconds")
pin("int[] SKETCH_VIDEO_FRAME_TICKS = {10, 10, 10, 10};" in screen
    and "sketchVideoFrameAtTick(sketchPlaybackTicks)" in screen
    and "sketchVideoDurationTicks()" in screen,
    "the four video frames each remain visible for ten client ticks")
pin("sketchSequenceConsumed = true;" in screen
    and "!sketchSequenceConsumed && sketchPlaybackStage == SketchPlaybackStage.IDLE" in screen
    and "updateSketchPlaybackPage();" in screen,
    "a page visit consumes one attempt for this book instance and cannot spam-retrigger")
pin("public void tick()" in screen
    and "sketchPlaybackStage = SketchPlaybackStage.FADE;" in screen
    and "sketchPlaybackStage = SketchPlaybackStage.VIDEO;" in screen
    and "playSketchVideoSound();" in screen,
    "client tick stages delay, fade, and video; sound starts on the video transition")
pin("(sketchPlaybackTicks + partialTick) / (float) SKETCH_FADE_TICKS" in screen
    and "g.fill(0, 0, width, height, SKETCH_OVERLAY_Z, alpha << 24);" in screen
    and "else if (sketchPlaybackStage == SketchPlaybackStage.VIDEO)" in screen,
    "the fade is a black fullscreen overlay and disappears as video rendering begins")
pin('"textures/gui/videoplaybak.png"' in screen
    and "SKETCH_VIDEO_FRAME_WIDTH = 128" in screen
    and "SKETCH_VIDEO_FRAME_HEIGHT = 72" in screen
    and "SKETCH_VIDEO_FRAME_HEIGHT * SKETCH_VIDEO_FRAME_COUNT" in screen,
    "the vertical 16:9 test strip is sampled one frame at a time and stretched fullscreen")
pin("SoundEvents.ANVIL_LAND" in screen
    and "getSoundManager().stop(this.sketchSound)" in screen
    and "public void onClose()" in screen
    and "public void removed()" in screen
    and "cancelSketchPlayback();" in screen,
    "the test sound plays once and Escape/screen removal cancels playback and stops it")
pin("public boolean isPauseScreen()" in screen and "return false;" in screen,
    "the book and playback do not pause gameplay or take movement control")
render = screen.split("public void render(GuiGraphics g", 1)[1].split(
    "private void renderSketchPlaybackOverlay", 1
)[0]
pin(render.rfind("g.renderTooltip") < render.index("g.flush();")
    < render.index("renderSketchPlaybackOverlay(g, partialTick);"),
    "the GUI batch is flushed after tabs/tooltips and before the fullscreen fade/video")
pin("SKETCH_OVERLAY_Z = (int) (GuiGraphics.MAX_GUI_Z - 1.0F)" in screen
    and "g.fill(0, 0, width, height, SKETCH_OVERLAY_Z, alpha << 24);" in screen
    and "g.pose().translate(0.0D, 0.0D, (double) SKETCH_OVERLAY_Z);" in screen,
    "fade and video render at the top GUI depth, above item-tab z offsets")

png = (RES / "videoplaybak.png").read_bytes()
pin(png.startswith(b"\x89PNG\r\n\x1a\n") and struct.unpack(">II", png[16:24]) == (128, 288),
    "videoplaybak.png is the supplied 128x288 four-frame strip")
metadata = json.loads((RES / "videoplaybak.png.mcmeta").read_text(encoding="utf-8"))["animation"]
pin(metadata.get("width") == 128 and metadata.get("height") == 72
    and metadata.get("frametime") == 10 and metadata.get("frames") == [0, 1, 2, 3],
    "the texture metadata describes exactly four 10-tick frames")
pin("mod_version=0.3.161" in (ROOT / "gradle.properties").read_text(encoding="utf-8"),
    "micropatch version is 0.3.161")

print(f"sketch page video audit: {checks} pins passed")
