#!/usr/bin/env python3
"""Execute extracted production hook bodies against tiny API stubs, plus static wiring.
Not Minecraft, not a Mixin transformation or real rendering test. Java 21 required.
"""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/main/java/com/gonzotech/mixin'
ui = (SRC / 'client/InBedFaintingScreenMixin.java').read_text()
status = (SRC / 'SleepStatusFaintingMixin.java').read_text()
server = (SRC / 'ServerPlayerFaintingMixin.java').read_text()


def method(source, name):
    start = re.search(r'private (?:void|boolean) ' + re.escape(name) + r'\(', source).start()
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('private ', 'public ', 1)


mixins = json.loads((ROOT / 'src/main/resources/gonzotech.mixins.json').read_text())
assert 'SleepStatusFaintingMixin' in mixins['mixins']
assert 'client.InBedFaintingScreenMixin' in mixins['client']
assert 'client.InBedFaintingScreenMixin' not in mixins['mixins']
assert 'method = "update"' in status and 'ServerPlayer;isSleeping()Z' in status
assert 'activePlayers' not in status.split('return ')[-1]  # never remove a faint from the denominator
assert 'method = "displayClientMessage"' in server and 'cancellable = true' in server
assert 'method = "init", at = @At("TAIL")' in ui
assert 'method = "render", at = @At("HEAD")' in ui
assert 'sendWakeUp' not in ui and 'new Button' not in ui and '.builder(' not in ui
assert 'gonzotech$bedButtonY - 48' in ui and 'leaveBedButton.getY() - 48' not in ui
for locale,label in [('ru_ru','Встать'),('en_us','Get up')]:
    lang = json.loads((ROOT / f'src/main/resources/assets/gonzotech/lang/{locale}.json').read_text())
    assert lang['gui.gonzotech.faint.get_up'] == label
    assert 'multiplayer.stopSleeping' not in lang  # vanilla bed text was not replaced globally

hooks = '\n'.join([method(ui,'gonzotech$initFaintButton'),method(ui,'gonzotech$updateFaintButton'),
                   method(status,'gonzotech$countOnlyRealSleep'),method(server,'gonzotech$hideBedStatusDuringFaint')])
