package com.sculkcrafterthefirst.motorways;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.IntValue MAX_ROAD_LENGTH;
    public static final ModConfigSpec.IntValue PIXELS_PER_BLOCK;
    public static final ModConfigSpec.IntValue ROAD_VIEW_DISTANCE;
    public static final ModConfigSpec.IntValue NODE_HIGHLIGHT_CHUNKS;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        MAX_ROAD_LENGTH = server
                .comment("Maximale Länge einer Straße in Blöcken (Luftlinie zwischen Start und Ende)")
                .defineInRange("maxRoadLength", 64, 4, 256);
        PIXELS_PER_BLOCK = server
                .comment("Texturpixel pro Block bei den Markierungen (Pfeile). 16 = Standard-Minecraft-Auflösung")
                .defineInRange("pixelsPerBlock", 96, 1, 1024);
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        ROAD_VIEW_DISTANCE = client
                .comment("Bis zu dieser Entfernung (in Blöcken) werden Straßen gezeichnet")
                .defineInRange("roadViewDistance", 128, 32, 512);
        NODE_HIGHLIGHT_CHUNKS = client
                .comment("Radius in Chunks, in dem Knoten hervorgehoben werden, wenn man ein Straßen-Item hält")
                .defineInRange("nodeHighlightChunks", 4, 1, 12);
        CLIENT_SPEC = client.build();
    }
}
