package com.fartiqs.motionblur.mixin;

import com.fartiqs.motionblur.Config;
import com.fartiqs.motionblur.MotionBlurRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final MinecraftClient client;

    // Motion blur: runs right after the 3D world is drawn, before the HUD
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
                    shift = At.Shift.AFTER))
    private void fartiqs$motionBlur(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MotionBlurRenderer.apply(this.client);
    }

    // Aspect ratio: rescales the horizontal axis of the projection matrix
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void fartiqs$aspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        Config cfg = Config.get();
        if (!cfg.aspectEnabled) return;
        float windowAspect = (float) this.client.getWindow().getFramebufferWidth()
                / (float) this.client.getWindow().getFramebufferHeight();
        float factor = windowAspect / Math.max(cfg.aspect, 0.1f);
        Matrix4f m = new Matrix4f(cir.getReturnValue());
        m.scaleLocal(factor, 1f, 1f);
        cir.setReturnValue(m);
    }
}
