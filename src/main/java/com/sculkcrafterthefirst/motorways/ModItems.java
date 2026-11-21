package com.sculkcrafterthefirst.motorways;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Motorways.MODID);

    public static final DeferredItem<Item> SINGLE_ROAD = ITEMS.register("single_road",
            () -> new RoadItem(new Item.Properties(), 5));
    public static final DeferredItem<Item> DOUBLE_ROAD = ITEMS.register("double_road",
            () -> new RoadItem(new Item.Properties(), 11));
    public static final DeferredItem<Item> ERASER = ITEMS.register("marking_eraser",
            () -> new EraserItem(new Item.Properties().stacksTo(1)));

    public static final Map<MarkingType, DeferredItem<Item>> MARKINGS = new EnumMap<>(MarkingType.class);
    public static final Map<LineType, DeferredItem<Item>> LINES = new EnumMap<>(LineType.class);
    public static final Map<LineType, DeferredItem<Item>> EDGE_LINES = new EnumMap<>(LineType.class);

    static {
        for (MarkingType type : MarkingType.values()) {
            MARKINGS.put(type, ITEMS.register(type.id,
                    () -> new MarkingItem(new Item.Properties(), type)));
        }
        for (LineType type : LineType.values()) {
            LINES.put(type, ITEMS.register(type.id,
                    () -> new LineItem(new Item.Properties(), type, false)));
            EDGE_LINES.put(type, ITEMS.register("edge_" + type.id,
                    () -> new LineItem(new Item.Properties(), type, true)));
        }
    }
}
