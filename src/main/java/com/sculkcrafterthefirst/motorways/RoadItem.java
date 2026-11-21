package com.sculkcrafterthefirst.motorways;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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

    // Löscht nur den gemerkten Startpunkt, der Modus bleibt erhalten
    private static void clearStart(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove("StartX");
        tag.remove("StartY");
        tag.remove("StartZ");
        tag.remove("DirX");
        tag.remove("DirZ");
        tag.remove("StartOff");
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        boolean straight = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean("Straight");
        tooltip.add(Component.literal(straight ? "Modus: gerade Straßen" : "Modus: Kurven"));
        tooltip.add(Component.literal("Rechtsklick in die Luft: Modus wechseln"));
    }

    // Rechtsklick in die Luft: zwischen Kurven und geraden Straßen wechseln
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            boolean straight = !tag.getBoolean("Straight");
            tag.putBoolean("Straight", straight);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            msg(player, straight ? "Modus: gerade Straßen"
                    : "Modus: Kurven (die Blickrichtung bestimmt die Krümmung)");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
            clearStart(stack);
            msg(player, "Startpunkt gelöscht");
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        boolean onNode = level.getBlockState(clicked).is(ModBlocks.ROAD_NODE.get());
        BlockPos node = onNode ? clicked : clicked.above();
        double[] look = snappedLook(player);
        Vec3 loc = context.getClickLocation();

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean curves = !tag.getBoolean("Straight");

        if (!tag.contains("StartX")) {
            // erster Klick
            if (!onNode && !level.getBlockState(node).canBeReplaced()) {
                msg(player, "Hier ist kein Platz");
                return InteractionResult.SUCCESS;
            }
            RoadPlanner.Anchor anchor = RoadPlanner.startAnchor(level, width, node,
                    look[0], look[1], loc.x, loc.z);
            if (!anchor.ok()) {
                msg(player, anchor.message());
                return InteractionResult.SUCCESS;
            }
            tag.putInt("StartX", node.getX());
            tag.putInt("StartY", node.getY());
            tag.putInt("StartZ", node.getZ());
            tag.putDouble("DirX", anchor.dx());
            tag.putDouble("DirZ", anchor.dz());
            tag.putDouble("StartOff", anchor.offset());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            msg(player, "Startpunkt gesetzt: " + node.getX() + " " + node.getY() + " " + node.getZ()
                    + " (Breite " + width + (anchor.offset() != 0 ? ", Spur der Doppelstraße" : "")
                    + ", " + (curves ? "Kurvenmodus" : "gerader Modus") + ")");
            return InteractionResult.SUCCESS;
        }

        // zweiter Klick
        BlockPos startPos = new BlockPos(tag.getInt("StartX"), tag.getInt("StartY"), tag.getInt("StartZ"));
        RoadPlanner.Plan plan = RoadPlanner.plan(level, width, startPos,
                tag.getDouble("DirX"), tag.getDouble("DirZ"), tag.getDouble("StartOff"),
                node, look[0], look[1], loc.x, loc.z, curves);
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
            startBe.addRoad(node, width, plan.sdx(), plan.sdz(), plan.edx(), plan.edz(),
                    plan.so(), plan.eo());
        }
        if (level.getBlockEntity(node) instanceof RoadNodeBlockEntity endBe) {
            endBe.addIncoming(startPos);
        }
        RoadCollision.place(level, startPos, plan.curve(), width);

        msg(player, "Straße gesetzt, Länge " + String.format("%.1f", plan.curve().length)
                + " Blöcke (" + plan.message() + ")");
        clearStart(stack);
        return InteractionResult.SUCCESS;
    }
}
