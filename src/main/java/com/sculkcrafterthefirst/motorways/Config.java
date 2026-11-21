package com.sculkcrafterthefirst.motorways;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.IntValue MAX_ROAD_LENGTH;
    public static final ModConfigSpec.IntValue PIXELS_PER_BLOCK;
    public static final ModConfigSpec.IntValue LINE_WIDTH_PIXELS;
    public static final ModConfigSpec.DoubleValue DASH_LENGTH;
    public static final ModConfigSpec.DoubleValue DASH_GAP;
    public static final ModConfigSpec.IntValue EDGE_LINE_WIDTH_PIXELS;
    public static final ModConfigSpec.IntValue EDGE_INSET_PIXELS;
    public static final ModConfigSpec.DoubleValue EDGE_DASH_LENGTH;
    public static final ModConfigSpec.DoubleValue EDGE_DASH_GAP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TRACK_BLOCKS;
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
                .comment("Länge eines Strichs bei gestrichelten Mittellinien in Blöcken")
                .defineInRange("dashLength", 4.0, 0.1, 64.0);
        DASH_GAP = server
                .comment("Länge der Lücke bei gestrichelten Mittellinien in Blöcken")
                .defineInRange("dashGap", 8.0, 0.1, 64.0);
        EDGE_LINE_WIDTH_PIXELS = server
                .comment("Breite der Randlinien in Texturpixeln")
                .defineInRange("edgeLineWidthPixels", 12, 1, 192);
        EDGE_INSET_PIXELS = server
                .comment("Abstand der Randlinien vom Straßenrand (bis zur Außenkante der Linie) in Texturpixeln")
                .defineInRange("edgeInsetPixels", 24, 0, 480);
        EDGE_DASH_LENGTH = server
                .comment("Länge eines Strichs bei gestrichelten Randlinien in Blöcken")
                .defineInRange("edgeDashLength", 1.0, 0.1, 64.0);
        EDGE_DASH_GAP = server
                .comment("Länge der Lücke bei gestrichelten Randlinien in Blöcken")
                .defineInRange("edgeDashGap", 1.0, 0.1, 64.0);
        TRACK_BLOCKS = server
                .comment("Blöcke (z. B. Create-Schienen), deren Kollision im Bereich einer Straße durch die "
                        + "Straßenkollision ersetzt wird. Leere Liste = Funktion aus. Änderung erst nach Neustart.")
                .defineListAllowEmpty("trackBlocks",
                        List.of("create:track", "create:fake_track", "create:track_station",
                                "create:track_signal", "create:track_observer"),
                        () -> "create:track", o -> o instanceof String);
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
