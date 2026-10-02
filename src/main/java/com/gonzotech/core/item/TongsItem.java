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
 * Щипцы (0.3.85, EPOCH3-BASE §2.8): предмет-контейнер на 64 единицы ОДНОГО
 * вида, ванильное поведение мешочка. Кнопки (автор 03.10.2026): ЛКМ —
 * ПОЛОЖИТЬ стопку в щипцы (сколько влезает, в обе ориентации), ПКМ —
 * ДОСТАТЬ один предмет (в слот/в курсор). В слоте станка щипцы заменяются
 * содержимым, сами падают у блока ({@code BaseMachineBlockEntity.setItem}).
 *
 * <p>Дозу содержимого носитель срезает: −40 % радиоактивности, −80 %
 * токсичности ({@link CarrierItem}); строку «Радиоактивность: …» рисует общий
 * RadTooltip по {@code RadSources.emissionDeep} (значение уже срезанное).
 *
 * <p>Фиксы 0.3.83–0.3.85: в NBT хранится ПОЛНЫЙ id «namespace:path»;
 * запись NBT — только через {@code stack.update} (merge, как у наведённой
 * радиации — грубый {@code set} ломал строку креатив-таба в тултипе).
 */
public final class TongsItem extends Item implements CarrierItem {

    private static final String TAG_ITEM = "carrier_item";
    private static final String TAG_COUNT = "carrier_count";

    public TongsItem(Properties properties) {
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

    /** Запись через update-merge (фикс 0.3.85: set ломал строку креатив-таба). */
    private static void store(ItemStack stack, String id, int count) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> {
                    tag.putString(TAG_ITEM, id);
                    tag.putInt(TAG_COUNT, count);
                }));
    }

    private static ItemStack previewOf(String id, int count) {
        if (id == null || id.isEmpty() || count <= 0) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, count);
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
        store(carrier, "", 0);
        return content;
    }

    // ─────────────────────── щипцы В РУКЕ, клик по слоту ───────────────────────

    /** ЛКМ: перелить стопку из слота в щипцы (сколько влезает). */
    @Override
    public boolean overrideStackedOnOther(ItemStack magazine, Slot slot,
                                          ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || !magazine.is(this)) return false;
        ItemStack other = slot.getItem();
        if (other.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(other.getItem()).toString();
        int moved = TongsLogic.roomFor(itemId(magazine), itemCount(magazine),
                id, other.getCount());
        if (moved <= 0) return false;
        store(magazine, id, itemCount(magazine) + moved);
        other.shrink(moved);
        slot.setChanged();
        playInsert(player);
        return true;
    }

    // ─────────────────────── стопка В РУКЕ, клик по щипцам ───────────────────────

    /** ЛКМ: положить стопку из курсора в щипцы; ПКМ: достать один в курсор. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack magazine, ItemStack incoming,
                                            Slot slot, ClickAction action, Player player,
                                            net.minecraft.world.entity.SlotAccess cursor) {
        if (!magazine.is(this)) return false;
        if (action == ClickAction.PRIMARY) {
            if (incoming.isEmpty()) return false;
            String id = BuiltInRegistries.ITEM.getKey(incoming.getItem()).toString();
            int moved = TongsLogic.roomFor(itemId(magazine), itemCount(magazine),
                    id, incoming.getCount());
            if (moved <= 0) return false;
            store(magazine, id, itemCount(magazine) + moved);
            incoming.shrink(moved);
            playInsert(player);
            return true;
        }
        // ПКМ: достать один предмет в курсор.
        ItemStack content = previewCarried(magazine);
        if (content.isEmpty()) return false;
        if (incoming.isEmpty()) {
            cursor.set(content.split(1));
        } else if (ItemStack.isSameItemSameComponents(incoming, content)
                && incoming.getCount() < incoming.getMaxStackSize()) {
            incoming.grow(1);
            content.shrink(1);
        } else {
            return false;
        }
        store(magazine, itemId(magazine), itemCount(magazine) - 1);
        playRemoveOne(player);
        return true;
    }

    // ─────────────────────────── выгрузка вручную ───────────────────────────

    /** ПКМ по воздуху — мешочек: содержимое возвращается в инвентарь (излишек — дроп). */
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
        // Lore-формат по скринам автора (03.10.2026): после строки креатив-таба —
        // пустая строка, статус занятости, пустая строка, экранирование; строки
        // «Радиоактивность/Токсичность» добавляет общий RadTooltip (со своим
        // отступом) по уже срезанным значениям.
        tooltip.add(Component.empty());
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
            if (itemCount(stack) >= TongsLogic.CAPACITY) {
                tooltip.add(Component.translatable("tooltip.gonzotech.carrier.full")
                        .withStyle(ChatFormatting.RED));
            }
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.gonzotech.shielding",
                Component.literal("40%").withColor(0xFFFFFF)).withStyle(ChatFormatting.GRAY));
    }
}
