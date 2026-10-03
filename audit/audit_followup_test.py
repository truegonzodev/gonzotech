#!/usr/bin/env python3
"""27 Sep follow-up: execute production method bodies with API stubs (NOT Minecraft).
Checks F1 render routing, translated title selection and per-cell fluid callbacks.
Does not simulate entity collision dispatch, networking, resource reload or GPU rendering.
Java 21 via JAVA_HOME (+ optional ECJ_JAR), matching other audit runners.
"""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/main/java/com/gonzotech'
client = (SRC / 'core/psyche/client/PsycheCrisisClient.java').read_text(encoding='utf-8')
notes = (SRC / 'chalkboard/client/ScholarNotesScreen.java').read_text(encoding='utf-8')
fluid = (SRC / 'core/fluid/ModFluidBlock.java').read_text(encoding='utf-8')
tap = (SRC / 'machines/client/DispensingTapScreen.java').read_text(encoding='utf-8')


def method(source, name):
    start = re.search(r'(?:public|private) (?:static )?(?:void|String) ' + name + r'\(', source).start()
    brace = source.index('{', start)
    end, depth = brace + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


# Name/unit keys are reused; separators are white literals (author 27.09.2026),
# numbers stay literal so the base colour can differ from the separators.
assert 'literal(":")' in tap and 'literal(" /")' in tap
assert 'ChatFormatting.AQUA)' in tap and 'ChatFormatting.GOLD)' in tap
# Раунд 14: значения шкал крана — в формате эпохи 3 X.Y (GtUnits.x1).
# Compile regression: this screen must explicitly import the cross-package formatter.
assert 'import com.gonzotech.core.text.GtUnits;' in tap
assert 'GtUnits.x1(menu.distillate()),' in tap and 'GtUnits.x1(menu.wort()),' in tap
assert 'GtUnits.x1(DispensingTapBlockEntity.DISTILLATE_CAPACITY),' in tap
assert 'GtUnits.x1(DispensingTapBlockEntity.WORT_CAPACITY),' in tap
notes_code = re.sub(r'/\*.*?\*/|//[^\n]*', '', notes, flags=re.S)
assert not re.search(r'"[^"\n]*[А-Яа-яЁё][^"\n]*"', notes_code)
assert 'structureSubpage + 1, il.deckView().subpageCount()).getString()' in notes
keys = ['gui.gonzotech.notes.' + x for x in ('assembly', 'layer.bottom', 'layer.middle', 'layer.top', 'layer')]
# 0.3.32: tap lines use plain-name keys (no format args).
keys += ['gui.gonzotech.distillate.name', 'gui.gonzotech.wort.name']
for locale in ('ru_ru', 'en_us'):
    lang = json.loads((ROOT / f'src/main/resources/assets/gonzotech/lang/{locale}.json').read_text(encoding='utf-8'))
    for key in keys:
        assert key in lang
        if key.startswith('gui.gonzotech.notes.'):
            assert lang[key].count('%s') == (2 if key.endswith('.layer') else 1)
        if locale == 'en_us':
            assert not re.search('[А-Яа-яЁё]', lang[key])

milli = re.search(r'public static final double MILLI = ([^;]+);',
                 (SRC / 'radiation/RadUnits.java').read_text(encoding='utf-8'))[1]
