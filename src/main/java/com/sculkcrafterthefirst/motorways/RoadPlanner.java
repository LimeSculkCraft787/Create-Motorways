package com.sculkcrafterthefirst.motorways;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class RoadPlanner {
    public static final int MAX_LENGTH = 32;

    public record Plan(boolean ok, String message, RoadCurve curve,
                       double sdx, double sdz, double edx, double edz) {
    }

    private static Plan fail(String message) {
        return new Plan(false, message, null, 0, 0, 0, 0);
    }

    public static int nodeWidth(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RoadNodeBlockEntity be) {
            return be.getRoadWidth();
        }
        return 0;
    }

    public static List<double[]> nodeArms(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RoadNodeBlockEntity be) {
            return be.getArms();
        }
        return new ArrayList<>();
    }

    // Richtung, in die eine neue Straße von diesem Punkt aus startet
    public static double[] startDirection(Level level, BlockPos node, double lookX, double lookZ) {
        double best = -2;
        double[] result = null;
        for (double[] arm : nodeArms(level, node)) {
            double ox = -arm[0];
            double oz = -arm[1];
            double d = ox * lookX + oz * lookZ;
            if (d > best) {
                best = d;
                result = new double[] { ox, oz };
            }
        }
        if (result != null) {
            return result;
        }
        return new double[] { lookX, lookZ };
    }

    public static Plan plan(Level level, int width, BlockPos start, double startDx, double startDz,
                            BlockPos end, double lookX, double lookZ) {
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

        int sw = nodeWidth(level, start);
        if (sw != 0 && sw != width) {
            return fail("Breite passt nicht zum Startpunkt");
        }
        int ew = nodeWidth(level, end);
        if (ew != 0 && ew != width) {
            return fail("Breite passt nicht zum Endpunkt");
        }

        if (level.getBlockEntity(start) instanceof RoadNodeBlockEntity sb && sb.hasRoadTo(end)) {
            return fail("Diese Verbindung gibt es schon");
        }
        if (level.getBlockEntity(end) instanceof RoadNodeBlockEntity eb && eb.hasRoadTo(start)) {
            return fail("Diese Verbindung gibt es schon");
        }

        double cx = dx / chord;
        double cz = dz / chord;

        double sdx = startDx;
        double sdz = startDz;
        if (!startExisting && sdx * cx + sdz * cz > 0.9) {
            sdx = cx;
            sdz = cz;
        }

        double edx = lookX;
        double edz = lookZ;
        if (endExisting) {
            double best = -2;
            for (double[] arm : nodeArms(level, end)) {
                double d = arm[0] * cx + arm[1] * cz;
                if (d > best) {
                    best = d;
                    edx = arm[0];
                    edz = arm[1];
                }
            }
        } else if (edx * cx + edz * cz > 0.9) {
            edx = cx;
            edz = cz;
        }

        RoadCurve curve = new RoadCurve(dx, dy, dz, sdx, sdz, edx, edz);

        if (sdx * cx + sdz * cz < 0.2 || edx * cx + edz * cz < 0.2) {
            return new Plan(false, "Blickrichtung passt nicht zur Strecke - schau in Fahrtrichtung",
                    curve, sdx, sdz, edx, edz);
        }
        if (curve.minRadius < width / 2.0 + 0.5) {
            return new Plan(false, "Kurve zu eng", curve, sdx, sdz, edx, edz);
        }
        return new Plan(true, "", curve, sdx, sdz, edx, edz);
    }
}
