package com.gonzotech.core.item;

import com.gonzotech.radiation.CarrierItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
 * Ковш (0.3.79, EPOCH3-BASE §2.8): брат щипцов для ЖИДКОСТЕЙ и для ртути/цезия
 * (те предметы, которые щипцы НЕ берут). Хранит порцию 1000 mB одного флюида
 * ИЛИ до 64 единиц ртути/цезия ({@link LadleLogic#canPickItem}).
 *
 * <p>Жидкость: держа ковш, клик по заполненному ведру — зачерпнуть (ведро
 * пустеет), клик по пустому ведру — отлить обратно в ведро. Предметное
 * содержимое и защитные множители — как у щипцов ({@code CarrierItem}).
 * Радиоактивность жидкостей появится вместе с параметром воды (шаг 2а).
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

    private static CompoundTag tag(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
    }

    private static void storeItems(ItemStack stack, String itemId, int count) {
        CompoundTag data = tag(stack);
        data.putString(TAG_ITEM, itemId);
        data.putInt(TAG_COUNT, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    private static String itemId(ItemStack stack) {
        return tag(stack).getString(TAG_ITEM);
    }

    private static int itemCount(ItemStack stack) {
        return tag(stack).getInt(TAG_COUNT);
    }

    private static String fluidId(ItemStack stack) {
        return tag(stack).getString(TAG_FLUID);
    }

    private static int fluidMb(ItemStack stack) {
        return tag(stack).getInt(TAG_MB);
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
        String ns = key.getNamespace();
        String fluidPath = key.getPath().substring(0, key.getPath().length() - BUCKET_SUFFIX.length());
        return ns + ":" + fluidPath;
    }

    /** Обратное: предмет ведра для флюида (соглашение «путь_bucket»), null — нет. */
    private static Item bucketOf(String fluidId) {
        if (fluidId == null || !fluidId.contains(":")) return null;
        ResourceLocation rl = ResourceLocation.parse(fluidId + BUCKET_SUFFIX);
        Item item = BuiltInRegistries.ITEM.getValue(rl);
        return (item == null || item == Items.AIR) ? null : item;
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

    // ─────────────────────────── перетаскивание ───────────────────────────

    /** Держат ковш и кликают по слоту: ведро → зачерпнуть; пустое ведро → отлить. */
    @Override
    public boolean overrideStackedOnOther(ItemStack magazine, Slot slot,
                                              ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || !magazine.is(this)) return false;
        ItemStack other = slot.getItem();
        if (other.isEmpty()) return false;

        // Ртуть/цезий предметами — профиль ковша. ПОЛНЫЙ id (0.3.83).
        String id = BuiltInRegistries.ITEM.getKey(other.getItem()).toString();
        int moved = LadleLogic.roomForItem(itemId(magazine), itemCount(magazine),
                id, other.getCount());
        if (moved > 0) {
            storeItems(magazine, id, itemCount(magazine) + moved);
            other.shrink(moved);
            slot.setChanged();
            return true;
        }

        // Заполненное ведро → зачерпнуть порцию.
        if (fluidMb(magazine) <= 0 || fluidId(magazine).isEmpty()) {
            String fluid = fluidOfBucket(other);
            if (!fluid.isEmpty() && itemCount(magazine) == 0) {
                CompoundTag data = tag(magazine);
                data.putString(TAG_FLUID, fluid);
                data.putInt(TAG_MB, LadleLogic.CAPACITY_MB);
                magazine.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                slot.set(new ItemStack(Items.BUCKET));
                return true;
            }

            // Пустое ведро → отлить порцию из ковша (обратное направление).
            if (path.equals(BUCKET_ITEM) && fluidMb(magazine) >= LadleLogic.CAPACITY_MB
                    && !fluidId(magazine).isEmpty()) {
                Item filled = bucketOf(fluidId(magazine));
                if (filled != null) {
                    slot.set(new ItemStack(filled));
                    CompoundTag data = tag(magazine);
                    data.putInt(TAG_MB, fluidMb(magazine) - LadleLogic.CAPACITY_MB);
                    magazine.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                    return true;
                }
            }
        }
        return false;
    }

    /** Держат стопку и кликают по ковшу в слоте: предметы ртути/цезия — внутрь. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack magazine, ItemStack incoming,
                                                Slot slot, ClickAction action, Player player,
                                                net.minecraft.world.entity.SlotAccess cursor) {
        if (action != ClickAction.PRIMARY || !magazine.is(this) || incoming.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(incoming.getItem()).toString();
        int moved = LadleLogic.roomForItem(itemId(magazine), itemCount(magazine),
                id, incoming.getCount());
        if (moved > 0) {
            storeItems(magazine, id, itemCount(magazine) + moved);
            incoming.shrink(moved);
            slot.setChanged();
            return true;
        }
        // Пустое ведро курсором → отлить порцию из ковша.
        if (path.equals(BUCKET_ITEM) && fluidMb(magazine) >= LadleLogic.CAPACITY_MB
                && !fluidId(magazine).isEmpty()) {
            Item filled = bucketOf(fluidId(magazine));
            if (filled != null) {
                incoming.shrink(1);
                slot.set(new ItemStack(filled));
                CompoundTag data = tag(magazine);
                data.putInt(TAG_MB, fluidMb(magazine) - LadleLogic.CAPACITY_MB);
                magazine.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
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
        // Экранирующее свойство — как у щипцов (формат хазмата); строку
        // «Радиоактивность: …» добавляет общий RadTooltip (значение уменьшенное).
        tooltip.add(Component.translatable("tooltip.gonzotech.shielding",
                Component.literal("40%").withColor(0xFFFFFF)).withStyle(ChatFormatting.GRAY));
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
    }
}
