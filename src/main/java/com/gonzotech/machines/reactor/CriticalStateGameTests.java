package com.gonzotech.machines.reactor;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.block.entity.ReactorFuelSheathBlockEntity;
import com.gonzotech.machines.inventory.SlotCapacity;
import com.gonzotech.machines.menu.ReactorMenu;
import com.gonzotech.machines.registry.ModMachines;
import com.gonzotech.radiation.engine.ExternalRadiation;
import com.gonzotech.radiation.engine.MenuDoseTransaction;
import com.gonzotech.radiation.RadUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

/** CRIT-1/2 invariant regressions; these must fail, not merely observe the old defect.
 * Reactor fixture binds real BEs directly: tests menu ownership, not shell validation/thermal physics.
 */
@GameTestHolder("gonzotech")
@PrefixGameTestTemplate(false)
public final class CriticalStateGameTests {
    private record ReactorFixture(ReactorControlPanelBlockEntity panel,
                                  ReactorFuelSheathBlockEntity sheath, BlockPos min, BlockPos max) {}

    private static ReactorFixture reactor(GameTestHelper h) {
        BlockPos min = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos max = min.offset(3, 3, 3);
        BlockPos panelPos = min.offset(0, 1, 1);
        BlockPos sheathPos = min.offset(1, 1, 1);
        h.getLevel().setBlock(panelPos, ModMachines.THIRD_URANIUM_REACTOR_CONTROL_PANEL.get().defaultBlockState(), 3);
        h.getLevel().setBlock(sheathPos, ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get().defaultBlockState(), 3);
        var panel = (ReactorControlPanelBlockEntity) h.getLevel().getBlockEntity(panelPos);
        var sheath = (ReactorFuelSheathBlockEntity) h.getLevel().getBlockEntity(sheathPos);
        sheath.setReactorBounds(min, max);
        panel.configureStructure(min, max, 1, 0, new long[0], new long[0], new long[0]);
        return new ReactorFixture(panel, sheath, min, max);
    }

    private static ReactorMenu open(ReactorFixture f, ServerPlayer player) {
        player.setPos(f.panel().getBlockPos().getX() + 0.5, f.panel().getBlockPos().getY() + 0.5,
                f.panel().getBlockPos().getZ() + 0.5);
        var menu = new ReactorMenu(37, player.getInventory(), f.panel(), f.panel().fuelSlotCount());
        player.containerMenu = menu;
        menu.setCarried(new ItemStack(ModItems.UF_TVEL.get()));
        return menu;
    }

    private static void rejected(GameTestHelper h, ServerPlayer player, ReactorMenu menu,
                                 ReactorFuelSheathBlockEntity old, ReactorFuelSheathBlockEntity live) {
        h.assertTrue(!menu.stillValid(player), "old menu must be invalid before a click");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(player.containerMenu != menu, "stale GUI must close");
        h.assertTrue(menu.getCarried().isEmpty(), "vanilla close returned the cursor");
        h.assertTrue(old.storedTvelCount() == 0 && live.storedTvelCount() == 0, "no stale write or remap");
        h.assertTrue(player.getInventory().countItem(ModItems.UF_TVEL.get()) == 1, "single TVEL preserved");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(player.getInventory().countItem(ModItems.UF_TVEL.get()) == 1, "late repeated click cannot award twice");
    }

    @GameTest(template = "radiation_empty")
    public static void replacedSheathRejectsBeforeCursorDebit(GameTestHelper h) {
        var f = reactor(h);
        var player = h.makeMockServerPlayerInLevel();
        try {
            var menu = open(f, player);
            h.assertTrue(menu.stillValid(player), "fresh menu valid");
            var pos = f.sheath().getBlockPos();
            var replacement = new ReactorFuelSheathBlockEntity(pos, f.sheath().getBlockState());
            h.getLevel().removeBlockEntity(pos);
            h.getLevel().setBlockEntity(replacement);
            replacement.setReactorBounds(f.min(), f.max());
            // Same panel, same bounds/count, replacement within the same tick.
            rejected(h, player, menu, f.sheath(), replacement);
        } finally {
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "radiation_empty")
    public static void reformedStructureRejectsOldMenu(GameTestHelper h) {
        var f = reactor(h);
        var player = h.makeMockServerPlayerInLevel();
        try {
            var menu = open(f, player);
            f.panel().clearStructure();
            f.panel().configureStructure(f.min(), f.max(), 1, 0, new long[0], new long[0], new long[0]);
            rejected(h, player, menu, f.sheath(), f.sheath());
            var fresh = new ReactorMenu(38, player.getInventory(), f.panel(), f.panel().fuelSlotCount());
            h.assertTrue(fresh.stillValid(player), "explicitly reopened menu uses current generation");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    private static final class SidedBox extends SimpleContainer implements WorldlyContainer, SlotCapacity {
        private final int limit;
        private final boolean allow;
        SidedBox(int limit, boolean allow) { super(9); this.limit = limit; this.allow = allow; }
        @Override public int[] getSlotsForFace(Direction side) { return new int[]{1, 0}; }
        @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return allow; }
        @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return allow; }
        @Override public int slotCapacity(int slot) { return slot == 1 ? limit : 64; }
    }

    private static ItemStack stack(int count, double rate) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        ExternalRadiation.write(stack, rate * RadUnits.MILLI, 100);
        return stack;
    }

