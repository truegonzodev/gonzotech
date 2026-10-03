package com.gonzotech.mixin;

import com.gonzotech.machines.block.ThirdPistonBlock;
import com.gonzotech.machines.block.ThirdStickyPistonBlock;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ванильный {@code PistonBaseBlock.moveBlocks} жёстко создаёт головку
 * {@code Blocks.PISTON_HEAD} — свинцовый поршень получал ванильную деревянную
 * головку (репорт автора, раунд 13). Два редиректа:
 * <ul>
 *   <li><b>создание</b> (второе обращение к полю {@code Blocks.PISTON_HEAD} в
 *       методе): свинцовые основания получают нашу головку;</li>
 *   <li><b>проверка {@code is(Blocks.PISTON_HEAD)}</b> (первое обращение, вветке
 *       втягивания): наша головка тоже считается «головкой» и молча снимается —
 *       иначе резолвер считал бы её толкаемым блоком с DESTROY.</li>
 * </ul>
 * Для ванильных поршней оба редиректа прозрачны (возвращают ванильный блок).
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    @Redirect(method = "moveBlocks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/Blocks;PISTON_HEAD:Lnet/minecraft/world/level/block/Block;",
                 ordinal = 1))
    private Block gonzotech$customHead(PistonBaseBlock piston) {
        if ((Object) this instanceof ThirdPistonBlock || (Object) this instanceof ThirdStickyPistonBlock) {
            return ModMachines.THIRD_LEAD_PISTON_HEAD.get();
        }
        return Blocks.PISTON_HEAD;
    }

    @Redirect(method = "moveBlocks",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean gonzotech$anyModdedHead(BlockState state, Block block) {
        if (block == Blocks.PISTON_HEAD && state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonHeadBlock) {
            return true;
        }
        return state.is(block);
    }
}
