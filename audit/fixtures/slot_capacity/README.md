# Slot-capacity audit fixtures

Offline upstream admission algorithms, NOT Minecraft runtime or a NeoForge binary.
InvWrapper/SidedInvWrapper are unchanged NeoForge1.21.4-branch source files, copyright Forge Development LLC and contributors, SPDX LGPL-2.1-only; upstream license: https://github.com/neoforged/NeoForge/blob/1.21.4/LICENSE.txt .
HopperMethods.java.txt contains only tryMoveInItem/canPlaceItemInContainer/canMergeItems extracted from a **non-authoritative decompilation mirror**, not official Mojang source. The manifest pins all origins/commits/hashes. No claim that this proves the exact installed21.4.157 binary.

The test compiles these algorithms against narrow MC doubles and manually routes calls through extracted production mixin hook bodies. It also compiles the complete new mixin sources against annotation/API doubles. This is not a Mixin application/bootstrap test; injector targets and compatibility still require native startup/game acceptance. No runtime network downloads.
