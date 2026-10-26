#!/usr/bin/env python3
"""Notes model compile-gate (0.3.78): the whole chalkboard/notes package is
dependency-free Java, so it is compiled here (ECJ) and smoke-run - syntax
slips like the PAGE_GONZO enum constant placed after the closing ';' are now
caught by suites instead of the author's local build. Runtime checks: the
booklet has 51 stable IDs with no duplicates, illustration factories
construct, structure models keep their block counts.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
NOTES = ROOT / "src/main/java/com/gonzotech/chalkboard/notes"
JAVA_HOME = os.environ.get("JAVA_HOME")
java = str(Path(JAVA_HOME) / "bin/java") if JAVA_HOME else shutil.which("java")
javac = str(Path(JAVA_HOME) / "bin/javac") if JAVA_HOME else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")

kind = (NOTES / "NoteIllustrationKind.java").read_text()
# 0.3.78 fix: enum constants are comma-separated; PAGE_GONZO closes the list.
assert 'CRAFTING_FERMENTATION("page_crafting_fermentation.png"),' in kind
assert 'PAGE_GONZO("page_gonzo.png");' in kind
# Устаревший javadoc ядерной топки (262) исправлен на подтверждённые 116.
nfb = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/NuclearFireboxBlockEntity.java").read_text()
assert "a constant 116 GTH/t" in nfb and "constant 262 GTH/t" not in nfb

HARNESS = '''package com.gonzotech.chalkboard.notes;

public class NotesModelSelfTest {
    public static void main(String[] args) {
        var pages = ScholarNotesContent.PAGES;
        if (pages.size() != 51) throw new AssertionError("pages: " + pages.size());
        var ids = new java.util.HashSet<Integer>();
        for (var page : pages) {
            if (!ids.add(page.number())) throw new AssertionError("duplicate ID " + page.number());
        }
        for (int id = 1; id <= 51; id++) {
            if (!ids.contains(id)) throw new AssertionError("missing ID " + id);
        }
        var favorites = new NotesFavorites(java.util.List.of(45, 1, 45, -1, 999));
        if (!favorites.pages().equals(java.util.List.of(45, 1))) throw new AssertionError("sanitize/order");
        if (favorites.set(45, true)) throw new AssertionError("duplicate add");
        favorites.set(45, false);
        favorites.set(45, true);
        if (!favorites.pages().equals(java.util.List.of(1, 45))) throw new AssertionError("readd order");
        if (NotesFavorites.maxScroll(10, 200) != 0 || NotesFavorites.maxScroll(11, 200) != 0
                || NotesFavorites.maxScroll(12, 200) != 12) throw new AssertionError("ribbon capacity");
        try {
            favorites.pages().add(2);
            throw new AssertionError("mutable saved list");
        } catch (UnsupportedOperationException expected) { }
        for (int count : new int[]{0, 1, 10, 11, 12, 51}) {
            double max = NotesFavorites.maxScroll(count, 200);
            if (NotesFavorites.clampScroll(-9, count, 200) != 0) throw new AssertionError("scroll top");
            if (NotesFavorites.clampScroll(9999, count, 200) != max) throw new AssertionError("scroll bottom");
            if (NotesFavorites.edgeAlpha(0, 200, 0, max) != 1) throw new AssertionError("top faded at start");
            if (NotesFavorites.edgeAlpha(199, 200, max, max) != 1) throw new AssertionError("bottom faded at end");
        }
        if (NotesFavorites.edgeAlpha(0, 200, 1, 16) >= 0.1f) throw new AssertionError("top fade");
        if (NotesFavorites.edgeAlpha(199, 200, 1, 16) >= 0.1f) throw new AssertionError("bottom fade");
        var noFlags = new NotesState(71999, true, true, false, java.util.Set.of());
        if (ScholarUnlock.PLAYTIME_1H.isMet(noFlags)) throw new AssertionError("early hour");
        if (!ScholarUnlock.PLAYTIME_1H.isMet(new NotesState(72000, false, false, false, java.util.Set.of()))) throw new AssertionError("hour");
        if (ScholarUnlock.PLAYTIME_2H.isMet(new NotesState(143999, false, false, false, java.util.Set.of()))) throw new AssertionError("early two hours");
        if (!ScholarUnlock.PLAYTIME_2H.isMet(new NotesState(144000, false, false, false, java.util.Set.of()))) throw new AssertionError("two hours");
        for (boolean tier3 : new boolean[]{false, true}) {
            for (boolean necrosis : new boolean[]{false, true}) {
                var state = new NotesState(999999, true, true, tier3,
                        necrosis ? java.util.Set.of(ScholarNoteFlags.NECROSIS) : java.util.Set.<String>of());
                if (ScholarUnlock.NECROSIS_AND_DISCOVERY_3.isMet(state) != (tier3 && necrosis)) throw new AssertionError("necrosis AND");
            }
        }
        var merelyHeldDiscovery = new NotesState(0, false, false, false,
                java.util.Set.of(ScholarNoteFlags.NECROSIS, ScholarNoteFlags.DISCOVERY_3));
        if (ScholarUnlock.NECROSIS_AND_DISCOVERY_3.isMet(merelyHeldDiscovery)) throw new AssertionError("unactivated scroll");
        long illustrated = pages.stream().filter(p -> p.illustration() != null).count();
        if (illustrated < 35) throw new AssertionError("illustrated: " + illustrated);
        if (StructureModel.blastFurnace().blocks().size() != 27) throw new AssertionError("blast model");
        if (StructureModel.turbineMinimum().blocks().size() != 27) throw new AssertionError("turbine model");
        System.out.println("NotesModelSelfTest passed ("
            + pages.size() + " pages, " + illustrated + " illustrated)");
    }
}
'''

with tempfile.TemporaryDirectory(prefix="gonzotech-notes-model-") as output:
    src = Path(output) / "harness" / "NotesModelSelfTest.java"
    src.parent.mkdir(parents=True)
    src.write_text(HARNESS)
    sources = sorted(NOTES.glob("*.java")) + [src]
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "com.gonzotech.chalkboard.notes.NotesModelSelfTest"], check=True)

print("Notes model compile-gate passed (package notes + 51-page booklet)")
