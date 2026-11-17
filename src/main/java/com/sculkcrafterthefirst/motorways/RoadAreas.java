package com.sculkcrafterthefirst.motorways;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;

// Merkt sich, welche Blöcke im Bereich einer Straße liegen, und liefert dort die Straßenkollision
public class RoadAreas {
    private static final class Cache {
        final Set<Long> nodes = new HashSet<>();
        Map<Long, Integer> cells; // null = muss neu berechnet werden
    }

    private static final VoxelShape[] SHAPES = new VoxelShape[17];

    static {
        for (int i = 1; i <= 16; i++) {
            SHAPES[i] = Block.box(0, 0, 0, 16, i, 16);
        }
    }

    private static final Map<Level, Cache> CACHES = new WeakHashMap<>();
    private static volatile Block[] tracks;

    private static Cache cache(Level level) {
        return CACHES.computeIfAbsent(level, k -> new Cache());
    }

    public static synchronized void register(Level level, BlockPos pos) {
        Cache c = cache(level);
        c.nodes.add(pos.asLong());
        c.cells = null;
    }

    public static synchronized void unregister(Level level, BlockPos pos) {
        Cache c = CACHES.get(level);
        if (c != null) {
            c.nodes.remove(pos.asLong());
            c.cells = null;
        }
    }

    public static synchronized void markDirty(Level level) {
        Cache c = CACHES.get(level);
        if (c != null) {
            c.cells = null;
        }
    }

    private static Block[] resolveTracks() {
        List<Block> list = new ArrayList<>();
        for (String id : Config.TRACK_BLOCKS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(list::add);
            }
        }
        return list.toArray(new Block[0]);
    }

    // Wird bei jeder Kollisionsabfrage aufgerufen: null = nichts ändern
    public static VoxelShape shapeFor(BlockState state, BlockGetter getter, BlockPos pos) {
        Block[] t = tracks;
        if (t == null) {
            if (!Config.SERVER_SPEC.isLoaded()) {
                return null;
            }
            t = resolveTracks();
            tracks = t;
        }
        if (t.length == 0) {
            return null;
        }
        Block b = state.getBlock();
        boolean match = false;
        for (Block candidate : t) {
            if (candidate == b) {
                match = true;
                break;
            }
        }
        if (!match) {
            return null;
        }

        Level level = null;
        if (getter instanceof Level l) {
            level = l;
        } else if (getter instanceof LevelChunk chunk) {
            level = chunk.getLevel();
        }
        if (level == null) {
            return null;
        }
        int layers = layersAt(level, pos);
        if (layers <= 0) {
            return null;
        }
        return SHAPES[Mth.clamp(layers, 1, 16)];
    }

    private static synchronized int layersAt(Level level, BlockPos pos) {
        Cache c = CACHES.get(level);
        if (c == null || c.nodes.isEmpty()) {
            return 0;
        }
        if (c.cells == null) {
            c.cells = rebuild(level, c);
        }
        return c.cells.getOrDefault(pos.asLong(), 0);
    }

    private static Map<Long, Integer> rebuild(Level level, Cache c) {
        Map<Long, Integer> result = new HashMap<>();
        for (long l : c.nodes) {
            BlockPos np = BlockPos.of(l);
            if (!level.hasChunkAt(np)) {
                continue;
            }
            if (!(level.getBlockEntity(np) instanceof RoadNodeBlockEntity node)) {
                continue;
            }
            for (RoadNodeBlockEntity.Road road : node.getRoads()) {
                for (Map.Entry<BlockPos, Integer> e : RoadCollision.cells(np, road.curve(np), road.width).entrySet()) {
                    result.merge(e.getKey().asLong(), e.getValue(), Math::max);
                }
            }
        }
        return result;
    }
}
