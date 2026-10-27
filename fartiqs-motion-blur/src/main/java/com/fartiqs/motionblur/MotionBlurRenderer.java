package com.fartiqs.motionblur;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public class MotionBlurRenderer {
    private static SimpleFramebuffer previous;
    private static float lastYaw, lastPitch;
    private static boolean hasLastRot;

    public static void apply(MinecraftClient mc) {
        Config cfg = Config.get();
        if (!cfg.enabled || mc.world == null) {
            hasLastRot = false;
            return;
        }
        if (cfg.disableInMenus && mc.currentScreen != null && !(mc.currentScreen instanceof MotionBlurScreen)) {
            copyToPrevious(mc.getFramebuffer());
            return;
        }

        Framebuffer main = mc.getFramebuffer();
        if (previous == null || previous.textureWidth != main.textureWidth || previous.textureHeight != main.textureHeight) {
            if (previous != null) previous.delete();
            previous = new SimpleFramebuffer(main.textureWidth, main.textureHeight, false);
            copyToPrevious(main);
            return;
        }

        float alpha = MathHelper.clamp(cfg.strength, 0f, 0.95f);

        if (cfg.adaptive) {
            float yaw = mc.gameRenderer.getCamera().getYaw();
            float pitch = mc.gameRenderer.getCamera().getPitch();
            if (hasLastRot) {
                float dy = Math.abs(MathHelper.wrapDegrees(yaw - lastYaw));
                float dp = Math.abs(pitch - lastPitch);
                float movement = dy + dp;
                // more sensitivity = less camera movement needed for full blur
                float full = MathHelper.lerp(cfg.adaptiveSensitivity, 12f, 1.5f);
                float factor = MathHelper.clamp(movement / full, 0f, 1f);
                alpha *= factor;
            }
            lastYaw = yaw;
            lastPitch = pitch;
            hasLastRot = true;
        }

        if (alpha > 0.001f) {
            drawTexture(main, previous.getColorAttachment(), alpha, true);
        }
        copyToPrevious(main);
    }

    private static void copyToPrevious(Framebuffer main) {
        if (previous == null || previous.textureWidth != main.textureWidth || previous.textureHeight != main.textureHeight) {
            if (previous != null) previous.delete();
            previous = new SimpleFramebuffer(main.textureWidth, main.textureHeight, false);
        }
        drawTexture(previous, main.getColorAttachment(), 1f, false);
        main.beginWrite(false);
    }

    /** Draws a full-screen quad of the given texture into the target framebuffer. */
    private static void drawTexture(Framebuffer target, int texture, float alpha, boolean blend) {
        target.beginWrite(false);
        RenderSystem.setProjectionMatrix(new Matrix4f(), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (blend) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(-1f, -1f, 0f).texture(0f, 0f).color(1f, 1f, 1f, alpha);
        buf.vertex(1f, -1f, 0f).texture(1f, 0f).color(1f, 1f, 1f, alpha);
        buf.vertex(1f, 1f, 0f).texture(1f, 1f).color(1f, 1f, 1f, alpha);
        buf.vertex(-1f, 1f, 0f).texture(0f, 1f).color(1f, 1f, 1f, alpha);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
