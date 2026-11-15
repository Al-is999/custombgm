package com.aurora.custombgm.mixin;

import com.aurora.custombgm.music.MusicScheduler;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 接管原版背景音乐的调度。只有当前情境下存在自定义曲目时才压制原版，
 * 否则原版音乐照常工作（相当于给整合包作者留了"只替换一部分"的余地）。
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Unique
    private static boolean custombgm$suppressing;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void custombgm$tick(CallbackInfo ci) {
        boolean suppress = MusicScheduler.get().shouldSuppressVanilla();
        if (suppress != custombgm$suppressing) {
            custombgm$suppressing = suppress;
            if (suppress) {
                ((MusicManager) (Object) this).stopPlaying();
            }
        }
        if (suppress) {
            ci.cancel();
        }
    }
}
