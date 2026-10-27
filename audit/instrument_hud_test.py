#!/usr/bin/env python3
"""317: actual InstrumentHud registration/callback against GUI boundary doubles.
Checks the root layer placement, not GPU pixels or another mod's render pipeline.
The actual SolarWatchHud/SpeedometerHud are also compiled by typecheck_test.
"""
from pathlib import Path
import os,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'src/main/java/com/gonzotech'
mod=(SRC/'GonzoTechMod.java').read_text()
assert mod.count('modEventBus.addListener(com.gonzotech.core.client.InstrumentHud::registerLayers)')==1
assert 'EVENT_BUS.register(com.gonzotech.core.client.SolarWatchHud.class)' not in mod
assert 'EVENT_BUS.register(com.gonzotech.core.client.SpeedometerHud.class)' in mod
for name,height in [('SolarWatchHud',71),('SpeedometerHud',59)]:
    text=(SRC/f'core/client/{name}.java').read_text()
    assert 'RenderGuiEvent' not in text and 'onRenderGui' not in text
    assert 'public static void render(GuiGraphics graphics)' in text
    assert f'graphics.guiHeight() - {height}' in text
    assert 'mc.options.hideGui' in text and 'ModItems.TELIFON.get()' in text
    assert 'graphics.drawString(font, line, x, y, HUD_COLOR, true)' in text
    assert 'disableDepthTest' not in text and 'setShaderColor' not in text
speed=(SRC/'core/client/SpeedometerHud.java').read_text()
assert '@SubscribeEvent\n    public static void onClientTick' in speed
assert 'Math.hypot(dm.x, dm.z) * 20.0D' in speed
STUBS={
 'com/gonzotech/GonzoTechMod.java':'package com.gonzotech;public class GonzoTechMod{public static final String MOD_ID="gonzotech";}',
 'net/minecraft/resources/ResourceLocation.java':'package net.minecraft.resources;public record ResourceLocation(String namespace,String path){public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}}',
 'net/minecraft/client/gui/GuiGraphics.java':'package net.minecraft.client.gui;public class GuiGraphics{public int watches,speeds;}',
 'net/neoforged/neoforge/client/gui/VanillaGuiLayers.java':'package net.neoforged.neoforge.client.gui;public class VanillaGuiLayers{public static final net.minecraft.resources.ResourceLocation CHAT=new net.minecraft.resources.ResourceLocation("minecraft","chat");}',
 'net/neoforged/neoforge/client/event/RegisterGuiLayersEvent.java':'''package net.neoforged.neoforge.client.event;
public class RegisterGuiLayersEvent{
 public interface Layer{void render(net.minecraft.client.gui.GuiGraphics g,Object tick);}
 public int calls;public Layer layer;public net.minecraft.resources.ResourceLocation anchor,id;
 public void registerAbove(net.minecraft.resources.ResourceLocation a,net.minecraft.resources.ResourceLocation b,Layer c){calls++;anchor=a;id=b;layer=c;}
}''',
 'com/gonzotech/core/client/SolarWatchHud.java':'package com.gonzotech.core.client;public class SolarWatchHud{public static void render(net.minecraft.client.gui.GuiGraphics g){g.watches++;}}',
 'com/gonzotech/core/client/SpeedometerHud.java':'package com.gonzotech.core.client;public class SpeedometerHud{public static void render(net.minecraft.client.gui.GuiGraphics g){g.speeds++;}}',
 'InstrumentHudSelfTest.java':'''import com.gonzotech.core.client.InstrumentHud;import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;import net.neoforged.neoforge.client.gui.VanillaGuiLayers;import net.minecraft.client.gui.GuiGraphics;
public class InstrumentHudSelfTest{public static void main(String[] args){var e=new RegisterGuiLayersEvent();InstrumentHud.registerLayers(e);
 if(e.calls!=1||!e.anchor.equals(VanillaGuiLayers.CHAT)||!e.id.namespace().equals("gonzotech")||!e.id.path().equals("instruments"))throw new AssertionError("not exactly one layer above chat");
 var g=new GuiGraphics();e.layer.render(g,null);if(g.watches!=1||g.speeds!=1)throw new AssertionError("duplicated/missing instrument renderer");
 System.out.println("Instrument HUD layer callback PASS; registration tested, GPU not executed");}}'''
}
home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else shutil.which('java');javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file():raise SystemExit('Java 21 required')
compiler=[java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none'] if os.environ.get('ECJ_JAR') else [javac,'--release','21']
with tempfile.TemporaryDirectory(prefix='gonzo-instrument-hud-') as temp:
    root=Path(temp)
    for path,code in STUBS.items():p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code)
    p=root/'com/gonzotech/core/client/InstrumentHud.java';p.write_text((SRC/'core/client/InstrumentHud.java').read_text())
    subprocess.run(compiler+['-d',str(root/'classes')]+[str(p) for p in root.rglob('*.java')],check=True)
    subprocess.run([java,'-cp',str(root/'classes'),'InstrumentHudSelfTest'],check=True)
print('Instrument HUD wiring PASS: no Post/double render, same positions/colors/F1/hand guards and speed measurement')
