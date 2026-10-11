#!/usr/bin/env python3
"""Пин онбординга первого запуска (инструмент агента, 11.10.2026).

Фиксирует контракт фич 0.3.183: экран-введение, кнопка «О моде», приветствие в
чат, override тултипов выживания/хардкора и авто-скип варнов об экспериментальных
настройках. Только статические проверки (без JDK).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
JAVA = ROOT / "src/main/java/com/gonzotech"
LANG = RES / "assets/gonzotech/lang"
TEX = RES / "assets/gonzotech/textures/gui"


def check(ok, msg):
    assert ok, msg


for lang in ("ru_ru", "en_us"):
    d = json.loads((LANG / f"{lang}.json").read_text(encoding="utf-8"))
    for key in ["gui.gonzotech.onboarding.title", "gui.gonzotech.onboarding.ok",
                "gui.gonzotech.onboarding.button.tooltip", "gui.gonzotech.onboarding.body",
                "gui.gonzotech.welcome.survival", "gui.gonzotech.welcome.hardcore",
                "gui.gonzotech.welcome.generic"]:
        check(key in d and d[key].strip(), f"{lang}: {key} present")
    # override ванильных тултипов: ванильная строка сохранена И добавлен блок Gonzotech
    check("Gonzotech:" in d["selectWorld.gameMode.survival.info"], f"{lang}: survival.info has Gonzotech block")
    hc = d["selectWorld.gameMode.hardcore.info"]
    check("Gonzotech:" in hc and hc.count("\n") >= 4, f"{lang}: hardcore.info has 4-line Gonzotech block")

check((TEX / "onboarding_button.png").is_file(), "icon PNG exists")
check((TEX / "onboarding_panel.png").is_file(), "panel placeholder PNG exists")
check((TEX / "onboarding_frame.png").is_file(), "frame placeholder PNG exists")
# Суперплоский мир: оверворлд не должен быть переопределён датапаком.
check(not (RES / "data/minecraft/dimension/overworld.json").exists(),
      "overworld dimension override removed (superflat works)")

onb = (JAVA / "core/client/onboard/OnboardingClient.java").read_text(encoding="utf-8")
check("@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)" in onb, "client subscriber registered")
check("ScreenEvent.Init.Post" in onb and "ClientTickEvent.Post" in onb, "hooks title init and first join")
check("gonzotech_onboarded.marker" in onb and "gonzotech_welcomed_" in onb and "currentWorldKey" in onb,
      "onboard flag global + welcome marker per-world")

screen = (JAVA / "core/client/onboard/GonzoOnboardingScreen.java").read_text(encoding="utf-8")
check("extends Screen" in screen and "gui.gonzotech.onboarding.body" in screen, "onboarding screen renders lang body")

skip = (JAVA / "space/client/ExperimentalWarningSkip.java").read_text(encoding="utf-8")
check("selectWorld.backupQuestion.experimental" in skip and "selectWorld.warning.experimental.title" in skip,
      "skip covers both experimental-warning screens")

print("OK: onboarding content contract holds")
