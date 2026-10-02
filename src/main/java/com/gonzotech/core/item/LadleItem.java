package com.gonzotech.core.item;

import com.gonzotech.radiation.CarrierItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ковш (0.3.85, EPOCH3-BASE §2.8): брат щипцов для ЖИДКОСТЕЙ и ртути/цезия.
 * Кнопки — как у щипцов (автор 03.10.2026): ЛКМ — ПОЛОЖИТЬ (предметы ртути/
 * цезия, ванильные контейнеры или зачерпнуть из заполненного ведра), ПКМ —
 * ДОСТАТЬ (один предмет в курсор/слот или отлить порцию в пустое ведро).
 *
 * <p>Запись NBT — только через {@code stack.update} (merge; грубый {@code set}
 * ломал строку креатив-таба), при смене типа содержимого чужие ключи чистятся.
 * Доза: −40 % рад / −80 % токс на содержимое; строку «Радиоактивность: …»
 * рисует общий RadTooltip по {@code RadSources.emissionDeep}.
 */
public final class LadleItem extends Item implements CarrierItem {

    private static final String TAG_ITEM = "carrier_item";
    private static final String TAG_COUNT = "carrier_count";
    private static final String TAG_FLUID = "carrier_fluid";
    private static final String TAG_MB = "carrier_mb";
    private static final String BUCKET_SUFFIX = "_bucket";
    private static final String BUCKET_ITEM = "bucket";

    public LadleItem(Properties properties) {
        super(properties);
    }

    // ─────────────────────────── NBT ───────────────────────────

