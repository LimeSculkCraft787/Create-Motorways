package com.sculkcrafterthefirst.motorways;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class RoadItem extends Item {
    private final int width;

    public RoadItem(Properties properties, int width) {
        super(properties);
        this.width = width;
    }

    public int getWidth() {
        return width;
    }

    // Blickrichtung, auf 45 Grad gerundet
    public static double[] snappedLook(Player player) {
        double angle = Math.atan2(player.getLookAngle().z, player.getLookAngle().x);
        double step = Math.PI / 4;
        double snapped = Math.round(angle / step) * step;
        return new double[] { Math.cos(snapped), Math.sin(snapped) };
    }

    private static void msg(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
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
        ItemStack stack = context.getItemInHand();

        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            msg(player, "Startpunkt gelöscht");
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        boolean onNode = level.getBlockState(clicked).is(ModBlocks.ROAD_NODE.get());
        BlockPos node = onNode ? clicked : clicked.above();
        double[] look = snappedLook(player);

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (!tag.contains("StartX")) {
            // erster Klick
            if (!onNode && !level.getBlockState(node).canBeReplaced()) {
                msg(player, "Hier ist kein Platz");
                return InteractionResult.SUCCESS;
            }
            int nodeWidth = RoadPlanner.nodeWidth(level, node);
            if (nodeWidth != 0 && nodeWidth != width) {
                msg(player, "Breite passt nicht zur bestehenden Straße");
                return InteractionResult.SUCCESS;
            }
            double[] dir = RoadPlanner.startDirection(level, node, look[0], look[1]);
            CompoundTag t = new CompoundTag();
            t.putInt("StartX", node.getX());
            t.putInt("StartY", node.getY());
            t.putInt("StartZ", node.getZ());
            t.putDouble("DirX", dir[0]);
            t.putDouble("DirZ", dir[1]);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            msg(player, "Startpunkt gesetzt: " + node.getX() + " " + node.getY() + " " + node.getZ()
                    + " (Breite " + width + ")");
            return InteractionResult.SUCCESS;
        }

        // zweiter Klick
        BlockPos startPos = new BlockPos(tag.getInt("StartX"), tag.getInt("StartY"), tag.getInt("StartZ"));
        RoadPlanner.Plan plan = RoadPlanner.plan(level, width, startPos,
                tag.getDouble("DirX"), tag.getDouble("DirZ"), node, look[0], look[1]);
        if (!plan.ok()) {
            msg(player, plan.message());
            return InteractionResult.SUCCESS;
        }

        if (!level.getBlockState(startPos).is(ModBlocks.ROAD_NODE.get())) {
            level.setBlock(startPos, ModBlocks.ROAD_NODE.get().defaultBlockState(), 3);
        }
        if (!level.getBlockState(node).is(ModBlocks.ROAD_NODE.get())) {
            level.setBlock(node, ModBlocks.ROAD_NODE.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(startPos) instanceof RoadNodeBlockEntity startBe) {
            startBe.addRoad(node, width, plan.sdx(), plan.sdz(), plan.edx(), plan.edz());
        }
        if (level.getBlockEntity(node) instanceof RoadNodeBlockEntity endBe) {
            endBe.addIncoming(startPos);
        }
        RoadCollision.place(level, startPos, plan.curve(), width);

        msg(player, "Straße gesetzt, Länge " + String.format("%.1f", plan.curve().length) + " Blöcke");
        stack.remove(DataComponents.CUSTOM_DATA);
        return InteractionResult.SUCCESS;
    }
}
