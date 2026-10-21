package com.gonzotech.machines.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Карта памяти автошахтера (0.3.257). Пустая — просто предмет; после
 * сейсмического импульса Плотник записывает в неё сектор 3×3 чанка
 * (NBT {@code MinerSector}: границы чанковых координат) и строку описания
 * «Имеет информацию о секторе [x,z]x[x,z]». Заполненная карта — условие
 * рудного режима кнопок «Разведка» и «Построить связи».
 */
public final class MemoryCardItem extends Item {

    /** Границы сектора в координатах чанковой сетки: включительно. */
    public record Sector(int minX, int maxX, int minZ, int maxZ) {
        public boolean contains(BlockPos pos) {
            int x = pos.getX() >> 4;
            int z = pos.getZ() >> 4;
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static final String TAG = "MinerSector";

    public MemoryCardItem(Properties properties) {
        super(properties);
    }

    public static boolean isFilled(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().contains(TAG);
    }

    /** @return границы сектора или null, если карта пуста/чужая. */
    public static Sector readSector(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG)) return null;
        int[] bounds = tag.getIntArray(TAG);
        if (bounds.length < 4) return null;
        return new Sector(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    /**
     * Записать сектор и описание. Угол форматируется как у автора:
     * «[дальний X,дальний Z]x[ближний X,ближний Z]» — реальные координаты
     * чанковой сетки.
     */
    public static void writeSector(ItemStack stack, Sector sector) {
        int[] bounds = new int[] {
            sector.minX(), sector.maxX(), sector.minZ(), sector.maxZ()
        };
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
            data -> data.update(tag -> tag.putIntArray(TAG, bounds)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
            "item.gonzotech.memory_card.sector",
            sector.maxX(), sector.maxZ(), sector.minX(), sector.minZ()))));
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.LORE);
    }
}
