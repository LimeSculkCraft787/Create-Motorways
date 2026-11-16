package com.sculkcrafterthefirst.motorways;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.IntValue MAX_ROAD_LENGTH;
    public static final ModConfigSpec.IntValue PIXELS_PER_BLOCK;
    public static final ModConfigSpec.IntValue LINE_WIDTH_PIXELS;
    public static final ModConfigSpec.DoubleValue DASH_LENGTH;
    public static final ModConfigSpec.DoubleValue DASH_GAP;
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
        LINE_WIDTH_PIXELS = server
                .comment("Breite der Mittellinien in Texturpixeln (bei 96 Pixeln pro Block sind 12 ca. 12 cm)")
                .defineInRange("lineWidthPixels", 12, 1, 192);
        DASH_LENGTH = server
                .comment("Länge eines Strichs bei gestrichelten Linien in Blöcken")
                .defineInRange("dashLength", 4.0, 0.1, 64.0);
        DASH_GAP = server
                .comment("Länge der Lücke bei gestrichelten Linien in Blöcken")
                .defineInRange("dashGap", 8.0, 0.1, 64.0);
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
