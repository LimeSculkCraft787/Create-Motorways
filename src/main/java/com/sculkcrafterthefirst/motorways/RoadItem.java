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
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos clicked = context.getClickedPos();
        UUID id = player.getUUID();

        if (player.isShiftKeyDown()) {
            STARTS.remove(id);
            player.displayClientMessage(Component.literal("Startpunkt gelöscht"), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos start = STARTS.get(id);
        if (start == null) {
            STARTS.put(id, clicked);
            player.displayClientMessage(Component.literal("Startpunkt gesetzt: "
                    + clicked.getX() + " " + clicked.getY() + " " + clicked.getZ()
                    + " (Breite " + width + ")"), true);
        } else {
            double dist = Math.sqrt(start.distSqr(clicked));
            if (dist > MAX_LENGTH) {
                player.displayClientMessage(Component.literal("Zu weit entfernt (max. "
                        + MAX_LENGTH + " Blöcke)"), true);
            } else {
                player.displayClientMessage(Component.literal("Straße von "
                        + start.getX() + " " + start.getY() + " " + start.getZ()
                        + " nach " + clicked.getX() + " " + clicked.getY() + " " + clicked.getZ()
                        + ", Länge " + String.format("%.1f", dist) + " Blöcke"), true);
                STARTS.remove(id);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
