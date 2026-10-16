#!/usr/bin/env python3
"""Static contracts for the client-authoritative music debug roll."""
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
_ver.at_least("0.3.230", "server music debug roll")

command = (JAVA / "radiation/RadiationDebugCommand.java").read_text(encoding="utf-8")
network = (JAVA / "music/MusicDebugNetwork.java").read_text(encoding="utf-8")
client = (JAVA / "music/client/GonzoMusicClient.java").read_text(encoding="utf-8")
mod = (JAVA / "GonzoTechMod.java").read_text(encoding="utf-8")
mixin = (JAVA / "mixin/client/MusicManagerStartPlayingMixin.java").read_text(encoding="utf-8")

assert 'Commands.literal("trigger")' in command and 'Commands.literal("music")' in command
assert "MusicDebugNetwork.requestDebugRoll(player)" in command
assert '"debug_music_trigger"' in network and "registrar.playToClient" in network
assert "MusicDebugNetwork.register(registrar)" in mod
assert "player.getRandom().nextInt(100) < roll.chancePercent()" in client
assert "Попытка создать музыку — " in client
assert "Пул музыки для этого броска был: " in client
assert 'return "музыка уже играет"' in client and '"кд" : null' in client
assert "isMusicCooldownActive()" in client and "isMusicPlayingFromManager(minecraft)" in client
assert "playOst(minecraft, selected)" in client
assert "GT_OST_BITTER" in client and "GT_OST_CRADLE" in client
assert "GT_OST_ETHEREAL" in client and "GT_OST_RAIDIN_BONES" in client
assert "GT_OST_40000FT" in client
assert "GonzoMusicClient.shouldSuppressMusicStart(Minecraft.getInstance())" in mixin

print("Music debug command/network/client roll checks passed")
