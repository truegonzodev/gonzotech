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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Щипцы (0.3.79, EPOCH3-BASE §2.8): предмет-контейнер на 64 единицы ОДНОГО
 * вида. Перетаскивание в обе стороны («слитки на щипцы или щипцы на слитки —
 * неважно»); ртуть/цезий «чистого» вида НЕ берут ({@link TongsLogic#canPick}).
 *
 * <p>Содержимое «фонит» меньше для держателя: −40 % радиоактивности, −80 %
 * токсичности ({@link CarrierItem}) — тултипы и суммы доз снижаются
 * автоматически через {@code RadSources.emissionOfStack}/{@code ItemToxicity}.
 * Станок, в чей слот положили щипцы, забирает содержимое, а сами щипцы
 * падают на землю у блока ({@code BaseMachineBlockEntity.setItem}).
 */
public final class TongsItem extends Item implements CarrierItem {

    private static final String TAG_ITEM = "carrier_item";
    private static final String TAG_COUNT = "carrier_count";

    public TongsItem(Properties properties) {
        super(properties);
    }

    // ─────────────────────────── NBT ───────────────────────────

    private static CompoundTag tag(ItemStack stack) {
        CompoundTag data = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        return data;
    }

    private static void store(ItemStack stack, String itemId, int count) {
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

    private static ItemStack previewOf(String id, int count) {
        if (id == null || id.isEmpty() || count <= 0) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, count);
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

    // ─────────────────────────── перетаскивание ───────────────────────────

    /** Держат щипцы и кликают по стопке в слоте: забрать из слота в щипцы. */
    @Override
    public boolean overrideStackedOnOther(ItemStack magazine, Slot slot,
                                              ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || !magazine.is(this)) return false;
        ItemStack other = slot.getItem();
        if (other.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(other.getItem()).getPath();
        int moved = TongsLogic.roomFor(itemId(magazine), itemCount(magazine),
                path, other.getCount());
        if (moved <= 0) return false;
        store(magazine, path, itemCount(magazine) + moved);
        other.shrink(moved);
        slot.setChanged();
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return true;
    }

    /** Держат стопку и кликают по щипцам в слоте: положить стопку в щипцы. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack magazine, ItemStack incoming,
                                                Slot slot, ClickAction action, Player player,
                                                net.minecraft.world.inventory.SlotAccess cursor) {
        if (action != ClickAction.PRIMARY || !magazine.is(this) || incoming.isEmpty()) {
            return false;
        }
        String path = BuiltInRegistries.ITEM.getKey(incoming.getItem()).getPath();
        int moved = TongsLogic.roomFor(itemId(magazine), itemCount(magazine),
                path, incoming.getCount());
        if (moved <= 0) return false;
        store(magazine, path, itemCount(magazine) + moved);
        incoming.shrink(moved);
        slot.setChanged();
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
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
        ItemStack content = previewCarried(stack);
        if (content.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.gonzotech.carrier.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.gonzotech.carrier.contains",
                    Component.literal(Integer.toString(content.getCount()))
                            .withStyle(ChatFormatting.YELLOW),
                    content.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
