package com.gonzotech.machines.item;

import net.minecraft.world.item.Item;

/**
 * Гаечный ключ Gonzo Tech. Инструмент-модификатор: ПКМ по трубе энергосети
 * переключает её режим (AUTO → PULL → PUSH), см. {@code PipeBlock#useItemOn}.
 * <p>
 * Пока не имеет собственной логики — служит «маркером»: блоки сами проверяют
 * {@code stack.getItem() instanceof WrenchItem}. Позже сюда можно добавить
 * поворот/демонтаж машин и настройку узлов с фильтрами.
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }
}
