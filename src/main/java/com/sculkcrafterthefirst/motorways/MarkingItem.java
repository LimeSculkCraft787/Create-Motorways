package com.sculkcrafterthefirst.motorways;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MarkingItem extends Item {
    private final MarkingType type;

    public MarkingItem(Properties properties, MarkingType type) {
        super(properties);
        this.type = type;
    }

    private static void msg(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return handle(context.getLevel(), player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(handle(level, player), player.getItemInHand(hand));
    }

    private InteractionResult handle(Level level, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Eigener Strahl gegen die Kollision, damit wir die Straßenoberfläche genau treffen
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(6.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            msg(player, "Keine Straße in Reichweite");
            return InteractionResult.SUCCESS;
        }
        RoadLocator.Hit road = RoadLocator.find(level, hit.getLocation());
        if (road == null) {
            msg(player, "Hier ist keine Straße");
            return InteractionResult.SUCCESS;
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

        double s = Math.round(road.s() * 16.0) / 16.0;
        double lat = 0.0;
        if (road.road().width == RoadPlanner.DOUBLE) {
            lat = road.lat() >= 0 ? RoadPlanner.LANE_OFFSET : -RoadPlanner.LANE_OFFSET;
        }
        boolean reverse = road.tx() * look.x + road.tz() * look.z < 0;
        List<RoadNodeBlockEntity.Marking> list = road.road().markings;

        if (player.isShiftKeyDown()) {
            int bestIdx = -1;
            double bestD = 3.0;
            for (int i = 0; i < list.size(); i++) {
                RoadNodeBlockEntity.Marking m = list.get(i);
                if (Math.abs(m.lat() - lat) > 2.0) {
                    continue;
                }
                double d = Math.abs(m.s() - road.s());
                if (d < bestD) {
                    bestD = d;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                msg(player, "Kein Pfeil in der Nähe");
            } else {
                list.remove(bestIdx);
                road.node().markingsChanged();
                msg(player, "Pfeil entfernt");
            }
        } else {
            list.add(new RoadNodeBlockEntity.Marking(type, s, lat, reverse));
            road.node().markingsChanged();
            msg(player, "Pfeil gesetzt");
        }
        return InteractionResult.SUCCESS;
    }
}
