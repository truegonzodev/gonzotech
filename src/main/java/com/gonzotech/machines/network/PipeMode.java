package com.gonzotech.machines.network;

import net.minecraft.util.StringRepresentable;

/**
 * Режим отдельной трубы, переключается ПКМ гаечным ключом. Хранится в
 * блокстейте трубы (свойство {@code mode}), поэтому у труб НЕ нужен BlockEntity.
 * <p>
 * Модель проводов — «вынос слива за пределы блока»: машина ДОБРОВОЛЬНО сливает
 * свою выходную шкалу, а провод лишь дотягивает этот слив до приёмников в связной
 * цепи. Ничего ниоткуда НЕ «высасывается». Режим определяет, как грань трубы,
 * прилегающая к машине, участвует в этом сливе:
 * <ul>
 *   <li>{@link #AUTO} — и принимает слив из машины в сеть, и отдаёт из сети в
 *       машину (универсальный конец);</li>
 *   <li>{@link #PULL} — «ЗАБОР»: только вход. Машина может слить в сеть здесь, но
 *       сеть сюда НЕ отдаёт;</li>
 *   <li>{@link #PUSH} — «ОТДАЧА»: только выход. Сеть отдаёт в машину здесь, но
 *       машина НЕ может слить в сеть.</li>
 * </ul>
 * Режим — свойство КОНКРЕТНОЙ трубы. Внутри цепи трубы проводят ресурс независимо
 * от режима; режим важен ТОЛЬКО на грани труба↔машина (вход/выход).
 */
public enum PipeMode implements StringRepresentable {

    AUTO("auto"),
    PULL("pull"),
    PUSH("push");

    private final String name;

    PipeMode(String name) {
        this.name = name;
    }

    /** true, если через эту грань машина может СЛИТЬ ресурс в сеть (вход). */
    public boolean acceptsFromMachine() {
        return this == AUTO || this == PULL;
    }

    /** true, если через эту грань сеть может ОТДАТЬ ресурс в машину (выход). */
    public boolean deliversToMachine() {
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
