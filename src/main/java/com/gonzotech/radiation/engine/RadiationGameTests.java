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


    private static ItemStack sourceItem(String id, int count) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gonzotech", id)), count);
    }
    @GameTest(template="radiation_empty") public static void perItemSourceSelection(GameTestHelper h) {
        double milli = com.gonzotech.radiation.RadUnits.MILLI;
        ItemStack uranium = sourceItem("uranium_ingot",64);
        h.assertTrue(!uranium.isEmpty(), "uranium fixture registered");
        var alone = new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(uranium)));
        eq(h,alone.otherTransfer(uranium),0,"own stack cannot induce itself");
        for(int i=0;i<100;i++) com.gonzotech.radiation.ItemRadioactivity.tickInducedAt(
            uranium,true,alone.otherTransfer(uranium),1,100+i*20);
        eq(h,ExternalRadiation.read(uranium).rate(),0,"64 fresh uranium stay uninduced without outside source");
        eq(h,com.gonzotech.radiation.RadSources.emissionDeep(uranium),1.28*milli,"total preset still counts 64");
        for(String material:new String[]{"uranium_ingot","plutonium_ingot","radium_ingot"}) {
            for(int split:new int[]{1,2,31,32,33,63}) {
                ItemStack original=sourceItem(material,64),part=original.split(split);
                for(int i=0;i<100;i++) {
                    var equalSources=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(original,part)));
                    for(ItemStack item:java.util.List.of(original,part))
                        com.gonzotech.radiation.ItemRadioactivity.tickInducedAt(item,true,equalSources.otherTransfer(item),1,100+i*20);
                    eq(h,ExternalRadiation.read(original).rate(),0,"equal presets do not induce: "+material+" split "+split);
                    eq(h,ExternalRadiation.read(part).rate(),0,"equal presets do not induce other part");
                }
                eq(h,original.getCount()+part.getCount(),64,"source selection preserves count");
            }
        }
        ItemStack weakMany=sourceItem("plutonium_ingot",64),strongOne=sourceItem("radium_ingot",1),target=new ItemStack(Items.DIAMOND);
        var snapshot=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(weakMany,strongOne,target)));
        eq(h,snapshot.otherTransfer(target),75*milli,"one radium beats 64 weaker plutonium items");
        eq(h,snapshot.otherTransfer(strongOne),3.6*milli,"strongest excludes its own owner");
        for(int n:new int[]{1,16,64}) {
            ItemStack source=sourceItem("plutonium_ingot",n),receiver=new ItemStack(Items.DIAMOND,64);
            var sources=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(source,receiver)));
            eq(h,sources.otherTransfer(receiver),3.6*milli,"source count independence");
            com.gonzotech.radiation.ItemRadioactivity.tickInducedAt(receiver,true,sources.otherTransfer(receiver),1,100);
            eq(h,ExternalRadiation.read(receiver).rate(),com.gonzotech.radiation.ItemRadioactivity.nextInduced(0,
                new ItemStack(Items.DIAMOND),true,3.6*milli,1),"receiver count independence");
        }
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void sourceSnapshotTiesAndOrder(GameTestHelper h) {
        ItemStack a=stack(64,100000000),b=stack(1,100000000),target=stack(1,0);
        var snap=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(a,b,target)));
        eq(h,snap.otherTransfer(a),75000000,"equal other owner still emits");
        eq(h,snap.otherTransfer(b),75000000,"equal other owner symmetric");
        ExternalRadiation.write(a,900000000,100);
        eq(h,snap.otherTransfer(target),75000000,"source remains frozen during update");
        for(boolean reverse:new boolean[]{false,true}) {
            ItemStack hot=stack(64,100000000),cold=stack(1,2000000),fresh=stack(64,0);
            var list=reverse?java.util.List.of(fresh,cold,hot):java.util.List.of(hot,cold,fresh);
            var sources=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(list));
            for(ItemStack item:list) com.gonzotech.radiation.ItemRadioactivity.tickInducedAt(item,true,sources.otherTransfer(item),1,120);
            eq(h,ExternalRadiation.read(hot).rate(),com.gonzotech.radiation.ItemRadioactivity.nextInduced(100000000,hot,true,1500000,1),"hot independent of slot order");
            eq(h,ExternalRadiation.read(fresh).rate(),com.gonzotech.radiation.ItemRadioactivity.nextInduced(0,fresh,true,75000000,1),"fresh independent of slot order");
        }
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void nestedSourceStrength(GameTestHelper h) {
        ItemStack nested=sourceItem("plutonium_ingot",64),target=new ItemStack(Items.DIAMOND);
        ItemStack box=new ItemStack(Items.SHULKER_BOX);
        box.set(net.minecraft.core.component.DataComponents.CONTAINER,
            net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(nested)));
        var snap=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(box,target)));
        eq(h,snap.otherTransfer(box),0,"nested contents cannot induce own container");
        eq(h,snap.otherTransfer(target),3600000,"nested source uses one item, not packed sum");
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tongs=new ItemStack(com.gonzotech.core.registry.ModItems.TONGS.get());
        var container=new SimpleContainer(sourceItem("plutonium_ingot",64));
        tongs.getItem().overrideStackedOnOther(tongs,new Slot(container,0,0,0),ClickAction.PRIMARY,player);
        var shielded=new com.gonzotech.radiation.ItemSourceSnapshot(java.util.List.of(java.util.List.of(tongs,target)));
        eq(h,shielded.otherTransfer(target),2160000,"carrier shielding retained per item");
        eq(h,shielded.otherTransfer(tongs),0,"cargo cannot induce its carrier");
        h.succeed();
    }
    @GameTest(template="radiation_empty") public static void radiationPanelPixelLayout(GameTestHelper h) {
        var box=com.gonzotech.radiation.RadiationPanelLayout.place(148,9,4,26,9,320,240);
        eq(h,box.x(),0,"reference frame X");eq(h,box.y(),0,"reference frame Y");
        eq(h,box.width(),160,"reference width");eq(h,box.height(),19,"reference height");
        eq(h,box.textX(),4,"text aligned with main tooltip");eq(h,box.textY(),6,"text lowered by two GUI pixels");
        var below=com.gonzotech.radiation.RadiationPanelLayout.place(148,9,300,5,20,320,240);
        eq(h,below.x()+below.width(),320,"right screen clamp");
        eq(h,below.y(),32,"fallback below tooltip with gap");
        h.succeed();
    }

    @GameTest(template="radiation_empty") public static void clusterMultiplicityAndSnapshots(GameTestHelper h) {
        double expected=-1;
        for (int slots : new int[]{1,30}) for (int count : new int[]{1,64}) {
            ItemStack cold=stack(count,0);
            var roots=new java.util.ArrayList<ItemStack>(); roots.add(cold);
            for (int j=0;j<slots;j++) roots.add(stack(count,100000000));
            var trace=new java.util.ArrayList<RadiationCluster.Step>();
            var sum=RadiationCluster.step(roots,0,RadiationCluster.Policy.NORMAL,120,trace::add);
            eq(h,trace.get(0).otherSource(),75000000,"30 dirty items do not add their strengths");
            if(expected<0)expected=ExternalRadiation.read(cold).rate();
            eq(h,ExternalRadiation.read(cold).rate(),expected,"source and receiver count independence");
            h.assertTrue(sum.strongestItem()<=100000000,"frozen sources cannot overtake");
        }
        for(String material:new String[]{"uranium_ingot","plutonium_ingot","radium_ingot"}) {
            ItemStack original=sourceItem(material,64),part=original.split(31);
            var roots=java.util.List.of(original,part);
            for(int t=0;t<100;t++)RadiationCluster.step(roots,0,RadiationCluster.Policy.NORMAL,120+t*20,null);
            eq(h,ExternalRadiation.read(original).rate(),0,"equal presets cannot self-induce");
            eq(h,ExternalRadiation.read(part).rate(),0,"split cannot turn count into source power");
            var sum=RadiationCluster.inspect(roots);
            eq(h,sum.chunkTarget(),sum.preset(),"natural quantity retained");
        }
        ItemStack a=stack(1,2000000),b=stack(64,100000000),c=stack(1,0);
        var forward=java.util.List.of(a,b,c);
        var reverse=java.util.List.of(c.copy(),b.copy(),a.copy());
        RadiationCluster.step(forward,0,RadiationCluster.Policy.NORMAL,120,null);
        RadiationCluster.step(reverse,0,RadiationCluster.Policy.NORMAL,120,null);
        for(int n=0;n<3;n++)eq(h,ExternalRadiation.read(forward.get(n)).rate(),ExternalRadiation.read(reverse.get(2-n)).rate(),"slot order");
        h.succeed();
    }

    @GameTest(template="radiation_empty") public static void clusterNestedAndIsolation(GameTestHelper h) {
        ItemStack box=new ItemStack(Items.SHULKER_BOX);
        box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(stack(16,0),sourceItem("radium_ingot",1))));
        ItemStack bundle=new ItemStack(Items.BUNDLE);
        bundle.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,new BundleContents(java.util.List.of(stack(16,0))));
        var roots=java.util.List.of(box,bundle);
        var serialized=roots.stream().map(x->x.save(h.getLevel().registryAccess())).toList();
        var sum=RadiationCluster.step(roots,0,RadiationCluster.Policy.NORMAL,120,null);
        var nested=RadiationOwners.children(box).get(0);
        var bundled=RadiationOwners.children(bundle).get(0);
        h.assertTrue(ExternalRadiation.read(nested).rate()>0,"nested receives active induction");
        eq(h,ExternalRadiation.read(nested).rate(),ExternalRadiation.read(bundled).rate(),"bundle and shulker use same source");
        eq(h,sum.total(),com.gonzotech.radiation.RadSources.emissionDeep(box)+com.gonzotech.radiation.RadSources.emissionDeep(bundle),"no nested double count");
        for(int n=0;n<2;n++)h.assertTrue(serialized.get(n).equals(roots.get(n).save(h.getLevel().registryAccess())),"cluster writes no item data");
        var repeated=RadiationCluster.step(roots,0,RadiationCluster.Policy.NORMAL,120,null);
        eq(h,repeated.induced(),sum.induced(),"same tick not processed twice");
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack clean=stack(1,0); player.getInventory().setItem(0,clean);
        var chest=new SimpleContainer(sourceItem("radium_ingot",64));
        var menu=ChestMenu.threeRows(1,player.getInventory(),new SimpleContainer(27));
        menu.setCarried(stack(64,100000000));
        RadiationCluster.step(RadiationOwners.slots(chest),0,RadiationCluster.Policy.NORMAL,120,null);
        RadiationCluster.step(java.util.List.of(clean),0,RadiationCluster.Policy.NORMAL,120,null);
        eq(h,ExternalRadiation.read(clean).rate(),0,"menu/cursor/chest not merged with player");
        ItemStack tongs=new ItemStack(com.gonzotech.core.registry.ModItems.TONGS.get());
        var cargo=new SimpleContainer(sourceItem("plutonium_ingot",64));
        tongs.getItem().overrideStackedOnOther(tongs,new Slot(cargo,0,0,0),ClickAction.PRIMARY,player);
        ItemStack packed=tongs;
        for(int depth=0;depth<4;depth++) {
            ItemStack wrapper=new ItemStack(Items.SHULKER_BOX);
            wrapper.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(packed)));
            packed=wrapper;
        }
        var boundary=RadiationCluster.inspect(java.util.List.of(packed));
        eq(h,boundary.total(),com.gonzotech.radiation.RadSources.emissionDeep(packed),"carrier cargo still emits at depth four");
        eq(h,boundary.strongestItem(),2160000,"deep carrier shielding stays per item");
        h.succeed();
    }

    @GameTest(template="radiation_empty") public static void clusterLeadAndIncomingChunk(GameTestHelper h) {
        ItemStack normal=stack(64,5000000),lead=normal.copy();
        var normalRoots=java.util.List.of(normal,sourceItem("radium_ingot",64));
        var leadRoots=java.util.List.of(lead,sourceItem("radium_ingot",64));
        var trace=new java.util.ArrayList<RadiationCluster.Step>();
        RadiationCluster.step(normalRoots,0,RadiationCluster.Policy.NORMAL,120,null);
        RadiationCluster.step(leadRoots,0,RadiationCluster.Policy.LEAD,120,trace::add);
        eq(h,trace.get(0).otherSource(),15000000,"lead internal strength times 0.2");
        h.assertTrue(ExternalRadiation.read(lead).rate()<ExternalRadiation.read(normal).rate(),"lead slows internal induction");
        normal=stack(64,0);lead=normal.copy();
        RadiationCluster.step(java.util.List.of(normal),40000000,RadiationCluster.Policy.NORMAL,120,null);
        RadiationCluster.step(java.util.List.of(lead),40000000,RadiationCluster.Policy.LEAD,120,null);
        eq(h,ExternalRadiation.read(normal).rate(),ExternalRadiation.read(lead).rate(),"incoming chunk unaffected by outward/internal shielding");
        ItemStack hot=stack(64,100000000);
        RadiationCluster.step(java.util.List.of(hot),40000000,RadiationCluster.Policy.NORMAL,120,null);
        h.assertTrue(ExternalRadiation.read(hot).rate()<100000000,"colder chunk cannot heat hotter item");
        h.succeed();
    }

    @GameTest(template="radiation_empty",timeoutTicks=100) public static void clusterClosedFeedback(GameTestHelper h) {
        for(int slots:new int[]{1,41}) for(int count:new int[]{1,64}) {
            var roots=new java.util.ArrayList<ItemStack>();
            for(int j=0;j<slots;j++)roots.add(stack(count,0));
            double chunk=31000000;
            for(int t=0;t<5000;t++) {
                var sum=RadiationCluster.step(roots,chunk,RadiationCluster.Policy.NORMAL,120+t*20,null);
                chunk+=Math.max(0,sum.chunkTarget()-chunk)*0.01*0.95;
                if(t%20==19)chunk*=0.99;
                h.assertTrue(chunk<=31000000,"closed feedback amplified the chunk");
                h.assertTrue(sum.strongestItem()<=31000000,"closed feedback amplified an item");
            }
            eq(h,RadiationCluster.inspect(roots).induced(),0,"after chunk cools, induced radiation extinguishes");
            eq(h,RadiationCluster.inspect(roots).count(),(long)slots*count,"feedback never creates/deletes items");
        }
        eq(h,com.gonzotech.radiation.ItemRadioactivity.UPTAKE_COST,0.0013,"author uptake coefficient");
        h.succeed();
    }

    @GameTest(template="radiation_empty") public static void radiationPanelUnknownHidden(GameTestHelper h) {
        for(Double value:new Double[]{null,0.0,0.5,Double.NaN,Double.POSITIVE_INFINITY})
            h.assertTrue(!com.gonzotech.radiation.RadiationPanelLayout.visible(value),"unknown/clean rates must not flash a placeholder");
        h.assertTrue(com.gonzotech.radiation.RadiationPanelLayout.visible(1000000.0),"known emission visible");
        h.succeed();
    }

    @GameTest(template="radiation_empty",timeoutTicks=100) public static void clusterScheduledWorldOwners(GameTestHelper h) {
        BlockPos pos=new BlockPos(1,2,1);
        h.setBlock(pos,Blocks.CHEST);
        var chest=(ChestBlockEntity)h.getBlockEntity(pos);
        chest.setItem(0,sourceItem("radium_ingot",1));chest.setItem(1,stack(8,0));
        var stand=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),h.absolutePos(new BlockPos(3,2,1)).getX()+0.5,
                h.absolutePos(new BlockPos(3,2,1)).getY(),h.absolutePos(new BlockPos(3,2,1)).getZ()+0.5);
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,sourceItem("radium_ingot",1));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,stack(8,0));
        h.getLevel().addFreshEntity(stand);
        h.runAfterDelay(45,()->{
            h.assertTrue(ExternalRadiation.read(chest.getItem(1)).rate()>0,"closed chest ticks without a player menu");
            h.assertTrue(ExternalRadiation.read(stand.getOffhandItem()).rate()>0,"armor stand uses common engine");
            eq(h,chest.getItem(1).getCount(),8,"chest count unchanged");
            eq(h,stand.getOffhandItem().getCount(),8,"equipment count unchanged");
            stand.discard();h.succeed();
        });
    }

    @GameTest(template="radiation_empty",timeoutTicks=100) public static void clusterDoubleChest(GameTestHelper h) {
        var leftState=Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var rightState=leftState.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT);
        BlockPos leftPos=new BlockPos(1,2,1);
        BlockPos rightPos=leftPos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(leftState));
        h.setBlock(leftPos,Blocks.CHEST);h.setBlock(rightPos,Blocks.CHEST);
        h.getLevel().setBlock(h.absolutePos(leftPos),leftState,2);
        h.getLevel().setBlock(h.absolutePos(rightPos),rightState,2);
        var left=(ChestBlockEntity)h.getBlockEntity(leftPos);
        var right=(ChestBlockEntity)h.getBlockEntity(rightPos);
        left.setItem(0,sourceItem("radium_ingot",1));right.setItem(0,stack(4,0));
        var a=com.gonzotech.radiation.RadiationSystem.chestOwners(h.getLevel(),left);
        var b=com.gonzotech.radiation.RadiationSystem.chestOwners(h.getLevel(),right);
        eq(h,a.size(),2,"double chest includes both halves");
        h.assertTrue(a.equals(b),"both halves resolve the same canonical owner");
        h.runAfterDelay(45,()->{
            h.assertTrue(ExternalRadiation.read(right.getItem(0)).rate()>0,"source crosses double chest half boundary");
            eq(h,right.getItem(0).getCount(),4,"double chest preserves count");
            var tag=right.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored=new ChestBlockEntity(right.getBlockPos(),right.getBlockState());
            restored.loadWithComponents(tag,h.getLevel().registryAccess());
            eq(h,ExternalRadiation.read(restored.getItem(0)).rate(),ExternalRadiation.read(right.getItem(0)).rate(),"external rate survives owner save/load after active processing");
            h.succeed();
        });
    }

    @GameTest(template="radiation_empty") public static void independentNestedCopies(GameTestHelper h) {
        ItemStack box=new ItemStack(Items.SHULKER_BOX);
        box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                com.gonzotech.mixin.ContainerContentsConstructorInvoker.gonzo$create(java.util.List.of(stack(16,2000000),ItemStack.EMPTY)));
        var mutable=new BundleContents.Mutable(new BundleContents(java.util.List.of(stack(16,3000000))));
        mutable.toggleSelectedItem(0);
        ItemStack bundle=new ItemStack(Items.BUNDLE);
        bundle.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,mutable.toImmutable());
        for(ItemStack original:java.util.List.of(box,bundle)) {
            ItemStack copied=original.copy();
            h.assertTrue(ItemStack.matches(original,copied),"copy preserves all item/component values");
            h.assertTrue(original.save(h.getLevel().registryAccess()).equals(copied.save(h.getLevel().registryAccess())),"copy preserves serialized item data");
            var a=RadiationOwners.children(original);var b=RadiationOwners.children(copied);
            eq(h,b.size(),a.size(),"trailing empty slot indices preserved");
            h.assertTrue(a.get(0)!=b.get(0),"independent owners must not share nested stack objects");
            double previous=ExternalRadiation.read(b.get(0)).rate();
            ExternalRadiation.rate(a.get(0),100000000);
            eq(h,ExternalRadiation.read(b.get(0)).rate(),previous,"changing original cannot irradiate its copy remotely");
            eq(h,RadiationCluster.inspect(java.util.List.of(original,copied)).total(),
                    com.gonzotech.radiation.RadSources.emissionDeep(original)+com.gonzotech.radiation.RadSources.emissionDeep(copied),"no lost emission from shared component deduplication");
            RadiationCluster.step(java.util.List.of(original),0,RadiationCluster.Policy.NORMAL,120,null);
            eq(h,ExternalRadiation.read(b.get(0)).rate(),previous,"processing one physical owner leaves another unchanged");
        }
        eq(h,bundle.copy().get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).getSelectedItem(),0,"bundle selection preserved");
        h.succeed();
    }

}