harness = '''
public class FaintUiHarness extends ServerPlayer {
    Button leaveBedButton;
    int gonzotech$bedButtonY;
    Component gonzotech$bedButtonMessage;
    boolean gonzotech$faintPresentation;
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    static final Operation<Boolean> NATIVE_SLEEP = args -> ((ServerPlayer)args[0]).sleeping;
    public static void main(String[] args) {
        FaintUiHarness h = new FaintUiHarness();
        Minecraft.getInstance().player = h;
        Component bed = Component.translatable("multiplayer.stopSleeping");
        // Regular bed untouched; late metadata changes the existing button, never replaces it.
        h.leaveBedButton = new Button(200, bed);
        Button originalButton = h.leaveBedButton;
        h.gonzotech$initFaintButton(new CallbackInfo());
        check(h.leaveBedButton.y == 200 && h.leaveBedButton.message == bed);
        h.faint = true;
        for (int i=0; i<200; i++) {
            h.gonzotech$updateFaintButton();
            check(h.leaveBedButton.y == 152 && h.leaveBedButton == originalButton);
        }
        check(((TranslatableContents)h.leaveBedButton.message.getContents()).getKey().equals("gui.gonzotech.faint.get_up"));
        // Resize / GUI-scale changes reconstruct the native widget, then apply exactly one offset.
        for (int y : new int[]{320,200,140,500}) {
            h.leaveBedButton = new Button(y,bed);
            h.gonzotech$initFaintButton(new CallbackInfo());
            h.gonzotech$updateFaintButton();
            check(h.leaveBedButton.y == y-48);
        }
        h.faint = false;
        h.gonzotech$updateFaintButton();
        check(h.leaveBedButton.y == 500 && h.leaveBedButton.message == bed);
        Minecraft.getInstance().player = null;
        h.gonzotech$updateFaintButton();
        check(h.leaveBedButton.y == 500);
        // Hook truth table: native lying != a real sleep vote.
        for (boolean faint : new boolean[]{false,true}) for (boolean sleeping : new boolean[]{false,true}) {
            h.faint = faint; h.sleeping = sleeping;
            check(h.gonzotech$countOnlyRealSleep(h,NATIVE_SLEEP) == (sleeping && !faint));
        }
        // Model SleepStatus.update's change detection with the actual production vote hook.
        // Other awake/real sleeping players remain unchanged on faint start and wake.
        for (int others=0; others<4; others++) {
            int before = others;
            h.faint=true; h.sleeping=true;
            int during = others + (h.gonzotech$countOnlyRealSleep(h,NATIVE_SLEEP) ? 1 : 0);
            h.sleeping=false; h.faint=false;
            int after = others + (h.gonzotech$countOnlyRealSleep(h,NATIVE_SLEEP) ? 1 : 0);
            check(before == during && during == after);
        }
        h.faint=false; h.sleeping=true;
        check(h.gonzotech$countOnlyRealSleep(h,NATIVE_SLEEP));
        // Only translated bed-status actionbar packets to a fainting recipient are suppressed.
        String[] keys = {"sleep.skipping_night","sleep.players_sleeping","other.actionbar"};
        for (boolean faint : new boolean[]{false,true}) for (boolean overlay : new boolean[]{false,true}) {
            h.faint = faint;
            for (String key : keys) {
                CallbackInfo ci = new CallbackInfo();
                h.gonzotech$hideBedStatusDuringFaint(Component.translatable(key),overlay,ci);
                check(ci.cancelled == (faint && overlay && key.startsWith("sleep.")));
            }
            CallbackInfo ci = new CallbackInfo();
            h.gonzotech$hideBedStatusDuringFaint(new Component("sleep.skipping_night"),overlay,ci);
            check(!ci.cancelled); // literal chat/actionbar text must not be swallowed
        }
        System.out.println("Faint UI production hook-body checks passed: " + checks + " (API stubs, not Minecraft)");
    }
''' + hooks + '''
}
class ServerPlayer { boolean faint, sleeping; }
class AlcoholFainting { static boolean isFainting(ServerPlayer p) { return p.faint; } }
interface Operation<T> { T call(Object... args); }
class Minecraft {
    static final Minecraft INSTANCE = new Minecraft();
    ServerPlayer player;
    static Minecraft getInstance() { return INSTANCE; }
}
record TranslatableContents(String key) { public String getKey() { return key; } }
record Component(Object contents) {
    static Component translatable(String key) { return new Component(new TranslatableContents(key)); }
    public Object getContents() { return contents; }
}
class Button {
    int y; Component message;
    Button(int y, Component message) { this.y=y; this.message=message; }
    int getY() { return y; } void setY(int y) { this.y=y; }
    Component getMessage() { return message; } void setMessage(Component c) { message=c; }
}
class CallbackInfo { boolean cancelled; void cancel() { cancelled=true; } }
'''
home = os.environ.get('JAVA_HOME')
java = str(Path(home)/'bin/java') if home else shutil.which('java')
javac = str(Path(home)/'bin/javac') if home else shutil.which('javac')
if not java or not Path(java).is_file():
    raise SystemExit('Java 21 required: set JAVA_HOME or PATH')
with tempfile.TemporaryDirectory(prefix='gonzotech-faint-ui-') as tmp:
    source = Path(tmp)/'FaintUiHarness.java'
    source.write_text(harness)
    if os.environ.get('ECJ_JAR'):
        compiler = [java,'-jar',os.environ['ECJ_JAR'],'-21','-proc:none']
    elif javac and Path(javac).is_file():
        compiler = [javac,'--release','21']
    else:
        raise SystemExit('javac or ECJ_JAR required')
    subprocess.run(compiler+['-d',tmp,str(source)],check=True)
    subprocess.run([java,'-cp',tmp,'FaintUiHarness'],check=True)
print('Faint UI mixin/client isolation/localization wiring passed (static)')
