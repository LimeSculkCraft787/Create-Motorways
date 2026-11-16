package com.sculkcrafterthefirst.motorways;

public enum LineType {
    SOLID("line_solid"),
    DASHED("line_dashed");

    public final String id;

    LineType(String id) {
        this.id = id;
    }

    public static LineType byId(String id) {
        for (LineType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
