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
    private static final Map<UUID, BlockPos> STARTS = new HashMap<>();
    private static final int MAX_LENGTH = 32;
    private final int width;

    public RoadItem(Properties properties, int width) {
        super(properties);
        this.width = width;
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

        BlockPos start = STARTS.get(id);
        if (start == null) {
            STARTS.put(id, node);
            player.displayClientMessage(Component.literal("Startpunkt gesetzt: "
                    + node.getX() + " " + node.getY() + " " + node.getZ()
                    + " (Breite " + width + ")"), true);
            return InteractionResult.SUCCESS;
        }

        double dist = Math.sqrt(start.distSqr(node));
        if (start.equals(node)) {
            player.displayClientMessage(Component.literal("Start und Ende sind gleich"), true);
        } else if (dist > MAX_LENGTH) {
            player.displayClientMessage(Component.literal("Zu weit entfernt (max. "
                    + MAX_LENGTH + " Blöcke)"), true);
        } else if (!level.getBlockState(start).canBeReplaced()) {
            player.displayClientMessage(Component.literal("Der Startpunkt ist blockiert"), true);
            STARTS.remove(id);
        } else {
            level.setBlock(start, ModBlocks.ROAD_NODE.get().defaultBlockState(), 3);
            level.setBlock(node, ModBlocks.ROAD_NODE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(start) instanceof RoadNodeBlockEntity be) {
                be.setConnection(node, width);
            }
            player.displayClientMessage(Component.literal("Straße gesetzt, Länge "
                    + String.format("%.1f", dist) + " Blöcke"), true);
            STARTS.remove(id);
        }
        return InteractionResult.SUCCESS;
    }
}
