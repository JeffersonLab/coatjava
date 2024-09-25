package org.jlab.detector.calib.utils;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import org.jlab.ccdb.Assignment;
import org.jlab.ccdb.TypeTableColumn;
import javax.swing.table.DefaultTableModel;

public class StringIndexedTable extends DefaultTableModel {

    private final Map<String, Integer> keyrows = new LinkedHashMap<>();
    private Assignment assignment = null;

    public StringIndexedTable(Assignment a) {
        List<TypeTableColumn> t = a.getTypeTable().getColumns();
        for (int i=0; i<a.getRowCount(); ++i)
            keyrows.put(a.getColumnValuesString(t.get(0).getName()).get(i), i);
        assignment = a;
    }

    public String getValueString(String key, String varname) {
        return assignment.getColumnValuesString(varname).get(keyrows.get(key));
    }

    public float getValueFloat(String key, String varname) {
        return Float.parseFloat(getValueString(key, varname));
    }

    public int getValueInt(String key, String varname) {
        return Integer.parseInt(getValueString(key, varname));
    }

}
