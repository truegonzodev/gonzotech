package com.gonzotech.radiation.engine;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.world.item.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("gonzotech")
@PrefixGameTestTemplate(false)
public final class RadiationGameTests {
    private static ItemStack stack(int n, double rate) {
        ItemStack s = new ItemStack(Items.DIAMOND, n);
        ExternalRadiation.write(s, rate, 100);
        return s;
    }
    private static void eq(GameTestHelper h, double actual, double expected, String label) {
        h.assertTrue(Math.abs(actual - expected) < 0.00001, label + ": " + actual + " != " + expected);
    }
    @GameTest(template="radiation_empty") public static void copySplitAndNoItemWrites(GameTestHelper h) {
        ItemStack s = stack(64, 2000000);
        var serialized = s.save(h.getLevel().registryAccess());
        ItemStack part = s.split(16);
        eq(h, ExternalRadiation.read(part).rate(), 2000000, "split per item");
        eq(h, s.getCount() + part.getCount(), 64, "split count");
        s.grow(16);
        for (int i=0; i<1000; i++) com.gonzotech.radiation.ItemRadioactivity.tickInducedAt(s, true, 90000000, 1, 120 + i*20);
        h.assertTrue(serialized.equals(s.save(h.getLevel().registryAccess())), "radiation changed serialized item");
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void pickupAndQuickCraft(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var box=new SimpleContainer(27);
        var menu=ChestMenu.threeRows(1, player.getInventory(), box);
        box.setItem(0, stack(1, 100)); box.setItem(1, stack(1, 300));
        menu.setCarried(stack(32, 200));
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0,1), ClickType.QUICK_CRAFT, player);
        menu.clicked(0, AbstractContainerMenu.getQuickcraftMask(1,1), ClickType.QUICK_CRAFT, player);
        menu.clicked(1, AbstractContainerMenu.getQuickcraftMask(1,1), ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2,1), ClickType.QUICK_CRAFT, player);
        eq(h, box.getItem(0).getCount(),2,"first count");
        eq(h, box.getItem(1).getCount(),2,"second count");
        eq(h, menu.getCarried().getCount(),30,"cursor count");
        eq(h, ExternalRadiation.read(box.getItem(0)).rate(),150,"first weighted rate");
        eq(h, ExternalRadiation.read(box.getItem(1)).rate(),250,"second weighted rate");
        eq(h, ExternalRadiation.read(menu.getCarried()).rate(),200,"remaining cursor");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0,0,ClickType.PICKUP,player);
        menu.clicked(1,0,ClickType.PICKUP,player);
        eq(h, box.getItem(1).getCount(),4,"merged count");
        eq(h, ExternalRadiation.read(box.getItem(1)).rate(),200,"merged rate");
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void automationAndDrops(GameTestHelper h) {
        var handler=new ItemStackHandler(1);
        handler.setStackInSlot(0,stack(2,100));
        ItemStack incoming=stack(2,300);
        handler.insertItem(0,incoming,true);
        eq(h,ExternalRadiation.read(handler.getStackInSlot(0)).rate(),100,"simulation did not mutate");
        handler.insertItem(0,incoming,false);
        eq(h,ExternalRadiation.read(handler.getStackInSlot(0)).rate(),200,"handler merge");
        var box=new SimpleContainer(1);box.setItem(0,stack(2,100));
        HopperBlockEntity.addItem(null,box,stack(2,300),null);
        eq(h,ExternalRadiation.read(box.getItem(0)).rate(),200,"hopper merge");
        ItemStack merged=ItemEntity.merge(stack(2,100),stack(2,300),64);
        eq(h,ExternalRadiation.read(merged).rate(),200,"drop merge");
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0,stack(2,100));
        player.getInventory().add(stack(2,300));
        eq(h,ExternalRadiation.read(player.getInventory().getItem(0)).rate(),200,"inventory pickup");
        var bundle=new BundleContents.Mutable(BundleContents.EMPTY);
        bundle.tryInsert(stack(2,100));bundle.tryInsert(stack(2,300));
        eq(h,ExternalRadiation.read(bundle.toImmutable().items().iterator().next()).rate(),200,"bundle merge");
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void ownerPersistence(GameTestHelper h) {
        var be=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
        be.setItem(0,stack(2,1234));
        var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(tag.contains(RadiationOwners.KEY),"missing owner sidecar");
        h.assertTrue(!be.saveCustomOnly(h.getLevel().registryAccess()).contains(RadiationOwners.KEY),"sidecar leaked into item data");
        var restored=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
        restored.loadWithComponents(tag,h.getLevel().registryAccess());
        eq(h,ExternalRadiation.read(restored.getItem(0)).rate(),1234,"chest load");
        var drop=new ItemEntity(h.getLevel(),0,0,0,stack(3,4321));
        var entityTag=drop.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var loaded=new ItemEntity(h.getLevel(),0,0,0,ItemStack.EMPTY);
        loaded.load(entityTag);
        eq(h,ExternalRadiation.read(loaded.getItem()).rate(),4321,"entity load");
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void wrappersAndCarriers(GameTestHelper h) {
        var box=new SimpleContainer(1);box.setItem(0,stack(2,100));
        new net.neoforged.neoforge.items.wrapper.InvWrapper(box).insertItem(0,stack(2,300),false);
        eq(h,ExternalRadiation.read(box.getItem(0)).rate(),200,"wrapper merge");
        box.clearContent();box.setItem(0,stack(2,100));box.addItem(stack(2,300));
        eq(h,ExternalRadiation.read(box.getItem(0)).rate(),200,"simple container merge");
        eq(h,ExternalRadiation.read(box.removeItemType(Items.DIAMOND,4)).rate(),200,"simple container collect");
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        for(var item:java.util.List.of(com.gonzotech.core.registry.ModItems.TONGS.get(),com.gonzotech.core.registry.ModItems.LADLE.get())) {
            ItemStack carrier=new ItemStack(item);
            var cargoItem = item instanceof com.gonzotech.core.item.LadleItem
                ? com.gonzotech.core.registry.ModItems.INGOT_ITEMS.get("mercury_ingot").get() : Items.DIAMOND;
            ItemStack first=new ItemStack(cargoItem,2),second=new ItemStack(cargoItem,2);
            ExternalRadiation.write(first,100,100);ExternalRadiation.write(second,300,100);
            var content=new SimpleContainer(1);content.setItem(0,first);
            Slot slot=new Slot(content,0,0,0);
            h.assertTrue(item.overrideStackedOnOther(carrier,slot,ClickAction.PRIMARY,player),"carrier insert");
            content.setItem(0,second);item.overrideStackedOnOther(carrier,slot,ClickAction.PRIMARY,player);
            var copy=carrier.copy();
            var out=((com.gonzotech.radiation.CarrierItem)item).takeCarried(copy);
            eq(h,out.getCount(),4,"carrier count");
            eq(h,ExternalRadiation.read(out).rate(),200,"carrier rate");
            eq(h,ExternalRadiation.readContents(copy).rate(),0,"emptied carrier");
            eq(h,ExternalRadiation.readContents(carrier).rate(),200,"independent carrier copy");
        }
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void creativeServerKnownDose(GameTestHelper h) {
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"radiation-test"));
        player.setGameMode(GameType.CREATIVE);
        var listener=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
            new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.getInventory().clearContent();player.getInventory().setItem(0,stack(64,200));
        CreativeRadiationBridge.begin(player);CreativeRadiationBridge.beforePacket(player);
        listener.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36,ItemStack.EMPTY));
        CreativeRadiationBridge.finish(player,64,RadiationViewNetwork.fingerprint(new ItemStack(Items.DIAMOND,64),player.registryAccess()));
        CreativeRadiationBridge.begin(player);CreativeRadiationBridge.beforePacket(player);
        listener.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37,new ItemStack(Items.DIAMOND,32)));
        listener.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(38,new ItemStack(Items.DIAMOND,32)));
        CreativeRadiationBridge.finish(player,0,0);
        eq(h,ExternalRadiation.read(player.getInventory().getItem(1)).rate(),200,"creative split 1");
        eq(h,ExternalRadiation.read(player.getInventory().getItem(2)).rate(),200,"creative split 2");
        CreativeRadiationBridge.begin(player);CreativeRadiationBridge.beforePacket(player);
        ItemStack drop=new ItemStack(Items.DIAMOND,1);
        CreativeRadiationBridge.dropped(player,drop);
        listener.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37,new ItemStack(Items.DIAMOND,31)));
        CreativeRadiationBridge.finish(player,0,0);
        eq(h,ExternalRadiation.read(drop).rate(),200,"creative Q");
        eq(h,player.getInventory().getItem(1).getCount()+player.getInventory().getItem(2).getCount()+drop.getCount(),64,"creative count unchanged");
        CreativeRadiationBridge.begin(player);CreativeRadiationBridge.beforePacket(player);
        listener.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(39,new ItemStack(Items.DIAMOND,64)));
        CreativeRadiationBridge.finish(player,0,0);
        eq(h,ExternalRadiation.read(player.getInventory().getItem(3)).rate(),0,"creative catalogue has no induced dose");
        CreativeRadiationBridge.forget(player);h.succeed();
    }
    @GameTest(template="radiation_empty") public static void plotnickCardLimits(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var be=new com.gonzotech.machines.block.entity.PlotnickBlockEntity(BlockPos.ZERO,
                com.gonzotech.machines.registry.ModMachines.PLOTNICK.get().defaultBlockState());
        for(int i=0;i<9;i++)be.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        var menu=new com.gonzotech.machines.menu.PlotnickMenu(1,player.getInventory(),be,
            new SimpleContainerData(com.gonzotech.machines.block.entity.PlotnickBlockEntity.DATA_SLOTS));
        player.getInventory().setItem(9,new ItemStack(com.gonzotech.machines.registry.ModMachines.MEMORY_CARD.get(),16));
        menu.clicked(11,0,ClickType.QUICK_MOVE,player);
        eq(h,be.getItem(9).getCount(),1,"left card limit");
        eq(h,be.getItem(10).getCount(),1,"right card limit");
        eq(h,player.getInventory().getItem(9).getCount(),14,"remaining cards preserved");
        h.succeed();
    }

    @GameTest(template="radiation_empty") public static void rapidMenuStress(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var box=new SimpleContainer(27);
        var menu=ChestMenu.threeRows(1,player.getInventory(),box);
        box.setItem(0,stack(64,100));box.setItem(1,stack(64,300));
        var random=new java.util.Random(306);
        double expected=25600;
        for(int i=0;i<2000;i++) {
            int slot=random.nextInt(menu.slots.size());
            int kind=random.nextInt(3);
            menu.clicked(slot,kind==2?random.nextInt(9):random.nextInt(2),
                kind==0?ClickType.PICKUP:kind==1?ClickType.QUICK_MOVE:ClickType.SWAP,player);
            int count=menu.getCarried().getCount();
            double mass=ExternalRadiation.read(menu.getCarried()).rate()*count;
            for(var one:menu.slots) {
                count+=one.getItem().getCount();
                mass+=ExternalRadiation.read(one.getItem()).rate()*one.getItem().getCount();
            }
            eq(h,count,128,"rapid count step "+i);
            eq(h,mass,expected,"rapid emission step "+i);
        }
        for(int i=0;i<1000;i++) {
            for(var one:menu.slots) com.gonzotech.radiation.ItemRadioactivity.tickInducedAt(one.getItem(),true,90000000,1,120+i*20);
            menu.clicked(random.nextInt(menu.slots.size()),random.nextInt(2),ClickType.PICKUP,player);
            int count=menu.getCarried().getCount();for(var one:menu.slots)count+=one.getItem().getCount();
            eq(h,count,128,"irradiated rapid count step "+i);
        }
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void nestedPersistenceAndPlacement(GameTestHelper h) {
        var content=net.minecraft.core.NonNullList.withSize(27,ItemStack.EMPTY);
        content.set(5,stack(4,789));
        ItemStack shulker=new ItemStack(Items.SHULKER_BOX);
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
            net.minecraft.world.item.component.ItemContainerContents.fromItems(content));
        var holder=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());holder.setItem(0,shulker);
        var saved=holder.saveWithFullMetadata(h.getLevel().registryAccess());
        var loaded=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());loaded.loadWithComponents(saved,h.getLevel().registryAccess());
        var restored=loaded.getItem(0);
        var be=new ShulkerBoxBlockEntity(null,BlockPos.ZERO,Blocks.SHULKER_BOX.defaultBlockState());
        be.applyComponentsFromItemStack(restored);
        eq(h,ExternalRadiation.read(be.getItem(5)).rate(),789,"placed shulker contents");
        var packed=new ItemStack(Items.SHULKER_BOX);packed.applyComponents(be.collectComponents());
        eq(h,ExternalRadiation.read(packed.get(net.minecraft.core.component.DataComponents.CONTAINER).getStackInSlot(5)).rate(),789,"packed shulker contents");
        h.succeed();
    }

    @GameTest(template="radiation_empty") public static void pipeShapeCacheEquivalent(GameTestHelper h) {
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
            .filter(b->b instanceof com.gonzotech.machines.network.CompositePipeBlock).findFirst().orElseThrow();
        for(var upper:net.minecraft.core.Direction.Axis.values()) for(var lower:net.minecraft.core.Direction.Axis.values()) for(int bits=0;bits<32;bits++) {
            var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,upper)
                .setValue(com.gonzotech.machines.network.CompositePipeBlock.AXIS_LOWER,lower);
            var expected=net.minecraft.world.phys.shapes.Shapes.empty();int index=0;
            for(var type:com.gonzotech.machines.network.CompositePipeBlock.BUNDLE_TYPES) {
                boolean present=(bits & (1<<index++))!=0;
                state=state.setValue(com.gonzotech.machines.network.CompositePipeBlock.PRESENT.get(type),present);
                if(present)expected=net.minecraft.world.phys.shapes.Shapes.join(expected,
                    com.gonzotech.machines.network.PipeGeometry.cornerBox(com.gonzotech.machines.network.CompositePipeBlock.axisOf(state,type),type),
                    net.minecraft.world.phys.shapes.BooleanOp.OR);
            }
            if(expected.isEmpty())expected=net.minecraft.world.phys.shapes.Shapes.block();
            h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(expected,state.getCollisionShape(h.getLevel(),BlockPos.ZERO),
                net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"pipe shape changed");
        }
        h.succeed();
    }


    @GameTest(template="radiation_empty") public static void plotnickReservationRollback(GameTestHelper h) throws Exception {
        var be = new com.gonzotech.machines.block.entity.PlotnickBlockEntity(BlockPos.ZERO,
            com.gonzotech.machines.registry.ModMachines.PLOTNICK.get().defaultBlockState());
        var reserve = be.getClass().getDeclaredMethod("takeStoredOne", Item.class);
        reserve.setAccessible(true);
        // Active, depot and mined slots: verify reservation order and identity metadata.
        int[] slots = {0, 13, 11};
        for (int i = 0; i < slots.length; i++) {
            ItemStack source = stack(1, (i + 1) * 100);
            source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("reserved-" + i));
            be.setItem(slots[i], source);
        }
        ItemStack[] reserved = new ItemStack[3];
        for (int i = 0; i < reserved.length; i++) {
            reserved[i] = (ItemStack) reserve.invoke(be, Items.DIAMOND);
            eq(h, reserved[i].getCount(), 1, "reserved count");
            eq(h, ExternalRadiation.read(reserved[i]).rate(), (i + 1) * 100, "reservation order/dose");
            h.assertTrue(reserved[i].getHoverName().getString().equals("reserved-" + i), "reservation components");
        }
        h.assertTrue(((ItemStack) reserve.invoke(be, Items.DIAMOND)).isEmpty(), "empty reservation");
        for (ItemStack refund : reserved) h.assertTrue(be.insert(refund), "rollback insertion");
        int count = 0; double emission = 0;
        for (int i = 0; i < be.getContainerSize(); i++) {
            ItemStack item = be.getItem(i);
            count += item.getCount(); emission += item.getCount() * ExternalRadiation.read(item).rate();
        }
        eq(h, count, 3, "rollback count"); eq(h, emission, 600, "rollback emission");
        h.succeed();
    }

}
