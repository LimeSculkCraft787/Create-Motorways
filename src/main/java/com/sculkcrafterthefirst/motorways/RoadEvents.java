package com.sculkcrafterthefirst.motorways;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Motorways.MODID)
public class RoadEvents {
    // Die unsichtbaren Kollisionsblöcke lassen sich nicht von Hand abbauen
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(ModBlocks.ROAD_SEGMENT.get())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(
                    Component.literal("Straße über einen ihrer Knoten abbauen"), true);
        }
    }
}
