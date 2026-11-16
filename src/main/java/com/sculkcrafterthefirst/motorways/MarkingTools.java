package com.sculkcrafterthefirst.motorways;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MarkingTools {

    public static void msg(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    // Findet die Stelle der Straße, auf die der Spieler zielt (sonst null, mit Meldung)
    public static RoadLocator.Hit pick(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(6.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            msg(player, "Keine Straße in Reichweite");
            return null;
        }
        RoadLocator.Hit road = RoadLocator.find(level, hit.getLocation());
        if (road == null) {
            msg(player, "Hier ist keine Straße");
            return null;
        }

        // Treffpunkt auf der Oberfläche der Straße genauer bestimmen
        if (look.y < -0.01) {
            double t = (road.top() - eye.y) / look.y;
            Vec3 q = eye.add(look.scale(t));
            RoadLocator.Hit refined = RoadLocator.find(level, q);
            if (refined != null && refined.road() == road.road()) {
                road = refined;
            }
        }
        return road;
    }

    // Entfernt die Markierung (Pfeil oder Linie) an der getroffenen Stelle
    public static boolean removeAt(RoadLocator.Hit road) {
        if (removeArrowAt(road)) {
            return true;
        }
        return removeLineAt(road);
    }

    private static boolean removeArrowAt(RoadLocator.Hit road) {
        List<RoadNodeBlockEntity.Marking> list = road.road().markings;
        double roadLen = road.road().curve(road.node().getBlockPos()).length;
        int bestIdx = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            RoadNodeBlockEntity.Marking m = list.get(i);
            double len = m.type().lengthBlocks();
            double halfLen = len / 2.0 + 0.3;
            double halfWidth = m.type().widthBlocks() / 2.0 + 0.3;
            double center = m.s();
            if (roadLen > len) {
                center = Math.max(len / 2.0, Math.min(roadLen - len / 2.0, center));
            }
            double ds = Math.abs(center - road.s());
            double dl = Math.abs(m.lat() - road.lat());
            if (ds > halfLen || dl > halfWidth) {
                continue;
            }
            double score = ds / halfLen + dl / halfWidth;
            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return false;
        }
        list.remove(bestIdx);
        road.node().markingsChanged();
        return true;
    }

    // Linien liegen in der Straßenmitte, getroffen wird in einem schmalen Bereich darum
    private static boolean removeLineAt(RoadLocator.Hit road) {
        if (Math.abs(road.lat()) > 0.6) {
            return false;
        }
        List<RoadNodeBlockEntity.Line> lines = road.road().lines;
        for (int i = 0; i < lines.size(); i++) {
            RoadNodeBlockEntity.Line l = lines.get(i);
            if (road.s() >= l.s0() - 0.1 && road.s() <= l.s1() + 0.1) {
                lines.remove(i);
                road.node().markingsChanged();
                return true;
            }
        }
        return false;
    }

    // Fügt eine Linie ein und überschreibt dabei überlappende Teile bestehender Linien
    public static void addLine(List<RoadNodeBlockEntity.Line> lines, RoadNodeBlockEntity.Line n) {
        List<RoadNodeBlockEntity.Line> result = new ArrayList<>();
        for (RoadNodeBlockEntity.Line l : lines) {
            if (l.s1() <= n.s0() || l.s0() >= n.s1()) {
                result.add(l);
                continue;
            }
            if (l.s0() < n.s0()) {
                result.add(new RoadNodeBlockEntity.Line(l.type(), l.s0(), n.s0()));
            }
            if (l.s1() > n.s1()) {
                result.add(new RoadNodeBlockEntity.Line(l.type(), n.s1(), l.s1()));
            }
        }
        result.add(n);
        lines.clear();
        lines.addAll(result);
    }
}
