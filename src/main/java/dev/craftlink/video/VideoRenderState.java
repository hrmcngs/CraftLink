package dev.craftlink.video;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/** State shared by world, hand and GUI passes, including Minecraft's GL caches. */
final class VideoRenderState {
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] textures = new int[12], samplers = new int[12];
    private final ShaderInstance shader = RenderSystem.getShader();
    private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
    private final float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
    private final float[] fogColor = RenderSystem.getShaderFogColor().clone();
    private final FogShape fogShape = RenderSystem.getShaderFogShape();
    private final float lineWidth = RenderSystem.getShaderLineWidth(), glintAlpha = RenderSystem.getShaderGlintAlpha();
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

    VideoRenderState() {
        for (int unit = 0; unit < textures.length; unit++) {
            samplers[unit] = RenderSystem.getShaderTexture(unit);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(activeTexture);
    }

    void restore() {
        for (int unit = 0; unit < textures.length; unit++) {
            RenderSystem.setShaderTexture(unit, samplers[unit]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            RenderSystem.bindTexture(textures[unit]);
        }
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setTextureMatrix(textureMatrix);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        RenderSystem.setShaderFogShape(fogShape);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.setShaderGlintAlpha(glintAlpha);
        RenderSystem.depthFunc(depthFunction);
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }
}
