package com.gonzotech.mixin;

import com.gonzotech.machines.block.ThirdStickyPistonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Голова поршня выживает только над ВАНИЛЬНЫМ основанием: ваниль
 * {@code isFittingBase} проверяет точные блоки {@code Blocks.PISTON}/
 * {@code Blocks.STICKY_PISTON} (1.21.4, dino939). Свинцовый поршень — подкласс
 * {@link PistonBaseBlock}, но другой блок: голова ставится и мгновенно
 * разрушается («головка сразу разрушается при выдвижении», репорт автора,
 * раунд 12). Разрешаем также модифицированные основания: подкласс
 * {@link PistonBaseBlock}, липкость по типу головы, состояние по EXTENDED+FACING —
 * как у ванили. Текстура головы остаётся ванильной (голову ставит ванильный
 * {@code moveBlocks} — замена потребует отдельного миксина, автору показано).
 */
@Mixin(PistonHeadBlock.class)
public abstract class PistonHeadBlockMixin {

    @Inject(method = "isFittingBase", at = @At("RETURN"), cancellable = true)
    private void gonzotech$acceptModdedBase(BlockState head, BlockState base, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (base.is(Blocks.PISTON) || base.is(Blocks.STICKY_PISTON)) return;
        if (!PistonBaseBlock.class.isAssignableFrom(base.getBlock().getClass())) {
            return;
        }
        boolean sticky = ThirdStickyPistonBlock.class.isAssignableFrom(base.getBlock().getClass());
        if ((head.getValue(PistonHeadBlock.TYPE) == PistonType.STICKY) != sticky) return;
        cir.setReturnValue(base.getValue(PistonBaseBlock.EXTENDED)
            && base.getValue(PistonBaseBlock.FACING) == head.getValue(PistonHeadBlock.FACING));
    }
}
