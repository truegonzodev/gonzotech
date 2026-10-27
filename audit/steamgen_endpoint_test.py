#!/usr/bin/env python3
"""Execute the actual 0.3.307 virtual-endpoint methods against tiny world stubs.

This is not a Minecraft/NeoForge compile or an in-game test. Only the endpoint
methods are extracted unchanged; chunk/index/controller boundaries are stubbed.
Source pins run separately in pipe_routing_perf_test even without a JDK.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
home = os.environ.get("JAVA_HOME")
java = str(Path(home) / "bin/java") if home else shutil.which("java")
javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")
if os.environ.get("ECJ_JAR"):
    compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
elif javac and Path(javac).is_file():
    compiler = [javac, "--release", "21"]
else:
    raise SystemExit("javac or ECJ_JAR required")
source = (ROOT / "src/main/java/com/gonzotech/machines/steamgen/SteamGenStructure.java").read_text()
start = source.index("    public static Transfer.Receiver waterReceiverAt(")
end = source.index("    /**\n     * Максимальный номинальный приём", start)
methods = source[start:end]
# A stub world throws if production accidentally switches back to forced reads.
stubs = r'''
import java.util.*;
public class SteamGenEndpointSelfTest {
    record BlockPos(int x, int z) {
        int getX() { return x; }
        int getZ() { return z; }
        BlockPos immutable() { return this; }
    }
    record BlockState(boolean water, boolean heat) {}
    static class Level {}
    static class ServerLevel extends Level {
        final Map<BlockPos, BlockPos> index = new HashMap<>();
        final Map<Long, Chunk> chunks = new HashMap<>();
        ServerLevel getChunkSource() { return this; }
        Chunk getChunkNow(int x, int z) { return chunks.get(key(x, z)); }
        Object getBlockEntity(BlockPos pos) { throw new AssertionError("forced BE read"); }
        Chunk getChunk(int x, int z) { throw new AssertionError("forced chunk read"); }
    }
    static class Chunk {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, Object> entities = new HashMap<>();
        BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, new BlockState(false, false)); }
        Object getBlockEntity(BlockPos pos) { return entities.get(pos); }
    }
    static class SteamGenCoreBlockEntity {
        boolean removed, formed = true;
        long water, heat;
        int calls;
        boolean isRemoved() { return removed; }
        boolean isFormedController() { return formed; }
        long receiveWaterFromPort(BlockPos port, long amount, boolean simulate) {
            calls++;
            if (!simulate) water += amount;
            return amount;
        }
        long receiveGthFromPort(BlockPos port, long amount, boolean simulate) {
            calls++;
            if (!simulate) heat += amount;
            return amount;
        }
    }
    static class Transfer {
        interface Receiver { long receive(long amount, boolean simulate); }
    }
    static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    static BlockPos controllerAt(ServerLevel level, BlockPos port) { return level.index.get(port); }
    static boolean isWaterPort(BlockState state) { return state.water(); }
    static boolean isHeatPort(BlockState state) { return state.heat(); }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
'''
cases = r'''
    public static void main(String[] args) {
        check(waterReceiverAt(new Level(), new BlockPos(0, 0)) == null, "client world");
        for (boolean heat : new boolean[]{false, true}) {
            ServerLevel level = new ServerLevel();
            // Negative coordinates and separate root/port chunks are deliberate.
            BlockPos port = new BlockPos(-1, -17), root = new BlockPos(16, 0);
            Chunk pc = new Chunk(), rc = new Chunk();
            long pk = key(-1, -2), rk = key(1, 0);
            level.chunks.put(pk, pc);
            level.chunks.put(rk, rc);
            SteamGenCoreBlockEntity old = new SteamGenCoreBlockEntity();
            pc.states.put(port, new BlockState(!heat, heat));
            rc.entities.put(root, old);
            check(portReceiverAt(level, port, heat) == null, "unindexed port");
            level.index.put(port, root);
            Transfer.Receiver endpoint = heat ? gthReceiverAt(level, port) : waterReceiverAt(level, port);
            check(endpoint != null, "live endpoint");
            check(endpoint.receive(11, true) == 11, "simulate accepted");
            check(old.water == 0 && old.heat == 0, "simulate mutated resource");
            check(endpoint.receive(11, false) == 11, "commit accepted");
            check((heat ? old.heat : old.water) == 11, "wrong resource");
            int before = old.calls;
            level.chunks.remove(rk);
            check(portReceiverAt(level, port, heat) == null, "unloaded root factory");
            check(endpoint.receive(7, false) == 0, "unloaded root callback");
            level.chunks.put(rk, rc);
            level.chunks.remove(pk);
            check(portReceiverAt(level, port, heat) == null, "unloaded port factory");
            check(endpoint.receive(7, true) == 0, "unloaded port simulate");
            check(endpoint.receive(7, false) == 0, "unloaded port commit");
            level.chunks.put(pk, pc);
            old.removed = true;
            check(endpoint.receive(7, false) == 0, "removed BE");
            old.removed = false;
            old.formed = false;
            check(endpoint.receive(7, false) == 0, "unformed BE");
            old.formed = true;
            pc.states.put(port, new BlockState(heat, !heat));
            check(endpoint.receive(7, false) == 0, "wrong port kind");
            pc.states.put(port, new BlockState(!heat, heat));
            level.index.remove(port);
            check(endpoint.receive(7, false) == 0, "removed index");
            level.index.put(port, root);
            SteamGenCoreBlockEntity replacement = new SteamGenCoreBlockEntity();
            rc.entities.put(root, replacement);
            check(endpoint.receive(7, false) == 0, "old endpoint redirected to replacement");
            check(replacement.calls == 0 && old.calls == before, "stale endpoint reached a BE");
            Transfer.Receiver fresh = portReceiverAt(level, port, heat);
            check(fresh != null && fresh.receive(7, false) == 7, "fresh endpoint rejected replacement");
            rc.entities.put(root, new Object());
            check(endpoint.receive(7, false) == 0, "wrong BE type");
            check(portReceiverAt(level, port, heat) == null, "wrong BE factory");
        }
        System.out.println("actual endpoint methods: water/GTH lifecycle cases passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix="gonzotech-endpoint-") as output:
    path = Path(output) / "SteamGenEndpointSelfTest.java"
    path.write_text(stubs + methods + cases)
    subprocess.run(compiler + ["-d", output, str(path)], check=True)
    subprocess.run([java, "-cp", output, "SteamGenEndpointSelfTest"], check=True)
