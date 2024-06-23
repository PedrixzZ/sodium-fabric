package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import me.jellysquid.mods.sodium.client.util.TextureUtil;
import org.joml.Matrix4fc;

import java.util.function.Function;

/**
 * A forward-rendering shader program for chunks.
 */
public class ChunkShaderInterface {
    private final GlUniformMatrix4f uniformModelViewMatrix;
    private final GlUniformMatrix4f uniformProjectionMatrix;
    private final GlUniformFloat3v uniformRegionOffset;
    private final GlUniformInt uniformBlockTex;
    private final GlUniformInt uniformLightTex;
    private final Function<ShaderBindingContext, ChunkShaderFogComponent> fogComponentFactory;

    private ChunkShaderFogComponent fogComponent;

    public ChunkShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        this.uniformModelViewMatrix = new GlUniformMatrix4f(context, "u_ModelViewMatrix");
        this.uniformProjectionMatrix = new GlUniformMatrix4f(context, "u_ProjectionMatrix");
        this.uniformRegionOffset = new GlUniformFloat3v(context, "u_RegionOffset");
        this.uniformBlockTex = new GlUniformInt(context, "u_BlockTex");
        this.uniformLightTex = new GlUniformInt(context, "u_LightTex");

        this.fogComponentFactory = options.fog().getFactory();
        this.fogComponent = this.fogComponentFactory.apply(context);
    }

    public void setupState() {
        RenderSystem.activeTexture(RenderSystem.GL_TEXTURE0);
        RenderSystem.bindTexture(TextureUtil.getBlockTextureId());
        this.uniformBlockTex.set(0);

        RenderSystem.activeTexture(RenderSystem.GL_TEXTURE1);
        RenderSystem.bindTexture(TextureUtil.getLightTextureId());
        this.uniformLightTex.set(1);

        this.fogComponent.setup();
    }

    public void setProjectionMatrix(Matrix4fc matrix) {
        this.uniformProjectionMatrix.set(matrix);
    }

    public void setModelViewMatrix(Matrix4fc matrix) {
        this.uniformModelViewMatrix.set(matrix);
    }

    public void setRegionOffset(float x, float y, float z) {
        this.uniformRegionOffset.set(x, y, z);
    }
}
