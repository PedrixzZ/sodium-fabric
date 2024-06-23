import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.arena.PendingUpload;
import me.jellysquid.mods.sodium.client.gl.arena.staging.FallbackStagingBuffer;
import me.jellysquid.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenderRegionManager {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();
    private final StagingBuffer stagingBuffer;

    public RenderRegionManager(CommandList commandList) {
        this.stagingBuffer = createStagingBuffer(commandList);
    }

    public void update() {
        this.stagingBuffer.flip();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            Iterator<RenderRegion> it = this.regions.values().iterator();

            while (it.hasNext()) {
                RenderRegion region = it.next();
                region.update(commandList);

                if (region.isEmpty()) {
                    region.delete(commandList);
                    it.remove();
                }
            }
        }
    }

    public void uploadMeshes(CommandList commandList, Collection<ChunkBuildOutput> results) {
        results.parallelStream()
               .collect(groupingByConcurrent(ChunkBuildOutput::getRender, mapping(identity(), toList())))
               .forEach((region, builds) -> uploadMeshes(commandList, region, builds));
    }

    private void uploadMeshes(CommandList commandList, RenderRegion region, List<ChunkBuildOutput> results) {
        List<PendingSectionUpload> uploads = new ArrayList<>();

        results.forEach(result -> {
            DefaultTerrainRenderPasses.ALL.forEach(pass -> {
                var storage = region.getStorage(pass);

                if (storage != null) {
                    storage.removeMeshes(result.render.getSectionIndex());
                }

                BuiltSectionMeshParts mesh = result.getMesh(pass);

                if (mesh != null) {
                    uploads.add(new PendingSectionUpload(result.render, mesh, pass,
                            new PendingUpload(mesh.getVertexData())));
                }
            });
        });

        if (uploads.isEmpty()) {
            return;
        }

        var resources = region.createResources(commandList);
        var arena = resources.getGeometryArena();

        boolean bufferChanged = arena.upload(commandList, uploads.stream()
                .map(upload -> upload.vertexUpload));

        if (bufferChanged) {
            region.refresh(commandList);
        }

        uploads.forEach(upload -> {
            var storage = region.createStorage(upload.pass);
            storage.setMeshes(upload.section.getSectionIndex(),
                    upload.vertexUpload.getResult(), upload.meshData.getVertexRanges());
        });
    }

    public void delete(CommandList commandList) {
        this.regions.values().forEach(region -> region.delete(commandList));
        this.regions.clear();
        this.stagingBuffer.delete(commandList);
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    @NotNull
    private RenderRegion create(int x, int y, int z) {
        long key = RenderRegion.key(x, y, z);
        RenderRegion instance = this.regions.get(key);

        if (instance == null) {
            instance = new RenderRegion(x, y, z, this.stagingBuffer);
            this.regions.put(key, instance);
        }

        return instance;
    }

    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (SodiumClientMod.options().advanced.useAdvancedStagingBuffers && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }

    private record PendingSectionUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload) {
    }
}
