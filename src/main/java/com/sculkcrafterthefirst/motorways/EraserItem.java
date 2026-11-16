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
import org.slf4j.Logger;

public class EraserItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public EraserItem(Properties properties) {
        super(properties);
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
            if (road != null) {
                if (MarkingTools.removeAt(road)) {
                    MarkingTools.msg(player, "Markierung entfernt");
                } else {
                    MarkingTools.msg(player, "Keine Markierung an dieser Stelle");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Fehler beim Entfernen einer Markierung", e);
            MarkingTools.msg(player, "Fehler: " + e);
        }
        return InteractionResult.SUCCESS;
    }
}
