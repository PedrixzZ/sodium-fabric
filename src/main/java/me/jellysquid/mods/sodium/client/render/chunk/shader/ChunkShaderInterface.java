package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import me.jellysquid.mods.sodium.client.util.TextureUtil;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL13; // Importe a classe GL13 para acessar GL_TEXTURE0 e GL_TEXTURE1


import java.util.function.Function;

public class ChunkShaderInterface {
    private final GlUniformMatrix4f uniformModelViewMatrix;
    private final GlUniformMatrix4f uniformProjectionMatrix;
    private final GlUniformFloat3v uniformRegionOffset;
    private final GlUniformInt uniformBlockTex;
    private final GlUniformInt uniformLightTex;
    private final Function<ShaderBindingContext, ChunkShaderFogComponent> fogComponentFactory;

    private ChunkShaderFogComponent fogComponent;

    public ChunkShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        int shaderProgramId = /* Adicione aqui o ID do programa de shader */;
        
        this.uniformModelViewMatrix = new GlUniformMatrix4f(shaderProgramId, "u_ModelViewMatrix");
        this.uniformProjectionMatrix = new GlUniformMatrix4f(shaderProgramId, "u_ProjectionMatrix");
        this.uniformRegionOffset = new GlUniformFloat3v(shaderProgramId, "u_RegionOffset");
        this.uniformBlockTex = new GlUniformInt(shaderProgramId, "u_BlockTex");
        this.uniformLightTex = new GlUniformInt(shaderProgramId, "u_LightTex");

        this.fogComponentFactory = options.fog().getFactory();
        this.fogComponent = this.fogComponentFactory.apply(context);
    }

    public void setupState() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(TextureUtil.getBlockTextureId());
        this.uniformBlockTex.set(0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
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
