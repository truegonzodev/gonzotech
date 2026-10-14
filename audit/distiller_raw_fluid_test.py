#!/usr/bin/env python3
"""Static regression pins for exclusive mash/wort buffering in the distiller."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/DistillerBlockEntity.java").read_text(encoding="utf-8")

assert "private enum RawInputType" in source
for token in ('NONE("none")', 'MASH("mash")', 'WORT("wort")', 'UNKNOWN("unknown")'):
    assert token in source, f"missing raw-fluid state {token}"

mash = re.search(r"public long receiveMash\(.*?\n    \}", source, re.S)
wort = re.search(r"public long receiveWort\(.*?\n    \}", source, re.S)
assert mash and wort
assert "if (!canReceiveRawInput(RawInputType.MASH)) return 0;" in mash.group(0)
assert "if (!canReceiveRawInput(RawInputType.WORT)) return 0;" in wort.group(0)
assert "if (rawInput.isEmpty()) rawInputType = RawInputType.MASH;" in mash.group(0)
assert "if (rawInput.isEmpty()) rawInputType = RawInputType.WORT;" in wort.group(0)
assert "return rawInput.isEmpty() || rawInputType == incomingType;" in source

assert "if (rawInput.isEmpty()) rawInputType = RawInputType.NONE;" in source
assert "long rawToProcess = Math.min(PROCESS_RAW_RATE, rawInput.amount());" in source
assert "rawInput = rawInput.withExtracted(rawToProcess);" in source
assert "distillate.receive(rawToProcess, false);" in source
assert "water.amount() >= PROCESS_WATER_RATE" in source and "currentGthMilli >= gthCostMilli" in source
assert 'tag.putString("RawInputType", rawInputType.id());' in source
assert 'RawInputType.fromStored(tag.getString("RawInputType"), !rawInput.isEmpty())' in source
assert 'default -> UNKNOWN;' in source, "legacy non-empty buffers must block both inputs until drained"
print("Distiller raw-fluid check passed: mash/wort cannot mix and input type persists")
