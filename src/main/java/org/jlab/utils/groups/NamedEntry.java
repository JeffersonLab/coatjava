package org.jlab.utils.groups;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;

/**
 * IndexedEntry wrapper for names and indices.
 */
public class NamedEntry {

    IndexedEntry entry;
    Map<String,Integer> names = new HashMap<>();
    int[] index;

    public static NamedEntry create(IndexedEntry entry, List<String> names, int... index) {
        NamedEntry e = new NamedEntry();
        for (int i=0; i<names.size(); ++i)
            e.names.put(names.get(i), i);
        e.entry = entry;
        e.index = index;
        return e;
    }

    public Number getValue(String name) {
        return entry.getValue(names.get(name));
    }

    public int[] getIndex() {
        return index;
    }

}
