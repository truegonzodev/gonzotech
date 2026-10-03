package net.minecraft.nbt;
/** Стаб компилятора: NBT-тег (интерфейс, как в ванили). */
public interface Tag {
    int TAG_END = 0;
    int TAG_BYTE = 1;
    int TAG_SHORT = 2;
    int TAG_INT = 3;
    int TAG_LONG = 4;
    int TAG_FLOAT = 5;
    int TAG_DOUBLE = 6;
    int TAG_BYTE_ARRAY = 7;
    int TAG_STRING = 8;
    int TAG_LIST = 9;
    int TAG_COMPOUND = 10;
    int TAG_INT_ARRAY = 11;
    int TAG_LONG_ARRAY = 12;
}
