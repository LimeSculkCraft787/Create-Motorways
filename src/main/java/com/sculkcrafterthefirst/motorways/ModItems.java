package com.sculkcrafterthefirst.motorways;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Motorways.MODID);

    public static final DeferredItem<Item> SINGLE_ROAD = ITEMS.register("single_road",
            () -> new RoadItem(new Item.Properties(), 5));
    public static final DeferredItem<Item> DOUBLE_ROAD = ITEMS.register("double_road",
            () -> new RoadItem(new Item.Properties(), 11));
}
