#!/usr/bin/env python3
"""Notes model compile-gate (0.3.78): the whole chalkboard/notes package is
dependency-free Java, so it is compiled here (ECJ) and smoke-run - syntax
slips like the PAGE_GONZO enum constant placed after the closing ';' are now
caught by suites instead of the author's local build. Runtime checks: the
booklet is 44 pages numbered 1..44 with no gaps, illustration factories
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
        if (pages.size() != 44) throw new AssertionError("pages: " + pages.size());
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).number() != i + 1) throw new AssertionError("page #" + i);
        }
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

print("Notes model compile-gate passed (package notes + 44-page booklet)")
