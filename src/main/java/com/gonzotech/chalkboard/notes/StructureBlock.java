package com.gonzotech.chalkboard.notes;

/**
 * Одна клетка структурной модели: позиция (x, y, z) в блоках + id предмета
 * для иконки (namespace c префиксом, как в витрине).
 *
 * <p>Оси как в MC: x → вправо (восток), y → вверх, z → «на зрителя» (юг).
 */
public record StructureBlock(int x, int y, int z, String itemId) {
}
