package com.sculkcrafterthefirst.motorways;

import java.io.IOException;
import java.io.InputStream;
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

    public final String id;
    private int[] size;

    MarkingType(String id) {
        this.id = id;
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(Motorways.MODID, "block/marking/" + id);
    }

    // Pixelgröße {Breite, Höhe}, direkt aus der PNG-Datei gelesen (funktioniert auch auf dem Server)
    private synchronized int[] pixelSize() {
        if (size == null) {
            int[] result = { 0, 0 };
            String path = "/assets/" + Motorways.MODID + "/textures/block/marking/" + id + ".png";
            try (InputStream in = MarkingType.class.getResourceAsStream(path)) {
                if (in != null) {
                    byte[] h = in.readNBytes(24);
                    if (h.length == 24) {
                        result[0] = readInt(h, 16);
                        result[1] = readInt(h, 20);
                    }
                }
            } catch (IOException e) {
                // Größe unbekannt, es werden Ersatzwerte benutzt
            }
            size = result;
        }
        return size;
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    public double widthBlocks() {
        int px = pixelSize()[0];
        return px > 0 ? px / (double) Config.PIXELS_PER_BLOCK.get() : 2.0;
    }

    public double lengthBlocks() {
        int px = pixelSize()[1];
        return px > 0 ? px / (double) Config.PIXELS_PER_BLOCK.get() : 3.0;
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
