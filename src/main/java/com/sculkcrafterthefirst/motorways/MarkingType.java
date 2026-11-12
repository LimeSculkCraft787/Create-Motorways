package com.sculkcrafterthefirst.motorways;

import net.minecraft.resources.ResourceLocation;

public enum MarkingType {
    ONWARDS("arrow_onwards"),
    LEFT("arrow_left"),
    RIGHT("arrow_right"),
    LEFT_RIGHT("arrow_left_right"),
    ONWARDS_LEFT("arrow_onwards_left"),
    ONWARDS_RIGHT("arrow_onwards_right"),
    LEFT_STRETCHED("arrow_left_stretched"),
    RIGHT_STRETCHED("arrow_right_stretched");

    // So viele Texturpixel sind ein Block
    public static final int PIXELS_PER_BLOCK = 96;

    public final String id;

    MarkingType(String id) {
        this.id = id;
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(Motorways.MODID, "block/marking/" + id);
    }

    public static MarkingType byId(String id) {
        for (MarkingType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
