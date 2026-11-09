package com.sculkcrafterthefirst.motorways;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class RoadItem extends Item {
    private record Pending(BlockPos pos, double dx, double dz) {
    }

    private static final Map<UUID, Pending> STARTS = new HashMap<>();
    private static final int MAX_LENGTH = 32;
    private final int width;

    public RoadItem(Properties properties, int width) {
        super(properties);
        this.width = width;
    }

    // Blickrichtung, auf 45 Grad gerundet
    private static double[] snappedLook(Player player) {
        double angle = Math.atan2(player.getLookAngle().z, player.getLookAngle().x);
        double step = Math.PI / 4;
        double snapped = Math.round(angle / step) * step;
        return new double[] { Math.cos(snapped), Math.sin(snapped) };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        UUID id = player.getUUID();

        if (player.isShiftKeyDown()) {
            STARTS.remove(id);
            player.displayClientMessage(Component.literal("Startpunkt gelöscht"), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos node = context.getClickedPos().above();
        if (!level.getBlockState(node).canBeReplaced()) {
            player.displayClientMessage(Component.literal("Hier ist kein Platz"), true);
            return InteractionResult.SUCCESS;
        }

        double[] look = snappedLook(player);
        Pending start = STARTS.get(id);
        if (start == null) {
            STARTS.put(id, new Pending(node, look[0], look[1]));
            player.displayClientMessage(Component.literal("Startpunkt gesetzt: "
                    + node.getX() + " " + node.getY() + " " + node.getZ()
                    + " (Breite " + width + ")"), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos startPos = start.pos();
        double dx = node.getX() - startPos.getX();
        double dy = node.getY() - startPos.getY();
        double dz = node.getZ() - startPos.getZ();
        double chord = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.sqrt(startPos.distSqr(node));

        if (startPos.equals(node)) {
            player.displayClientMessage(Component.literal("Start und Ende sind gleich"), true);
            return InteractionResult.SUCCESS;
        }
        if (chord < 2) {
            player.displayClientMessage(Component.literal("Zu kurz"), true);
            return InteractionResult.SUCCESS;
        }
        if (dist > MAX_LENGTH) {
            player.displayClientMessage(Component.literal("Zu weit entfernt (max. "
                    + MAX_LENGTH + " Blöcke)"), true);
            return InteractionResult.SUCCESS;
        }
        if (!level.getBlockState(startPos).canBeReplaced()) {
            player.displayClientMessage(Component.literal("Der Startpunkt ist blockiert"), true);
            STARTS.remove(id);
            return InteractionResult.SUCCESS;
        }

        double cx = dx / chord;
        double cz = dz / chord;
        double sdx = start.dx();
        double sdz = start.dz();
        double edx = look[0];
        double edz = look[1];

        // Liegt der Blick nahe an der Strecke, wird sie gerade
        if (sdx * cx + sdz * cz > 0.9) {
            sdx = cx;
            sdz = cz;
        }
        if (edx * cx + edz * cz > 0.9) {
            edx = cx;
            edz = cz;
        }

        if (sdx * cx + sdz * cz < 0.2 || edx * cx + edz * cz < 0.2) {
            player.displayClientMessage(Component.literal(
                    "Blickrichtung passt nicht zur Strecke - schau in Fahrtrichtung"), true);
            return InteractionResult.SUCCESS;
        }

        RoadCurve curve = new RoadCurve(dx, dy, dz, sdx, sdz, edx, edz);
        if (curve.minRadius < width / 2.0 + 0.5) {
            player.displayClientMessage(Component.literal("Kurve zu eng"), true);
            return InteractionResult.SUCCESS;
        }

        level.setBlock(startPos, ModBlocks.ROAD_NODE.get().defaultBlockState(), 3);
        level.setBlock(node, ModBlocks.ROAD_NODE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(startPos) instanceof RoadNodeBlockEntity be) {
            be.setConnection(node, width, sdx, sdz, edx, edz);
            be.setPartner(node);
        }
        if (level.getBlockEntity(node) instanceof RoadNodeBlockEntity endBe) {
            endBe.setPartner(startPos);
        }
        RoadCollision.place(level, startPos, curve, width);
        player.displayClientMessage(Component.literal("Straße gesetzt, Länge "
                + String.format("%.1f", curve.length) + " Blöcke"), true);
        STARTS.remove(id);
        return InteractionResult.SUCCESS;
    }
}
