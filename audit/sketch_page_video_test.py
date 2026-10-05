#!/usr/bin/env python3
"""Static contract for the Scholar Notes video/audio playback and overlay ordering."""
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/gonzotech"
ASSETS = ROOT / "src/main/resources/assets/gonzotech"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


def ogg_duration_ticks(path):
    """Read the Vorbis sample rate and final Ogg granule position without extra deps."""
    data = path.read_bytes()
    offset = 0
    packet = bytearray()
    sample_rate = None
    last_granule = None
    while offset < len(data):
        header = data[offset:offset + 27]
        if len(header) != 27 or header[:4] != b"OggS":
            raise AssertionError(f"invalid Ogg page at byte {offset}")
        segment_count = header[26]
        table_start = offset + 27
        lacing = data[table_start:table_start + segment_count]
        body_pos = table_start + segment_count
        for segment_size in lacing:
            packet.extend(data[body_pos:body_pos + segment_size])
            body_pos += segment_size
            if segment_size < 255:
                if packet.startswith(b"\x01vorbis") and len(packet) >= 16:
                    sample_rate = struct.unpack_from("<I", packet, 12)[0]
                packet.clear()
        granule = struct.unpack_from("<Q", header, 6)[0]
        if granule != 0xFFFFFFFFFFFFFFFF:
            last_granule = granule
        offset = body_pos
    if not sample_rate or last_granule is None:
        raise AssertionError("Ogg Vorbis identification header or final granule is missing")
    return last_granule / sample_rate * 20.0


screen = (SRC / "chalkboard/client/ScholarNotesScreen.java").read_text(encoding="utf-8")
content = (SRC / "chalkboard/notes/ScholarNotesContent.java").read_text(encoding="utf-8")
page = content.split("new ScholarPage(38, ScholarChapter.ERA_2", 1)[1].split(
    "new ScholarPage(39,", 1
)[0]
pin('"gui.gonzotech.notes.p44.title"' in page,
    "the trigger is attached to the Knowledge book's ‘Sketch’ page 5/11")
pin("SKETCH_PAGE_NUMBER = 38" in screen
    and "page.number() == SKETCH_PAGE_NUMBER && page.chapter() == ScholarChapter.ERA_2" in screen,
    "the timer matches the stable page identity, not a translated title or list offset")
pin("SKETCH_DELAY_TICKS = 5 * 20" in screen
    and "SKETCH_FADE_TICKS = 10 * 20" in screen,
    "playback retains its five-second delay and ten-second fade")

duration_method = screen.split("private static int sketchVideoDurationTicks()", 1)[1].split(
    "private static int sketchVideoFrameAtTick", 1
)[0]
frame_method = screen.split("private static int sketchVideoFrameAtTick", 1)[1].split(
    "@Override", 1
)[0]
pin("SKETCH_VIDEO_FRAME_COUNT = 91" in screen
    and "return SKETCH_VIDEO_FRAME_COUNT;" in duration_method
    and "return Math.min(Math.max(0, elapsedTicks), SKETCH_VIDEO_FRAME_COUNT - 1);" in frame_method,
    "the clip renders 91 distinct frames at exactly one frame per client tick")
pin("sketchSequenceConsumed = true;" in screen
    and "!sketchSequenceConsumed && sketchPlaybackStage == SketchPlaybackStage.IDLE" in screen
    and "updateSketchPlaybackPage();" in screen,
    "a page visit consumes one attempt for this book instance and cannot spam-retrigger")

fade_transition = screen.split("case FADE ->", 1)[1].split("case VIDEO ->", 1)[0]
pin("sketchPlaybackStage = SketchPlaybackStage.VIDEO;" in fade_transition
    and "sketchPlaybackTicks = 0;" in fade_transition
    and "playSketchVideoSound();" in fade_transition,
    "the custom sound starts on the same client tick that begins video frame zero")
pin('SKETCH_VIDEO = sound("videoplaybak")' in (SRC / "core/registry/ModSounds.java").read_text(encoding="utf-8")
    and "ModSounds.SKETCH_VIDEO.get()" in screen
    and "SoundEvents.ANVIL_LAND" not in screen,
    "playback uses the registered custom recording, not the temporary anvil sound")

video_tick = screen.split("case VIDEO ->", 1)[1].split("default ->", 1)[0]
pin("sketchPlaybackStage = SketchPlaybackStage.FINISHED;" in video_tick
    and "stopSketchSound()" not in video_tick,
    "finishing the 91-frame picture does not stop or truncate the audio")
cancel_method = screen.split("private void cancelSketchPlayback()", 1)[1].split(
    "private void playSketchVideoSound()", 1
)[0]
pin("            stopSketchSound();\n        }\n        // After the 91 video ticks" in cancel_method
    and "public void onClose()" in screen
    and "public void removed()" in screen,
    "ESC/screen removal cancels active playback, while completed audio is allowed to finish")
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

png_path = ASSETS / "textures/gui/videoplaybak.png"
png = png_path.read_bytes()
pin(png.startswith(b"\x89PNG\r\n\x1a\n")
    and struct.unpack(">II", png[16:24]) == (128, 91 * 72),
    "the supplied vertical PNG contains exactly 91 frames of 128x72 pixels")
metadata = json.loads((ASSETS / "textures/gui/videoplaybak.png.mcmeta").read_text(
    encoding="utf-8"))["animation"]
pin(metadata.get("width") == 128 and metadata.get("height") == 72
    and metadata.get("frametime") == 1 and metadata.get("frames") == list(range(91)),
    "the PNG metadata selects each of the 91 frames for one tick")

sound_json = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
sound_entries = sound_json.get("videoplaybak", {}).get("sounds", [])
pin(any(entry.get("name") == "gonzotech:videoplaybak" for entry in sound_entries)
    and (ASSETS / "sounds/videoplaybak.ogg").is_file(),
    "sounds.json points the registered playback event at the committed OGG asset")
video_ticks = ogg_duration_ticks(ASSETS / "sounds/videoplaybak.ogg")
pin(abs(video_ticks - 91.0) <= 1.0,
    f"the OGG keeps its complete ~91-tick duration ({video_ticks:.2f} ticks)")

pin("mod_version=0.3.162" in (ROOT / "gradle.properties").read_text(encoding="utf-8"),
    "micropatch version is 0.3.162")

print(f"sketch page video/audio audit: {checks} pins passed")
