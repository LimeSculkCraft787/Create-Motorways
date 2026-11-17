package com.sculkcrafterthefirst.motorways;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class RoadCollision {
    private static final double HEIGHT = 5.0 / 16.0;

    // Welche Blöcke das Band berührt, und wie hoch (in 1/16) die Oberkante im jeweiligen Block liegt
    public static Map<BlockPos, Integer> cells(BlockPos start, RoadCurve curve, int width) {
        return cells(start, curve, width, false);
    }

    // legacy = true: altes Verfahren, das östlich und südlich einen Block zu viel erfasste
    private static Map<BlockPos, Integer> cells(BlockPos start, RoadCurve curve, int width, boolean legacy) {
        Map<BlockPos, Integer> map = new HashMap<>();
        double half = width / 2.0;
        int sub = 4;
        int steps = (int) Math.ceil(width / 0.25);
        for (int i = 0; i < curve.count - 1; i++) {
            int j = i + 1;
            for (int s = 0; s <= sub; s++) {
                double f = (double) s / sub;
                double cx = curve.x[i] + (curve.x[j] - curve.x[i]) * f;
                double cy = curve.y[i] + (curve.y[j] - curve.y[i]) * f;
                double cz = curve.z[i] + (curve.z[j] - curve.z[i]) * f;
                double tx = curve.tx[i] + (curve.tx[j] - curve.tx[i]) * f;
                double tz = curve.tz[i] + (curve.tz[j] - curve.tz[i]) * f;
                double tl = Math.hypot(tx, tz);
                if (tl < 1e-6) {
                    continue;
                }
                tx /= tl;
                tz /= tl;
                double px = -tz;
                double pz = tx;
                for (int k = 0; k <= steps; k++) {
                    double a = legacy
                            ? -half + 0.25 * k
                            : -half + 0.001 + (width - 0.002) * k / steps;
                    double wx = start.getX() + cx + px * a;
                    double wz = start.getZ() + cz + pz * a;
                    double top = start.getY() + cy + HEIGHT;
                    int top16 = (int) Math.round(top * 16.0);
                    int cellY = Math.floorDiv(top16 - 1, 16);
                    int layers = top16 - cellY * 16;
                    BlockPos p = new BlockPos(Mth.floor(wx), cellY, Mth.floor(wz));
                    map.merge(p, layers, Math::max);
                }
            }
        }
        return map;
    }

    public static void place(Level level, BlockPos start, RoadCurve curve, int width) {
        for (Map.Entry<BlockPos, Integer> e : cells(start, curve, width).entrySet()) {
            BlockPos p = e.getKey();
            int layers = Mth.clamp(e.getValue(), 1, 16);
            BlockState cur = level.getBlockState(p);
            if (cur.is(ModBlocks.ROAD_SEGMENT.get())) {
                if (cur.getValue(RoadSegmentBlock.LAYERS) < layers) {
                    level.setBlock(p, cur.setValue(RoadSegmentBlock.LAYERS, layers), 2);
                }
            } else if (cur.canBeReplaced() && cur.getFluidState().isEmpty()) {
                level.setBlock(p, ModBlocks.ROAD_SEGMENT.get().defaultBlockState()
                        .setValue(RoadSegmentBlock.LAYERS, layers), 2);
            }
        }
    }

    // Beim Entfernen werden auch die Randblöcke des alten Verfahrens aufgeräumt
    public static void remove(Level level, BlockPos start, RoadCurve curve, int width) {
        if (curve == null) {
            return;
        }
        Set<BlockPos> all = new HashSet<>(cells(start, curve, width, false).keySet());
        all.addAll(cells(start, curve, width, true).keySet());
        for (BlockPos p : all) {
            if (level.getBlockState(p).is(ModBlocks.ROAD_SEGMENT.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // Baut die Kollision aller Straßen, die an diesem Knoten hängen, neu auf
    public static void rebuildAt(Level level, BlockPos nodePos) {
        if (!(level.getBlockEntity(nodePos) instanceof RoadNodeBlockEntity be)) {
            return;
        }
        for (RoadNodeBlockEntity.Road r : be.getRoads()) {
            place(level, nodePos, r.curve(nodePos), r.width);
        }
        for (BlockPos s : be.getIncoming()) {
            if (level.getBlockEntity(s) instanceof RoadNodeBlockEntity sb) {
                for (RoadNodeBlockEntity.Road r : sb.getRoads()) {
                    if (r.target.equals(nodePos)) {
                        place(level, s, r.curve(s), r.width);
                    }
                }
            }
        }
    }
}
