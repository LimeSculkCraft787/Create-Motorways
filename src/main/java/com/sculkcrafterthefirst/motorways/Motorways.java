package com.sculkcrafterthefirst.motorways;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

@Mod(Motorways.MODID)
public class Motorways {
    public static final String MODID = "motorways";

    public Motorways(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SINGLE_ROAD);
            event.accept(ModItems.DOUBLE_ROAD);
            event.accept(ModItems.ERASER);
            for (DeferredItem<Item> item : ModItems.MARKINGS.values()) {
                event.accept(item);
            }
            for (DeferredItem<Item> item : ModItems.LINES.values()) {
                event.accept(item);
            }
        }
    }
}
