package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.3.335 — ЗАГОТОВКА под {@code D-PORTMODE1} (0.3.336+, автор: «Режимы для
 * вшитых позже … конкретную реализацию обсудим после завершения и утверждения
 * патча 0.3.335»).
 *
 * <p>Сейчас режим вшитого корпусного порта НЕ подменяется: метод возвращает
 * текущий {@link PipeMode} без изменений и НИГДЕ не вызывается в runtime.
 * Точки будущего вызова помечены комментариями в
 * {@link PipeRouting#drainFromMultiblockPort} и в методе addPortLane:
 * именно там режим порта участвует в сливе/приёме.</p>
 *
 * <p>Авторская постановка для будущей реализации: AUTO — «обе стороны, делят
 * общую проводимость порта», PULL — «забор», PUSH — «отдача». В 0.3.335
 * валидность реактора от режима уже не зависит (REACT-VAL1), но сама логика
 * направления I/O оставлена как есть до отдельного решения.</p>
 */
public final class EmbeddedPortMode {

    private EmbeddedPortMode() {
    }

    /**
     * Заглушка точки вызова политики режимов. Намеренно не меняет поведение:
     * конкретная реализация и место вызова выбираются автором после 0.3.335.
     */
    public static PipeMode resolve(Level level, BlockPos pos, BlockState state,
                                   PipeType type, PipeMode current) {
        // TODO(0.3.336+ D-PORTMODE1): подставить политику вшитого порта,
        // когда автор утвердит распределение общей проводимости и направления.
        return current;
    }
}
