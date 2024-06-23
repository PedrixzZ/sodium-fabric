package me.jellysquid.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    protected static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    protected static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    protected static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    private final StagingBuffer stagingBuffer;
    private final int x, y, z;

    private final ChunkRenderList renderList;

    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    private int sectionCount;

    // Usando ConcurrentHashMap para melhorar a concorrência e o desempenho
    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new ConcurrentHashMap<>();
    private DeviceResources resources;

    public RenderRegion(int x, int y, int z, StagingBuffer stagingBuffer) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.stagingBuffer = stagingBuffer;
        this.renderList = new ChunkRenderList(this);
    }

    public static long key(int x, int y, int z) {
        return ChunkSectionPos.asLong(x, y, z);
    }

    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public void delete(CommandList commandList) {
        sectionRenderData.values().forEach(SectionRenderDataStorage::delete);
        sectionRenderData.clear();

        if (resources != null) {
            resources.delete(commandList);
            resources = null;
        }

        Arrays.fill(sections, null);
    }

    public boolean isEmpty() {
        return sectionCount == 0;
    }

    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return sectionRenderData.get(pass);
    }

    public SectionRenderDataStorage createStorage(TerrainRenderPass pass) {
        return sectionRenderData.computeIfAbsent(pass, k -> new SectionRenderDataStorage());
    }

    public void refresh(CommandList commandList) {
        sectionRenderData.values().forEach(SectionRenderDataStorage::onBufferResized);
    }

    public synchronized void addSection(RenderSection section) {
        int sectionIndex = section.getSectionIndex();

        if (sections[sectionIndex] != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        sections[sectionIndex] = section;
        sectionCount++;
    }

    public synchronized void removeSection(RenderSection section) {
        int sectionIndex = section.getSectionIndex();

        if (sections[sectionIndex] == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        }

        if (sections[sectionIndex] != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        sectionRenderData.values().forEach(storage -> storage.removeMeshes(sectionIndex));

        sections[sectionIndex] = null;
        sectionCount--;
    }

    public RenderSection getSection(int id) {
        return sections[id];
    }

    public DeviceResources getResources() {
        return resources;
    }

    public DeviceResources createResources(CommandList commandList) {
        if (resources == null) {
            resources = new DeviceResources(commandList, stagingBuffer, REGION_SIZE);
        }

        return resources;
    }

    public void update(CommandList commandList) {
        if (resources != null && resources.shouldDelete()) {
            resources.delete(commandList);
            resources = null;
        }
    }

    public ChunkRenderList getRenderList() {
        return renderList;
    }

    public static class DeviceResources {
        private final GlBufferArena geometryArena;
        private GlTessellation tessellation;

        public DeviceResources(CommandList commandList, StagingBuffer stagingBuffer, int regionSize) {
            int stride = ChunkMeshFormats.COMPACT.getVertexFormat().getStride();
            this.geometryArena = new GlBufferArena(commandList, regionSize * stride * 6, stride, stagingBuffer); // Ajuste do tamanho do buffer

            // Inicialização assíncrona ou em thread separada da tessellation, se necessário
        }

        public void updateTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
            }

            this.tessellation = tessellation;
        }

        public GlTessellation getTessellation() {
            return tessellation;
        }

        public void deleteTessellations(CommandList commandList) {
            if (tessellation != null) {
                tessellation.delete(commandList);
                tessellation = null;
            }
        }

        public GlBuffer getVertexBuffer() {
            return geometryArena.getBufferObject();
        }

        public void delete(CommandList commandList) {
            deleteTessellations(commandList);
            geometryArena.delete(commandList);
        }

        public GlBufferArena getGeometryArena() {
            return geometryArena;
        }

        public boolean shouldDelete() {
            return geometryArena.isEmpty();
        }
    }
}
