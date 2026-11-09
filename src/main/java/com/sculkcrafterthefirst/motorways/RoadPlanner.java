package com.sculkcrafterthefirst.motorways;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class RoadPlanner {
    public static final int MAX_LENGTH = 32;
    public static final int SINGLE = 5;
    public static final int DOUBLE = 11;
    public static final double LANE_OFFSET = (DOUBLE - SINGLE) / 2.0;

    public record Plan(boolean ok, String message, RoadCurve curve,
                       double sdx, double sdz, double edx, double edz, double so, double eo) {
    }

    // Startrichtung und seitlicher Versatz einer neuen Straße an einem Knoten
    public record Anchor(boolean ok, String message, double dx, double dz, double offset) {
    }

    private record Candidates(List<RoadNodeBlockEntity.Arm> arms, boolean lane, boolean incompatible) {
    }

    private static Plan fail(String message) {
        return new Plan(false, message, null, 0, 0, 0, 0, 0, 0);
    }

    private static List<RoadNodeBlockEntity.Arm> nodeArms(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RoadNodeBlockEntity be) {
            return be.getArms();
        }
        return new ArrayList<>();
    }

    // Welche Anschlüsse eines Knotens kann eine Straße dieser Breite nutzen?
    private static Candidates candidates(Level level, BlockPos node, int width) {
        List<RoadNodeBlockEntity.Arm> all = nodeArms(level, node);
        List<RoadNodeBlockEntity.Arm> normal = new ArrayList<>();
        List<RoadNodeBlockEntity.Arm> lane = new ArrayList<>();
        for (RoadNodeBlockEntity.Arm a : all) {
            if (a.offset() != 0) {
                continue;
            }
            if (a.width() == width) {
                normal.add(a);
            } else if (width == SINGLE && a.width() == DOUBLE) {
                lane.add(a);
            }
        }
        if (!normal.isEmpty()) {
            return new Candidates(normal, false, false);
        }
        if (!lane.isEmpty()) {
            return new Candidates(lane, true, false);
        }
        return new Candidates(new ArrayList<>(), false, !all.isEmpty());
    }

    // +1 oder -1: auf welcher Seite der Straßenachse wurde gezielt?
    private static int laneSide(RoadNodeBlockEntity.Arm arm, BlockPos node, double hitX, double hitZ) {
        double lat = (hitX - (node.getX() + 0.5)) * (-arm.dz()) + (hitZ - (node.getZ() + 0.5)) * arm.dx();
        return lat >= 0 ? 1 : -1;
    }

    public static Anchor startAnchor(Level level, int width, BlockPos node, double lookX, double lookZ,
                                     double hitX, double hitZ) {
        if (!level.getBlockState(node).is(ModBlocks.ROAD_NODE.get())) {
            return new Anchor(true, "", lookX, lookZ, 0);
        }
        Candidates c = candidates(level, node, width);
        if (c.incompatible()) {
            return new Anchor(false, "Breite passt nicht zur bestehenden Straße", 0, 0, 0);
        }
        RoadNodeBlockEntity.Arm bestArm = null;
        double best = -2;
        for (RoadNodeBlockEntity.Arm a : c.arms()) {
            double d = -a.dx() * lookX - a.dz() * lookZ;
            if (d > best) {
                best = d;
                bestArm = a;
            }
        }
        if (bestArm == null) {
            return new Anchor(true, "", lookX, lookZ, 0);
        }
        double offset = 0;
        if (c.lane()) {
            offset = -laneSide(bestArm, node, hitX, hitZ) * LANE_OFFSET;
        }
        return new Anchor(true, "", -bestArm.dx(), -bestArm.dz(), offset);
    }

    public static Plan plan(Level level, int width, BlockPos start, double startDx, double startDz,
                            double startOff, BlockPos end, double lookX, double lookZ,
                            double hitX, double hitZ) {
        if (start.equals(end)) {
            return fail("Start und Ende sind gleich");
        }
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();
        double chord = Math.sqrt(dx * dx + dz * dz);
        if (chord < 2) {
            return fail("Zu kurz");
        }
        if (Math.sqrt(start.distSqr(end)) > MAX_LENGTH) {
            return fail("Zu weit entfernt (max. " + MAX_LENGTH + " Blöcke)");
        }

        boolean startExisting = level.getBlockState(start).is(ModBlocks.ROAD_NODE.get());
        boolean endExisting = level.getBlockState(end).is(ModBlocks.ROAD_NODE.get());
        if (!startExisting && !level.getBlockState(start).canBeReplaced()) {
            return fail("Der Startpunkt ist blockiert");
        }
        if (!endExisting && !level.getBlockState(end).canBeReplaced()) {
            return fail("Hier ist kein Platz");
        }

        if (level.getBlockEntity(start) instanceof RoadNodeBlockEntity sb && sb.hasRoadTo(end)) {
            return fail("Diese Verbindung gibt es schon");
        }
        if (level.getBlockEntity(end) instanceof RoadNodeBlockEntity eb && eb.hasRoadTo(start)) {
            return fail("Diese Verbindung gibt es schon");
        }

        double cx0 = dx / chord;
        double cz0 = dz / chord;

        double sdx = startDx;
        double sdz = startDz;
        double so = startOff;

        double edx = lookX;
        double edz = lookZ;
        double eo = 0;
        if (endExisting) {
            Candidates ce = candidates(level, end, width);
            if (ce.incompatible()) {
                return fail("Breite passt nicht zum Endpunkt");
            }
            RoadNodeBlockEntity.Arm bestArm = null;
            double best = -2;
            for (RoadNodeBlockEntity.Arm a : ce.arms()) {
                double d = a.dx() * cx0 + a.dz() * cz0;
                if (d > best) {
                    best = d;
                    bestArm = a;
                }
            }
            if (bestArm != null) {
                edx = bestArm.dx();
                edz = bestArm.dz();
                if (ce.lane()) {
                    eo = laneSide(bestArm, end, hitX, hitZ) * LANE_OFFSET;
                }
            }
        }

        // Anfangs- und Endpunkt der Mittellinie (mit Spurversatz)
        double p0x = -sdz * so;
        double p0z = sdx * so;
        double p3x = dx - edz * eo;
        double p3z = dz + edx * eo;
        double qx = p3x - p0x;
        double qz = p3z - p0z;
        double ql = Math.sqrt(qx * qx + qz * qz);
        if (ql < 1) {
            return fail("Zu kurz");
        }
        double cx = qx / ql;
        double cz = qz / ql;

        // Freie Enden werden gerade, wenn der Blick nahe an der Strecke liegt
        if (!startExisting && sdx * cx + sdz * cz > 0.9) {
            sdx = cx;
            sdz = cz;
        }
        if (!endExisting && edx * cx + edz * cz > 0.9) {
            edx = cx;
            edz = cz;
        }

        RoadCurve curve = new RoadCurve(dx, dy, dz, sdx, sdz, edx, edz, so, eo);

        if (sdx * cx + sdz * cz < 0.2 || edx * cx + edz * cz < 0.2) {
            return new Plan(false, "Blickrichtung passt nicht zur Strecke - schau in Fahrtrichtung",
                    curve, sdx, sdz, edx, edz, so, eo);
        }
        if (curve.minRadius < width / 2.0 + 0.5) {
            return new Plan(false, "Kurve zu eng", curve, sdx, sdz, edx, edz, so, eo);
        }
        return new Plan(true, "", curve, sdx, sdz, edx, edz, so, eo);
    }
}
