package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.util.collections.DoubleBufferedQueue;
import me.jellysquid.mods.sodium.client.util.collections.ReadQueue;
import me.jellysquid.mods.sodium.client.util.collections.WriteQueue;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class OcclusionCuller {
    private final Long2ReferenceMap<RenderSection> sections;
    private final World world;

    private final DoubleBufferedQueue<RenderSection> queue = new DoubleBufferedQueue<>();

    private static final float CHUNK_SECTION_SIZE = 8.0f + 1.0f + 0.125f;

    public OcclusionCuller(Long2ReferenceMap<RenderSection> sections, World world) {
        this.sections = sections;
        this.world = world;
    }

    public void findVisible(Visitor visitor,
                            Viewport viewport,
                            float searchDistance,
                            boolean useOcclusionCulling,
                            int frame)
    {
        final var queues = this.queue;
        queues.reset();

        init(visitor, queues.write(), viewport, searchDistance, useOcclusionCulling, frame);

        while (queues.flip()) {
            processQueue(visitor, viewport, searchDistance, useOcclusionCulling, frame, queues.read(), queues.write());
        }
    }

    private void processQueue(Visitor visitor,
                              Viewport viewport,
                              float searchDistance,
                              boolean useOcclusionCulling,
                              int frame,
                              ReadQueue<RenderSection> readQueue,
                              WriteQueue<RenderSection> writeQueue)
    {
        RenderSection section;

        while ((section = readQueue.dequeue()) != null) {
            boolean visible = isSectionVisible(section, viewport, searchDistance);
            visitor.visit(section, visible);

            if (!visible) {
                continue;
            }

            int connections = useOcclusionCulling ?
                    VisibilityEncoding.getConnections(section.getVisibilityData(), section.getIncomingDirections()) :
                    GraphDirectionSet.ALL;

            connections &= getOutwardDirections(viewport.getChunkCoord(), section);

            visitNeighbors(writeQueue, section, connections, frame);
        }
    }

    private static boolean isSectionVisible(RenderSection section, Viewport viewport, float maxDistance) {
        return isWithinRenderDistance(viewport.getTransform(), section, maxDistance) && isWithinFrustum(viewport, section);
    }

    private static void visitNeighbors(final WriteQueue<RenderSection> queue, RenderSection section, int outgoing, int frame) {
        outgoing &= section.getAdjacentMask();

        if (outgoing == GraphDirectionSet.NONE) {
            return;
        }

        queue.ensureCapacity(6);

        if (GraphDirectionSet.contains(outgoing, GraphDirection.DOWN)) {
            visitNode(queue, section.adjacentDown, GraphDirectionSet.of(GraphDirection.UP), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.UP)) {
            visitNode(queue, section.adjacentUp, GraphDirectionSet.of(GraphDirection.DOWN), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.NORTH)) {
            visitNode(queue, section.adjacentNorth, GraphDirectionSet.of(GraphDirection.SOUTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.SOUTH)) {
            visitNode(queue, section.adjacentSouth, GraphDirectionSet.of(GraphDirection.NORTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.WEST)) {
            visitNode(queue, section.adjacentWest, GraphDirectionSet.of(GraphDirection.EAST), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.EAST)) {
            visitNode(queue, section.adjacentEast, GraphDirectionSet.of(GraphDirection.WEST), frame);
        }
    }

    private static void visitNode(final WriteQueue<RenderSection> queue, @NotNull RenderSection render, int incoming, int frame) {
        if (render.getLastVisibleFrame() != frame) {
            render.setLastVisibleFrame(frame);
            render.setIncomingDirections(GraphDirectionSet.NONE);

            queue.enqueue(render);
        }

        render.addIncomingDirections(incoming);
    }

    private static int getOutwardDirections(ChunkSectionPos origin, RenderSection section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.getX() ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.getX() ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.getY() ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.getY() ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.getZ() ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.getZ() ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    private static boolean isWithinRenderDistance(CameraTransform camera, RenderSection section, float maxDistance) {
        int ox = section.getOriginX() - camera.intX;
        int oy = section.getOriginY() - camera.intY;
        int oz = section.getOriginZ() - camera.intZ;

        float dx = nearestToZero(ox, ox + 16) - camera.fracX;
        float dy = nearestToZero(oy, oy + 16) - camera.fracY;
        float dz = nearestToZero(oz, oz + 16) - camera.fracZ;

        return (((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance);
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    private static int nearestToZero(int min, int max) {
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }

    public static boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
                CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE);
    }

    private void init(Visitor visitor,
                      WriteQueue<RenderSection> queue,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.getY() < this.world.getBottomSectionCoord()) {
            initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.world.getBottomSectionCoord(), GraphDirection.DOWN);
        } else if (origin.getY() >= this.world.getTopSectionCoord()) {
            initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.world.getTopSectionCoord() - 1, GraphDirection.UP);
        } else {
            initWithinWorld(visitor, queue, viewport, useOcclusionCulling, frame);
        }
    }

    private void initWithinWorld(Visitor visitor, WriteQueue<RenderSection> queue, Viewport viewport, boolean useOcclusionCulling, int frame) {
        var origin = viewport.getChunkCoord();
        var section = getRenderSection(origin.getX(), origin.getY(), origin.getZ());

        if (section == null) {
            return;
        }

        section.setLastVisibleFrame(frame);
        section.setIncomingDirections(GraphDirectionSet.NONE);

        visitor.visit(section, true);

        int outgoing = useOcclusionCulling ?
                VisibilityEncoding.getConnections(section.getVisibilityData()) :
                GraphDirectionSet.ALL;

        visitNeighbors(queue, section, outgoing, frame);
    }

    private void initOutsideWorldHeight(WriteQueue<RenderSection> queue,
                                        Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        var origin = viewport.getChunkCoord();
        var radius = MathHelper.floor(searchDistance / 16.0f);

        tryVisitNode(queue, origin.getX(), height, origin.getZ(), direction, frame, viewport);

        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }

        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }
    }

    private void tryVisitNode(WriteQueue<RenderSection> queue, int x, int y, int z, int direction, int frame, Viewport viewport) {
        RenderSection section = this.getRenderSection(x, y, z);

        if (section == null || !isWithinFrustum(viewport, section)) {
            return;
        }

        visitNode(queue, section, GraphDirectionSet.of(direction), frame);
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sections.get(ChunkSectionPos.asLong(x, y, z));
    }

    public interface Visitor {
        void visit(RenderSection section, boolean visible);
    }
}