    private static double mass(ItemStack stack) { return stack.getCount() * ExternalRadiation.read(stack).rate() / RadUnits.MILLI; }
    private static void eq(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.00001, message + ": " + actual + " != " + expected);
    }

    @GameTest(template = "radiation_empty")
    public static void sidedInsertConservesInducedMass(GameTestHelper h) {
        for (boolean ordinary : new boolean[]{false, true})
            for (boolean reverse : new boolean[]{false, true})
                for (int before : new int[]{0, 4, 60, 64})
                    for (int offered : new int[]{4, 16})
                        for (int limit : new int[]{8, 64}) {
                            var box = new SidedBox(limit, true);
                            if (before > 0) box.setItem(1, stack(before, reverse ? 6 : 2));
                            var source = stack(offered, reverse ? 2 : 6);
                            double total = mass(source) + mass(box.getItem(1));
                            var handler = ordinary ? new InvWrapper(box) : new SidedInvWrapper(box, Direction.NORTH);
                            int slot = ordinary ? 1 : 0;
                            var simulated = handler.insertItem(slot, source, true);
                            eq(h, box.getItem(1).getCount(), before, "simulate leaves count");
                            eq(h, mass(source) + mass(box.getItem(1)), total, "simulate leaves rates");
                            var left = handler.insertItem(slot, source, false);
                            eq(h, left.getCount(), simulated.getCount(), "simulate predicts accepted count");
                            eq(h, left.getCount() + box.getItem(1).getCount(), offered + before, "count conservation");
                            eq(h, mass(left) + mass(box.getItem(1)), total, "induced mass conservation");
                            eq(h, source.getCount(), offered, "caller input is not consumed");
                            eq(h, ExternalRadiation.read(source).rate() / RadUnits.MILLI, reverse ? 2 : 6, "caller rate unchanged");
                        }
        var blocked = new SidedBox(64, false);
        blocked.setItem(1, stack(4, 2));
        var offer = stack(4, 6);
        var remainder = new SidedInvWrapper(blocked, Direction.NORTH).insertItem(0, offer, false);
        eq(h, remainder.getCount(), 4, "blocked face rejects");
        eq(h, mass(remainder) + mass(blocked.getItem(1)), 32, "blocked face preserves dose");
        h.succeed();
    }

    @GameTest(template = "radiation_empty")
    public static void sidedInsertInsideMenuTransaction(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (boolean reverse : new boolean[]{false, true}) {
            var box = new SidedBox(64, true);
            box.setItem(1, stack(4, reverse ? 6 : 2));
            var menu = new ChestMenu(MenuType.GENERIC_9x1, 40, player.getInventory(), box, 1);
            h.assertTrue(menu.getContainer() == box, "outer transaction observes the actual destination");
            h.assertTrue(menu.getRowCount() == 1, "one-row layout matches the nine-slot box");
            menu.setCarried(stack(4, reverse ? 2 : 6));
            try (var transaction = new MenuDoseTransaction(menu, ClickType.PICKUP)) {
                menu.setCarried(new SidedInvWrapper(box, Direction.NORTH).insertItem(0, menu.getCarried(), false));
                eq(h, mass(box.getItem(1)), 32, "wrapper is correct before outer reconciliation");
            }
            eq(h, mass(box.getItem(1)), 32, "outer transaction does not double-apply");
            eq(h, box.getItem(1).getCount(), 8, "outer transaction leaves count");
        }
        h.succeed();
    }
}
