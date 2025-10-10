package org.jlab.utils.groups;

import org.jlab.utils.system.ClasUtilsFile;

public enum StockSchema {
    FULL    ( 0, "FULL"),
    MON     ( 10, "MON"),
    CALIB   ( 11, "CALIB"),
    DCALIGN ( 12, "DCALIGN"),
    DCHV    ( 13, "DCHV"),
    LEVEL3  ( 14, "LEVEL3"),
    TRIGGER ( 15, "TRIGGER"),
    DST     ( 100, "DST"),
    DSTHB   ( 101, "DSTHB");

    private final int schemaId;
    private final String schemaName;
    public String getName() { return schemaName; }
    public int getId() { return schemaId; }

    StockSchema(int id, String name) {
        schemaId = id;
        schemaName = name;
    }

    public String getPath() {
        return ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4/singles/"+schemaName.toLowerCase());
    }

    public static StockSchema get(String name) {
        for (StockSchema s: StockSchema.values())
            if (s.getName().equalsIgnoreCase(name)) 
                return s;
        throw new RuntimeException("Unknown StockSchema:  "+name);
    }
}