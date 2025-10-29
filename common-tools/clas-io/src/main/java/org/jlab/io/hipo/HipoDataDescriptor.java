package org.jlab.io.hipo;

import org.jlab.io.base.DataDescriptor;
import org.jlab.jnp.hipo4.data.Schema;

/**
 *
 * @author gavalian
 */
public class HipoDataDescriptor implements DataDescriptor {

    private  Schema hipoSchema = null;
    
    public HipoDataDescriptor(){
    }
     
    public HipoDataDescriptor(Schema schema){
        hipoSchema = schema;
    }
    
    public final void init(Schema schema){
    }
    
    @Override
    public void init(String s) {
        hipoSchema = Schema.fromJsonString(s);
    }

    @Override
    public String[] getEntryList() {
        int elements = hipoSchema.getElements();
        String[] entries = new String[elements];
        for(int i = 0; i < elements; i++){
            entries[i] = hipoSchema.getElementName(i);
        }
        return entries;
    }

    @Override
    public String getXML() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean hasEntry(String entry) {
        return hipoSchema.hasEntry(entry);
    }

    @Override
    public boolean hasEntries(String... entries) {
        for(String entry : entries){
            if(hasEntry(entry)==false) return false;
        }
        return true;
    }

    @Override
    public int getProperty(String property_name, String entry_name) {
        if(property_name.compareTo("type")==0){
            int type = hipoSchema.getType(entry_name);
            return type;
        }
        return 0;
    }

    @Override
    public int getProperty(String property_name) {
        return 1;
    }

    @Override
    public void setPropertyString(String name, String value) {
        
    }

    @Override
    public String getPropertyString(String property_name) {
        return "undefined";
    }

    @Override
    public void show() {
        System.out.println(this.hipoSchema.toString());
    }

    @Override
    public String getName() {
        return this.hipoSchema.getName();
    }
    
    public Schema getSchema(){ return this.hipoSchema;}
}
