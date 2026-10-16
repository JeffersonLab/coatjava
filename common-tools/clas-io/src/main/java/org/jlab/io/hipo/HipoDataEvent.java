package org.jlab.io.hipo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataDictionary;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.data.Schema;

/**
 *
 * @author gavalian
 */
public class HipoDataEvent implements DataEvent {
    
    private Event hipoEvent = null;
    private SchemaFactory schemaFactory = null;
    
    private DataEventType eventType = DataEventType.EVENT_ACCUMULATE;
    
    public HipoDataEvent(byte[] array, SchemaFactory factory){
        hipoEvent = new Event(array.length);
        hipoEvent.initFrom(array);
        schemaFactory = factory;
    }
    
    public HipoDataEvent(Event event){
        this.hipoEvent = event;
    }
    
    public HipoDataEvent(Event event,SchemaFactory factory){
        this.hipoEvent = event;
        schemaFactory = factory;
    }
    
    public Event  getHipoEvent(){return this.hipoEvent;}
    
    public void initDictionary(SchemaFactory factory){
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public String[] getBankList() {
        List<Schema> schemaList = schemaFactory.getSchemaList();
        List<String> existingBanks = new ArrayList<String>();
        for(Schema schema : schemaList){
            int group = schema.getGroup();
            int item  = schema.getItem();
            if(hipoEvent.scan(group, item)>0){
                existingBanks.add(schema.getName());
            }
        }
        String[] list = new String[existingBanks.size()];
        for(int i = 0; i < list.length; i++) list[i] = existingBanks.get(i);
        return list;
    }

    public String[] getColumnList(String bank_name) {
        Schema schema = schemaFactory.getSchema(bank_name);
        int  ncolumns = schemaFactory.getSchema(bank_name).getElements();
        String[] columns = new String[ncolumns];
        for(int i = 0; i < columns.length; i++) columns[i] = schema.getElementName(i);
        return columns;
    }
    
    public void addSchema(Schema schema){
        schemaFactory.addSchema(schema);
    }
    
    public void addSchemaList(List<Schema> schemaList){
        for(Schema schema : schemaList) addSchema(schema);
    }
    
    @Override
    public DataDictionary getDictionary() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ByteBuffer getEventBuffer() {
        return hipoEvent.getEventBuffer();
    }

    @Override
    public void appendBank(DataBank bank) {
        if(bank==null) return;
        if(bank instanceof HipoDataBank){
            Bank group =  ((HipoDataBank) bank).getBank();
            hipoEvent.write(group);
        }
    }

    public void appendBanks(DataBank... bank) {
        for(DataBank item : bank){
            this.appendBank(item);
        }
    }

    @Override
    public boolean hasBank(String name) {
        Schema schema = schemaFactory.getSchema(name);
        if(schema==null) return false;
        return hipoEvent.scan(schema.getGroup(),schema.getItem())>0;
    }

    @Override
    public DataBank getBank(String bank_name) {
        Schema schema = schemaFactory.getSchema(bank_name);
        if(schema!=null){            
            Bank bank = new Bank(schema);            
            hipoEvent.read(bank);
            HipoDataBank dataBank = new HipoDataBank(bank);
            return dataBank;
        }        
        return null;
    }

    public void getBank(String bank_name, DataBank bank) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setProperty(String property, String value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getProperty(String property) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public double[] getDouble(String path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setDouble(String path, double[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendDouble(String path, double[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public float[] getFloat(String path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setFloat(String path, float[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendFloat(String path, float[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int[] getInt(String path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setInt(String path, int[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendInt(String path, int[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short[] getShort(String path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setShort(String path, short[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendShort(String path, short[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public byte[] getByte(String path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setByte(String path, byte[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendByte(String path, byte[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setType(DataEventType type) {
        this.eventType = type;
    }

    public DataEventType getType() {
        return this.eventType;
    }
    
    public void show(){
        this.hipoEvent.scan();
    }
    
    public void showBankByOrder(int order){
       // this.hipoEvent.showGroupByOrder(order);
    }
    
    @Override
    public DataBank createBank(String bank_name, int rows) {
        
        Schema schema = schemaFactory.getSchema(bank_name);
        if(schema ==null) {
            System.out.println(" SCHEMA FOR ["+bank_name + "] = NULL");
            List<String>  scList = schemaFactory.getSchemaKeys();
            System.out.println(" SCHEMA FACTORY SIZE = " + scList.size());
            Collections.sort(scList);
            for(String sc : scList){
                System.out.println("\t ----> " + sc);
            }
        }
        Bank bank = new Bank(schema,rows);
        return new HipoDataBank(bank);
    }

    @Override
    public void removeBank(String bankName) {
        if(schemaFactory.hasSchema(bankName)==true){
            hipoEvent.remove(schemaFactory.getSchema(bankName));
        }
        //this.hipoEvent.removeGroup(bankName);
    }

    @Override
    public void removeBanks(String... bankNames) {
        for(String bank : bankNames){
            removeBank(bank);
        }
    }

    @Override
    public long[] getLong(String path) {
       /* HipoNode node = this.getHipoNodeByPath(path);        
        if(node==null){
            System.out.println("\n>>>>> error : getting node failed : " + path);
            return new long[0];
        }
        int size = node.getDataSize();
        long[] data = new long[size];
        for(int i =0; i < data.length; i++) data[i] = node.getLong(i);
        return data;*/
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setLong(String path, long[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void appendLong(String path, long[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
