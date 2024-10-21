package org.jlab.utils.groups;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NamedEntry extends IndexedTable.IndexedEntry {

    Map<String,Integer> entryNames = new HashMap<>();

    public NamedEntry(IndexedTable.IndexedEntry entry, List<String> names) {
        super(entry.getSize());
        for (int i=0; i<names.size(); ++i)
            entryNames.put(names.get(i), i);
        entryValues = entry.entryValues;
    }

    public Number getValue(String name) {
        return getValue(entryNames.get(name));
    }

}
