package com.sculkcrafterthefirst.motorways;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

// Sucht den kürzesten Weg über verbundene Zweispur-Straßen und legt Linien darüber
public class RoadGraph {

    // Ein vollständig durchlaufenes Straßenstück; forward = vom Besitzerknoten zum Zielknoten
    public record Leg(RoadNodeBlockEntity owner, RoadNodeBlockEntity.Road road, boolean forward) {
    }

    public record Route(boolean exitAtOwner, List<Leg> legs, boolean enterAtOwner) {
    }

    private record Edge(RoadNodeBlockEntity.Road road, RoadNodeBlockEntity owner,
                        BlockPos from, BlockPos to, boolean forward, double length) {
    }

    private record State(BlockPos pos, double dist) {
    }

    private static RoadNodeBlockEntity node(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof RoadNodeBlockEntity n ? n : null;
    }

    private static boolean usable(RoadNodeBlockEntity.Road r) {
        return r.width == RoadPlanner.DOUBLE && r.so == 0 && r.eo == 0;
    }

    private static List<Edge> edges(Level level, BlockPos x) {
        List<Edge> result = new ArrayList<>();
        RoadNodeBlockEntity xb = node(level, x);
        if (xb == null) {
            return result;
        }
        for (RoadNodeBlockEntity.Road r : xb.getRoads()) {
            if (usable(r)) {
                result.add(new Edge(r, xb, x, r.target, true, r.curve(x).length));
            }
        }
        for (BlockPos s : xb.getIncoming()) {
            RoadNodeBlockEntity sb = node(level, s);
            if (sb == null) {
                continue;
            }
            for (RoadNodeBlockEntity.Road r : sb.getRoads()) {
                if (r.target.equals(x) && usable(r)) {
                    result.add(new Edge(r, sb, x, s, false, r.curve(s).length));
                }
            }
        }
        return result;
    }

    // Weg von Punkt aS auf Straße aRoad zu Punkt bS auf Straße bRoad (null, wenn nicht verbunden)
    public static Route find(Level level, RoadNodeBlockEntity aNode, RoadNodeBlockEntity.Road aRoad, double aS,
                             RoadNodeBlockEntity bNode, RoadNodeBlockEntity.Road bRoad, double bS) {
        BlockPos a1 = aNode.getBlockPos();
        BlockPos b1 = aRoad.target;
        BlockPos a2 = bNode.getBlockPos();
        BlockPos b2 = bRoad.target;
        double len1 = aRoad.curve(a1).length;
        double len2 = bRoad.curve(a2).length;

        Map<BlockPos, Double> dist = new HashMap<>();
        Map<BlockPos, Edge> via = new HashMap<>();
        PriorityQueue<State> queue = new PriorityQueue<>(Comparator.comparingDouble(State::dist));
        dist.put(a1, aS);
        dist.put(b1, len1 - aS);
        queue.add(new State(a1, aS));
        queue.add(new State(b1, len1 - aS));

        int settled = 0;
        while (!queue.isEmpty()) {
            State st = queue.poll();
            if (st.dist() > dist.getOrDefault(st.pos(), Double.MAX_VALUE)) {
                continue;
            }
            if (++settled > 400) {
                break;
            }
            for (Edge e : edges(level, st.pos())) {
                if (e.road() == aRoad || e.road() == bRoad) {
                    continue;
                }
                double nd = st.dist() + e.length();
                if (nd < dist.getOrDefault(e.to(), Double.MAX_VALUE)) {
                    dist.put(e.to(), nd);
                    via.put(e.to(), e);
                    queue.add(new State(e.to(), nd));
                }
            }
        }

        double costOwner = dist.getOrDefault(a2, Double.MAX_VALUE) + bS;
        double costTarget = dist.getOrDefault(b2, Double.MAX_VALUE) + (len2 - bS);
        if (costOwner == Double.MAX_VALUE && costTarget == Double.MAX_VALUE) {
            return null;
        }
        boolean enterAtOwner = costOwner <= costTarget;
        BlockPos end = enterAtOwner ? a2 : b2;

        List<Leg> legs = new ArrayList<>();
        BlockPos cur = end;
        Edge e = via.get(cur);
        while (e != null) {
            legs.add(0, new Leg(e.owner(), e.road(), e.forward()));
            cur = e.from();
            e = via.get(cur);
        }
        boolean exitAtOwner = cur.equals(a1);
        return new Route(exitAtOwner, legs, enterAtOwner);
    }

    private static void add(RoadNodeBlockEntity node, RoadNodeBlockEntity.Road road,
                            RoadNodeBlockEntity.Line line) {
        if (line.s1() - line.s0() < 1e-6) {
            return;
        }
        MarkingTools.addLine(road.lines, line);
        node.markingsChanged();
    }

    // Legt die Linie über die ganze Strecke; gibt die Gesamtlänge zurück
    public static double apply(LineType type, Route route,
                               RoadNodeBlockEntity aNode, RoadNodeBlockEntity.Road aRoad, double aS,
                               RoadNodeBlockEntity bNode, RoadNodeBlockEntity.Road bRoad, double bS) {
        double len1 = aRoad.curve(aNode.getBlockPos()).length;
        double len2 = bRoad.curve(bNode.getBlockPos()).length;
        double traveled;

        // Erste Straße: vom Klickpunkt bis zum Knoten
        if (route.exitAtOwner()) {
            add(aNode, aRoad, new RoadNodeBlockEntity.Line(type, 0, aS, 0, true));
            traveled = aS;
        } else {
            add(aNode, aRoad, new RoadNodeBlockEntity.Line(type, aS, len1, 0, false));
            traveled = len1 - aS;
        }

        // Straßen dazwischen, komplett
        for (Leg leg : route.legs()) {
            double len = leg.road().curve(leg.owner().getBlockPos()).length;
            add(leg.owner(), leg.road(), new RoadNodeBlockEntity.Line(type, 0, len, traveled, !leg.forward()));
            traveled += len;
        }

        // Letzte Straße: vom Knoten bis zum Klickpunkt
        if (route.enterAtOwner()) {
            add(bNode, bRoad, new RoadNodeBlockEntity.Line(type, 0, bS, traveled, false));
            traveled += bS;
        } else {
            add(bNode, bRoad, new RoadNodeBlockEntity.Line(type, bS, len2, traveled, true));
            traveled += len2 - bS;
        }
        return traveled;
    }
}
