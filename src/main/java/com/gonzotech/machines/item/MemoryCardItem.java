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
    /** 9 бит: какие чанки сектора реально простучаны источниками (0.3.261). */
    private static final String MASK_TAG = "MinerSourceMask";

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
        writeSector(stack, sector, 0);
    }

    /**
     * То же + маска простучанных чанков (бит (dx+1)*3+(dz+1) от центра
     * сектора, dx/dz ∈ [-1;1]). 0 — «весь сектор» (наследный формат).
     * Разметка руд затем сканирует только помеченные чанки: один источник —
     * один чанк информации, а не девять.
     */
    public static void writeSector(ItemStack stack, Sector sector, int sourceMask) {
        int[] bounds = new int[] {
            sector.minX(), sector.maxX(), sector.minZ(), sector.maxZ()
        };
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
            data -> data.update(tag -> {
                tag.putIntArray(TAG, bounds);
                if (sourceMask != 0) tag.putInt(MASK_TAG, sourceMask);
                else tag.remove(MASK_TAG);
            }));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
            "item.gonzotech.memory_card.sector",
            sector.maxX(), sector.maxZ(), sector.minX(), sector.minZ()))));
    }

    /** Битовая маска чанков с источниками; 0 — все 9 чанки (старая карта). */
    public static int readSourceMask(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getInt(MASK_TAG);
    }

    public static void clear(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.LORE);
    }
}
