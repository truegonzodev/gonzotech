package com.gonzotech.mixin;

import com.gonzotech.cleanroom.CleanRoomSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notify clean-room/radiation geometry after explosions, pistons and command edits too. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkCleanRoomMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void gonzotech$cleanRoomChanged(BlockPos pos, BlockState state, boolean moving,
                                            CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        BlockState old = callback.getReturnValue();
        if (old != null && chunk.getLevel() instanceof ServerLevel level) {
            BlockState actual = chunk.getBlockState(pos);
            CleanRoomSystem.blockChanged(level, pos, old, actual);
            com.gonzotech.radiation.Containment.blockChanged(level, pos, old, actual);
            // 0.3.290 #17: тот же канал кормит счётчики Плотника (см. SECTOR_PULSE).
            com.gonzotech.machines.block.entity.PlotnickBlockEntity.pulseBlockChange(level, pos);
            // 0.3.299 (N13): тем же штампом инвалидируется кэш топологии труб.
            com.gonzotech.machines.network.ItemRouting.onWorldEdit(level);
            // 0.3.306 (автор 26.10): тем же каналом правки командой/взрывом
            // будят ревалидацию матрицы реактора, если клетка внутри границ.
            // 0.3.335 REACT-VAL1: смена ТОЛЬКО свойств BlockState (режим узла
            // ключом, FORMED обшивки) геометрию не меняет и форму не рвёт.
            if (com.gonzotech.machines.reactor.ReactorStructure.isCosmeticStateChange(old, actual)) {
                com.gonzotech.machines.reactor.ReactorStructure.onWorldEditStateOnly(level, pos, old, actual);
            } else {
                com.gonzotech.machines.reactor.ReactorStructure.onWorldEditCell(level, pos);
            }
            // 0.3.335 REACT-VAL2: поршень возвращает блок обшивки вместе с его
            // сохранённым BlockState (в т.ч. formed=true), но в этот момент
            // соседние клетки ещё могут быть moving_piston. Итоговая проверка
            // выполняется на ближайшем серверном тике панели, не здесь.
            if (com.gonzotech.machines.reactor.ReactorStructure.isShellPart(actual)
                && !old.is(actual.getBlock())) {
                com.gonzotech.machines.reactor.ReactorStructure.schedulePistonShellRevalidation(level, pos);
            }
        }
    }
}
