package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;

/**
 * Внешний корпус прямоугольной паровой турбины.
 *
 * <p>Сам блок хранит только {@link TurbinePartBlock#FORMED}. Койма, наружные
 * углы и 2×2 внутренние уголки собираются на клиенте Smart CTM-моделью из
 * восьми соседей каждой видимой грани. Поэтому у корпуса нет визуальных
 * {@code port_*}/{@code frame}/{@code cap_*} BlockState-вариантов и тысяч
 * JSON-моделей.</p>
 */
public final class TurbineCasingBlock extends TurbinePartBlock {

    public static final MapCodec<TurbineCasingBlock> CODEC = simpleCodec(TurbineCasingBlock::new);

    public TurbineCasingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<TurbineCasingBlock> codec() {
        return CODEC;
    }
}