harness = '''
import java.util.Arrays;
public class AuditFollowupHarness {
    static boolean fakeDeath;
    static int screenEffectRenders, deathRenders, checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    static void renderScreenEffect(GuiGraphics g, int w, int h) { screenEffectRenders++; }
    static void renderFakeDeath(GuiGraphics g, Minecraft mc, int w, int h) { deathRenders++; }
    public static void main(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        for (boolean player : new boolean[]{false,true})
        for (boolean hidden : new boolean[]{false,true})
        for (boolean death : new boolean[]{false,true})
        for (int width : new int[]{320,640,1280}) {
            mc.player = player ? new Object() : null;
            mc.options.hideGui = hidden; fakeDeath = death;
            screenEffectRenders = deathRenders = 0;
            for (int frame = 0; frame < 100; frame++)
                onRenderGui(new RenderGuiEvent.Post(new GuiGraphics(width,240)));
            check(deathRenders == (player && death ? 100 : 0));
            // 0.3.30: F1 hides the HUD only; the crisis status effect persists (author request).
            check(screenEffectRenders == (player ? 100 : 0));
            check(fakeDeath == death); // F1/render cannot dismiss or stack the illusion.
            check(mc.options.hideGui == hidden); // Never force the user's HUD on.
        }
        check(structureTitle(new StructureModel(true,1),0).equals("gui.gonzotech.notes.assembly[1]"));
        for (int total : new int[]{1,2,3,4,5,8}) for (int sub=0; sub<total; sub++) {
            String key = "gui.gonzotech.notes.layer";
            String expected;
            if (total == 2 || total == 3) {
                key += sub == 0 ? ".bottom" : sub == total-1 ? ".top" : ".middle";
                expected = key + "[" + (sub+1) + "]";
            } else expected = key + "[" + (sub+1) + ", " + total + "]";
            check(structureTitle(new StructureModel(false,total),sub).equals(expected));
        }
        double[] doses = {7,3,13,0,0,0.1};
        int index=0;
        for (FluidProbe.Kind kind : FluidProbe.Kind.values()) {
            FluidProbe probe = new FluidProbe(); probe.kind=kind;
            for (boolean client : new boolean[]{false,true}) for (int cells : new int[]{1,2,4,8,256}) {
                ServerPlayer p = new ServerPlayer();
                for (int tick=1; tick<=120; tick++) {
                    p.tickCount=tick;
                    double before=p.dose;
                    for (int i=0; i<cells; i++) probe.entityInside(new BlockState(),new Level(client),new BlockPos(),p);
                    if (tick % 20 != 0 || client) check(p.dose==before);
                    check(Double.isFinite(p.dose) && p.dose>=before);
                }
                double expected = client ? 0 : 6*cells*doses[index]*RadUnits.MILLI;
                check(Math.abs(p.dose-expected)<0.01);
                ServerPlayer other = new ServerPlayer();
                other.tickCount=20;
                probe.entityInside(new BlockState(),new Level(client),new BlockPos(),other);
                check(Math.abs(other.dose-(client ? 0 : doses[index]*RadUnits.MILLI))<0.01);
            }
            // Non-living entities must be safe; living non-players get effects but no player-dose cast.
            probe.entityInside(new BlockState(),new Level(false),new BlockPos(),new Entity());
            LivingEntity mob = new LivingEntity(); mob.tickCount=20;
            probe.entityInside(new BlockState(),new Level(false),new BlockPos(),mob);
            check(mob.effects == (kind==FluidProbe.Kind.ETHANOL || kind==FluidProbe.Kind.FORMALDEHYDE ? 2
                    : kind==FluidProbe.Kind.SULFURIC_ACID || kind==FluidProbe.Kind.DISTILLATE ? 1 : 0));
            index++;
        }
        System.out.println("Audit follow-up method-body checks passed: " + checks + " (API stubs, not Minecraft)");
    }
''' + method(client, 'onRenderGui') + '\n' + method(notes, 'structureTitle') + '''
}
class FluidProbe extends LiquidBlock {
    enum Kind { SULFURIC_ACID, ETHANOL, FORMALDEHYDE, MASH, WORT, DISTILLATE }
    Kind kind;
''' + method(fluid, 'entityInside') + '''
}
class LiquidBlock { public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {} }
class BlockState {}
class BlockPos {}
class Level { boolean isClientSide; Level(boolean client) { isClientSide=client; } }
class Entity {}
class LivingEntity extends Entity {
    int tickCount, effects;
    void addEffect(MobEffectInstance effect) { effects++; }
}
class ServerPlayer extends LivingEntity { double dose; }
record MobEffectInstance(Object effect, int duration, int amplifier) {}
class MobEffects { static final Object POISON=new Object(), CONFUSION=new Object(), BLINDNESS=new Object(), WITHER=new Object(); }
class RadUnits { static final double MILLI = ''' + milli + '''; }
class PsycheChemical { static void addDoseToxicity(ServerPlayer p, double dose) { p.dose+=dose; } }
record StructureModel(boolean assembly, int sizeY) {}
record Component(String key, Object[] args) {
    static Component translatable(String key, Object... args) { return new Component(key,args); }
    String getString() { return key + Arrays.toString(args); }
}
record GuiGraphics(int guiWidth, int guiHeight) {}
class RenderGuiEvent { record Post(GuiGraphics graphics) { GuiGraphics getGuiGraphics() { return graphics; } } }
class Minecraft {
    static final Minecraft INSTANCE = new Minecraft();
    Object player;
    Options options = new Options();
    static Minecraft getInstance() { return INSTANCE; }
}
class Options { boolean hideGui; }
'''
home = os.environ.get('JAVA_HOME')
java = str(Path(home)/'bin/java') if home else shutil.which('java')
javac = str(Path(home)/'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file():
    raise SystemExit('Java 21 required: set JAVA_HOME or PATH')
with tempfile.TemporaryDirectory(prefix='gonzotech-audit-followup-') as tmp:
    source = Path(tmp)/'AuditFollowupHarness.java'
    source.write_text(harness, encoding='utf-8')
    if os.environ.get('ECJ_JAR'):
        compiler = [java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none','-encoding','UTF-8']
    elif javac and Path(javac).is_file():
        compiler = [javac,'--release','21','-encoding','UTF-8']
    else:
        raise SystemExit('javac or ECJ_JAR required')
    subprocess.run(compiler+['-d',tmp,str(source)],check=True)
    subprocess.run([java,'-cp',tmp,'AuditFollowupHarness'],check=True)
print('Tap / notes translation wiring checks passed (static)')
