package com.sculkcrafterthefirst.motorways;

import com.mojang.logging.LogUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class MarkingItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MarkingType type;

    public MarkingItem(Properties properties, MarkingType type) {
        super(properties);
        this.type = type;
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
        try {
            RoadLocator.Hit road = MarkingTools.pick(level, player);
            if (road == null) {
                return InteractionResult.SUCCESS;
            }

            if (player.isShiftKeyDown()) {
                if (MarkingTools.removeAt(road)) {
                    MarkingTools.msg(player, "Pfeil entfernt");
                } else {
                    MarkingTools.msg(player, "Kein Pfeil an dieser Stelle");
                }
                return InteractionResult.SUCCESS;
            }

            // Setzen: auf 1 Pixel gerastet, Spurmitte bei der Zweispur
            Vec3 look = player.getLookAngle();
            double s = Math.round(road.s() * 16.0) / 16.0;
            double lat = 0.0;
            if (road.road().width == RoadPlanner.DOUBLE) {
                lat = road.lat() >= 0 ? RoadPlanner.LANE_OFFSET : -RoadPlanner.LANE_OFFSET;
            }
            boolean reverse = road.tx() * look.x + road.tz() * look.z < 0;
            road.road().markings.add(new RoadNodeBlockEntity.Marking(type, s, lat, reverse));
            road.node().markingsChanged();
            MarkingTools.msg(player, "Pfeil gesetzt");
        } catch (Exception e) {
            LOGGER.error("Fehler beim Bearbeiten einer Markierung", e);
            MarkingTools.msg(player, "Fehler: " + e);
        }
        return InteractionResult.SUCCESS;
    }
}
