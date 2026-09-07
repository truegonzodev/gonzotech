package com.gonzotech.machines.network;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Всё, что «несёт» одну или несколько труб. Реализуют {@link PipeBlock} (одна
 * труба фиксированного типа) и {@link CompositePipeBlock} (составной блок —
 * несколько типов в одном кубе, каждый в своём углу сечения, не соединяясь между
 * собой).
 * <p>
 * Маршрутизатор {@link PipeRouting} работает ТОЛЬКО через этот интерфейс и тип
 * ресурса. Ему всё равно, одиночная это труба, узел или связка — важно лишь
 * «несёт ли этот блок тип T и открыта ли его грань в сторону D». Благодаря этому
 * логика передачи одна на всех, а стакаемость труб — чисто вопрос геометрии.
 */
public interface PipeCarrier {

    /** Несёт ли этот блок трубу типа {@code type}. */
    boolean carries(BlockState state, PipeType type);

    /**
     * Открыта ли грань трубы типа {@code type} в сторону {@code dir}. Вызывается
     * маршрутизатором только когда {@link #carries} истинно. У обычной трубы
     * открыты два торца по её оси (у узла — все 6); у связки — по общей оси.
     */
    boolean opensToward(BlockState state, PipeType type, Direction dir);

    /** Режим (AUTO/PULL/PUSH) трубы типа {@code type} в этом блоке. */
    PipeMode modeFor(BlockState state, PipeType type);
}
