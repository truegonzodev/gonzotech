package com.gonzotech.machines.network;

import net.minecraft.util.StringRepresentable;

/**
 * Режим отдельной трубы, переключается ПКМ гаечным ключом. Хранится в
 * блокстейте трубы (свойство {@code mode}), поэтому у труб НЕ нужен BlockEntity —
 * режим виден прямо в состоянии блока.
 * <p>
 * Режим влияет на то, как ИМЕННО эта труба взаимодействует с примыкающими к ней
 * машинами (не-трубами):
 * <ul>
 *   <li>{@link #AUTO} — «умолчание»: труба и тянет из источников, и отдаёт в
 *       приёмники среди своих соседей-машин. Достаточно для простого случая
 *       «генератор — провод — потребитель».</li>
 *   <li>{@link #PULL} — труба ТОЛЬКО тянет из примыкающих машин в сеть (вход).</li>
 *   <li>{@link #PUSH} — труба ТОЛЬКО отдаёт из сети в примыкающие машины (выход).</li>
 * </ul>
 * Важно: режим — свойство КОНКРЕТНОЙ трубы, а не всей сети. Сеть смотрит на
 * концы своих труб: где режим разрешает вход — забирает, где разрешает выход —
 * раздаёт. Так один контур может тянуть с одного конца и отдавать на другом.
 */
public enum PipeMode implements StringRepresentable {

    AUTO("auto"),
    PULL("pull"),
    PUSH("push");

    private final String name;

    PipeMode(String name) {
        this.name = name;
    }

    /** true, если в этом режиме труба может ЗАБИРАТЬ ресурс из примыкающей машины. */
    public boolean canPull() {
        return this == AUTO || this == PULL;
    }

    /** true, если в этом режиме труба может ОТДАВАТЬ ресурс в примыкающую машину. */
    public boolean canPush() {
        return this == AUTO || this == PUSH;
    }

    /** Следующий режим по кругу AUTO → PULL → PUSH → AUTO (для ПКМ ключом). */
    public PipeMode next() {
        return switch (this) {
            case AUTO -> PULL;
            case PULL -> PUSH;
            case PUSH -> AUTO;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
