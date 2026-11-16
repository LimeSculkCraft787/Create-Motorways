package com.sculkcrafterthefirst.motorways;

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

    // Entfernt die Markierung, auf deren Fläche der Treffer liegt
    public static boolean removeAt(RoadLocator.Hit road) {
        java.util.List<RoadNodeBlockEntity.Marking> list = road.road().markings;
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
}