    private static String itemId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(TAG_ITEM);
    }

    private static int itemCount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(TAG_COUNT);
    }

    private static String fluidId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(TAG_FLUID);
    }

    private static int fluidMb(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(TAG_MB);
    }

    /** Merge-запись предметов; жидкостные ключи чистятся. */
    private static void storeItems(ItemStack stack, String id, int count) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> {
                    tag.remove(TAG_FLUID);
                    tag.remove(TAG_MB);
                    tag.putString(TAG_ITEM, id);
                    tag.putInt(TAG_COUNT, count);
                }));
    }

    /** Merge-запись жидкости; предметные ключи чистятся. */
    private static void storeFluid(ItemStack stack, String fluid, int mb) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> {
                    tag.remove(TAG_ITEM);
                    tag.remove(TAG_COUNT);
                    tag.putString(TAG_FLUID, fluid);
                    tag.putInt(TAG_MB, mb);
                }));
    }

    private static ItemStack previewOf(String id, int count) {
        if (id == null || id.isEmpty() || count <= 0) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, count);
    }

    /** id флюида из предмета заполненного ведра: «water_bucket» → «minecraft:water». */
    private static String fluidOfBucket(ItemStack bucket) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(bucket.getItem());
        if (!key.getPath().endsWith(BUCKET_SUFFIX)) return "";
        return key.getNamespace() + ":"
                + key.getPath().substring(0, key.getPath().length() - BUCKET_SUFFIX.length());
    }

    /** Обратное: предмет ведра для флюида (соглашение «путь_bucket»), null — нет. */
    private static Item bucketOf(String fluidId) {
        if (fluidId == null || !fluidId.contains(":")) return null;
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(fluidId + BUCKET_SUFFIX));
        return (item == null || item == Items.AIR) ? null : item;
    }

    private static void playInsert(Player player) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private static void playRemoveOne(Player player) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_REMOVE_ONE,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private static void playScoop(Player player) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(),
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    // ─────────────────────────── CarrierItem ───────────────────────────

    @Override
    public boolean hasCarried(ItemStack carrier) {
        return itemCount(carrier) > 0;
    }

    @Override
    public ItemStack previewCarried(ItemStack carrier) {
        return previewOf(itemId(carrier), itemCount(carrier));
    }

    @Override
    public ItemStack takeCarried(ItemStack carrier) {
        ItemStack content = previewCarried(carrier);
        storeItems(carrier, "", 0);
        return content;
    }

    // ─────────────────────── ковш В РУКЕ, клик по слоту ───────────────────────

    /** ЛКМ: предметы ртути/цезия или зачерпнуть из заполненного ведра в слоте. */
    @Override
    public boolean overrideStackedOnOther(ItemStack magazine, Slot slot,
                                          ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || !magazine.is(this)) return false;
        ItemStack other = slot.getItem();
        if (other.isEmpty()) return false;

        // ПКМ-эквивалент отменён: на ЛКМ только вставка/зачерпывание.
        String id = BuiltInRegistries.ITEM.getKey(other.getItem()).toString();
        int moved = LadleLogic.roomForItem(itemId(magazine), itemCount(magazine),
                id, other.getCount());
        if (moved > 0) {
            storeItems(magazine, id, itemCount(magazine) + moved);
            other.shrink(moved);
            slot.setChanged();
            playInsert(player);
            return true;
        }

        // Зачерпнуть из заполненного ведра (порция 1000 mB).
        if (fluidMb(magazine) <= 0 && fluidId(magazine).isEmpty() && itemCount(magazine) == 0) {
            String fluid = fluidOfBucket(other);
            if (!fluid.isEmpty()) {
                storeFluid(magazine, fluid, LadleLogic.CAPACITY_MB);
                slot.set(new ItemStack(Items.BUCKET));
                playScoop(player);
                return true;
            }
        }
        return false;
    }

    // ─────────────────────── предмет/ведро В РУКЕ, клик по ковшу ───────────────────────

    /** ЛКМ: положить ртуть/цезий или зачерпнуть пустым ведром; ПКМ: достать/отлить. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack magazine, ItemStack incoming,
                                            Slot slot, ClickAction action, Player player,
                                            net.minecraft.world.entity.SlotAccess cursor) {
        if (!magazine.is(this)) return false;
        String id = incoming.isEmpty()
                ? "" : BuiltInRegistries.ITEM.getKey(incoming.getItem()).toString();

        if (action == ClickAction.PRIMARY) {
            // ЛКМ предметом: положить ртуть/цезий/ванильный контейнер.
            if (!incoming.isEmpty()) {
                int moved = LadleLogic.roomForItem(itemId(magazine), itemCount(magazine),
                        id, incoming.getCount());
                if (moved > 0) {
                    storeItems(magazine, id, itemCount(magazine) + moved);
                    incoming.shrink(moved);
                    playInsert(player);
                    return true;
                }
                // ЛКМ ОДИНОЧНЫМ пустым ведром: зачерпнуть порцию из ковша
                // (курсор заменяется заполненным ведром; стопку вёдер не тратим).
                if (id.equals(BUCKET_ITEM) && incoming.getCount() == 1
                        && fluidMb(magazine) >= LadleLogic.CAPACITY_MB
                        && !fluidId(magazine).isEmpty()) {
                    Item filled = bucketOf(fluidId(magazine));
                    if (filled != null) {
                        cursor.set(new ItemStack(filled));
                        storeFluid(magazine, fluidId(magazine), fluidMb(magazine) - LadleLogic.CAPACITY_MB);
                        playScoop(player);
                        return true;
                    }
                }
            }
            return false;
        }

        // ПКМ: достать один предмет или отлить порцию в пустое ведро.
        if (incoming.isEmpty()) {
            ItemStack content = previewCarried(magazine);
            if (!content.isEmpty()) {
                cursor.set(content.split(1));
                storeItems(magazine, itemId(magazine), itemCount(magazine) - 1);
                playRemoveOne(player);
                return true;
            }
            // Пустой курсор: отлить порцию в новое заполненное ведро.
            if (fluidMb(magazine) >= LadleLogic.CAPACITY_MB && !fluidId(magazine).isEmpty()) {
                Item filled = bucketOf(fluidId(magazine));
                if (filled != null) {
                    cursor.set(new ItemStack(filled));
                    storeFluid(magazine, fluidId(magazine), fluidMb(magazine) - LadleLogic.CAPACITY_MB);
                    playScoop(player);
                    return true;
                }
            }
            return false;
        }

        // ПКМ стопкой того же вида: достать ещё один в стопку.
        ItemStack content = previewCarried(magazine);
        if (!content.isEmpty() && ItemStack.isSameItemSameComponents(incoming, content)
                && incoming.getCount() < incoming.getMaxStackSize()) {
            incoming.grow(1);
            storeItems(magazine, itemId(magazine), itemCount(magazine) - 1);
            playRemoveOne(player);
            return true;
        }
        // ПКМ одиночным пустым ведром в курсоре: отлить порцию в него.
        if (id.equals(BUCKET_ITEM) && incoming.getCount() == 1
                && fluidMb(magazine) >= LadleLogic.CAPACITY_MB
                && !fluidId(magazine).isEmpty()) {
            Item filled = bucketOf(fluidId(magazine));
            if (filled != null) {
                cursor.set(new ItemStack(filled));
                storeFluid(magazine, fluidId(magazine), fluidMb(magazine) - LadleLogic.CAPACITY_MB);
                playScoop(player);
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────── выгрузка вручную ───────────────────────────

    /** ПКМ по воздуху — мешочек: ртуть/цезий в инвентарь; жидкость не выливается. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack magazine = player.getItemInHand(hand);
        if (!hasCarried(magazine)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack content = takeCarried(magazine);
            if (!content.isEmpty() && !player.getInventory().add(content)) {
                net.minecraft.world.Containers.dropItemStack(level,
                        player.getX(), player.getY() + 0.5, player.getZ(), content);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ─────────────────────────── тултип ───────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        if (fluidMb(stack) > 0 && !fluidId(stack).isEmpty()) {
            String path = fluidId(stack).contains(":")
                    ? fluidId(stack).substring(fluidId(stack).indexOf(':') + 1) : fluidId(stack);
            Component name = Component.translatable("resource.gonzotech." + path);
            tooltip.add(Component.translatable("tooltip.gonzotech.carrier.fluid",
                    name, Component.literal(fluidMb(stack) + " mB")
                            .withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        } else {
            ItemStack content = previewCarried(stack);
            if (content.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.gonzotech.carrier.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("tooltip.gonzotech.carrier.empty")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.gonzotech.carrier.contains",
                        content.getHoverName(),
                        Component.literal(Integer.toString(content.getCount()))
                                .withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
                if (itemCount(stack) >= LadleLogic.ITEM_CAPACITY) {
                    tooltip.add(Component.translatable("tooltip.gonzotech.carrier.full")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.gonzotech.shielding",
                Component.literal("40%").withColor(0xFFFFFF)).withStyle(ChatFormatting.GRAY));
    }
}
