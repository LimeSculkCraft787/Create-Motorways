package com.sculkcrafterthefirst.motorways;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class RoadLocator {
    // s: Bogenlänge ab Straßenanfang, lat: Seitenabstand von der Mitte, tx/tz: Straßenrichtung, top: Höhe der Oberseite
    public record Hit(RoadNodeBlockEntity node, RoadNodeBlockEntity.Road road,
                      double s, double lat, double tx, double tz, double top) {
    }

    public static Hit find(Level level, Vec3 p) {
        int pcx = Mth.floor(p.x) >> 4;
        int pcz = Mth.floor(p.z) >> 4;
        Hit best = null;
        double bestScore = Double.MAX_VALUE;

        int radius = Config.MAX_ROAD_LENGTH.get() / 16 + 2;
        for (int cxo = -radius; cxo <= radius; cxo++) {
            for (int czo = -radius; czo <= radius; czo++) {
                if (!level.hasChunk(pcx + cxo, pcz + czo)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(pcx + cxo, pcz + czo);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof RoadNodeBlockEntity node)) {
                        continue;
                    }
                    BlockPos np = node.getBlockPos();
                    for (RoadNodeBlockEntity.Road road : node.getRoads()) {
                        RoadCurve curve = road.curve(np);
                        double half = road.width / 2.0 + 0.25;
                        int n = curve.count - 1;
                        double step = curve.length / n;
                        for (int i = 0; i < n; i++) {
                            double ax = np.getX() + curve.x[i];
                            double az = np.getZ() + curve.z[i];
                            double ex = curve.x[i + 1] - curve.x[i];
                            double ez = curve.z[i + 1] - curve.z[i];
                            double len2 = ex * ex + ez * ez;
                            if (len2 < 1e-9) {
                                continue;
                            }
                            double len = Math.sqrt(len2);
                            double f = ((p.x - ax) * ex + (p.z - az) * ez) / len2;
                            if (f < 0 || f > 1) {
                                continue;
                            }
                            double lat = ((p.x - ax) * (-ez) + (p.z - az) * ex) / len;
                            if (Math.abs(lat) > half) {
                                continue;
                            }
                            double top = np.getY() + curve.y[i] + (curve.y[i + 1] - curve.y[i]) * f + 5.0 / 16.0;
                            if (Math.abs(p.y - top) > 0.5) {
                                continue;
                            }
                            double score = Math.abs(lat) + Math.abs(p.y - top);
                            if (score < bestScore) {
                                bestScore = score;
                                best = new Hit(node, road, (i + f) * step, lat, ex / len, ez / len, top);
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
}
