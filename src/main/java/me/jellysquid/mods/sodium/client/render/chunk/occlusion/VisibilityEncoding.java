package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import net.minecraft.client.render.chunk.ChunkOcclusionData;
import org.jetbrains.annotations.NotNull;

public class VisibilityEncoding {
    private static final int COUNT = GraphDirection.COUNT;
    private static final long ALL_MASK = 0b00000001_00000001_00000001_00000001_00000001_00000001L;
    private static final long[] MASKS = createMasks();

    private static long[] createMasks() {
        long[] masks = new long[COUNT];
        for (int i = 0; i < COUNT; i++) {
            masks[i] = (ALL_MASK * Integer.toUnsignedLong(i)) & ALL_MASK;
        }
        return masks;
    }

    public static long encode(@NotNull ChunkOcclusionData occlusionData) {
        long visibilityData = 0;

        for (int from = 0; from < COUNT; from++) {
            for (int to = 0; to < COUNT; to++) {
                if (occlusionData.isVisibleThrough(GraphDirection.toEnum(from), GraphDirection.toEnum(to))) {
                    visibilityData |= 1L << bit(from, to);
                }
            }
        }

        return visibilityData;
    }

    private static int bit(int from, int to) {
        return (from * 8) + to;
    }

    public static int getConnections(long visibilityData, int incoming) {
        return foldOutgoingDirections(visibilityData & MASKS[incoming]);
    }

    public static int getConnections(long visibilityData) {
        return foldOutgoingDirections(visibilityData);
    }

    private static int foldOutgoingDirections(long data) {
        data |= data >> 32;
        data |= data >> 16;
        data |= data >> 8;
        return (int) (data & GraphDirectionSet.ALL);
    }
}
