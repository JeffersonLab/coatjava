package org.jlab.io.hipo;

import javax.swing.table.TableModel;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataDescriptor;
import org.jlab.jnp.hipo4.data.Bank;


/**
 *
 * @author gavalian
 */
public class HipoDataBank implements DataBank  {
    
    private HipoDataDescriptor descriptor = null;
    private Bank               hipoGroup  = null;
    
    public HipoDataBank(Bank bank){
        hipoGroup = bank;
        descriptor = new HipoDataDescriptor(bank.getSchema());
    }
    
    public HipoDataBank(HipoDataDescriptor desc, int size){        
        hipoGroup = new Bank( desc.getSchema(),size);
        this.descriptor = desc;
    }
    
    public Bank getBank(){
        return hipoGroup;
    }
    
    @Override
    public String[] getColumnList() {
        String[] columns = new String[descriptor.getSchema().getElements()];
        for(int i = 0; i < columns.length; i++) columns[i] = descriptor.getSchema().getElementName(i);
        return columns;
    }

    @Override
    public DataDescriptor getDescriptor() {
        return this.descriptor;
    }

    @Override
    public double[] getDouble(String path) {
        int    nrows = this.hipoGroup.getRows();
        double[] result = new double[nrows];
        for(int i = 0; i < nrows; i++) result[i] = hipoGroup.getDouble(path, i);
        return result;
    }

    @Override
    public double getDouble(String path, int index) {
        return this.hipoGroup.getDouble(path, index);
    }

    @Override
    public void setDouble(String path, double[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putDouble(path, i, arr[i]);
    }

    @Override
    public void setDouble(String path, int row, double value) {
        hipoGroup.putDouble(path,row,value);
    }

    @Override
    public void appendDouble(String path, double[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float[] getFloat(String path) {
        int    nrows = this.hipoGroup.getRows();
        float[] result = new float[nrows];
        for(int i = 0; i < nrows; i++) result[i] = hipoGroup.getFloat(path, i);
        return result;
    }

    @Override
    public float getFloat(String path, int index) {
        return this.hipoGroup.getFloat(path, index);
    }

    @Override
    public void setFloat(String path, float[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putFloat(path, i, arr[i]);
    }

    @Override
    public void setFloat(String path, int row, float value) {
        this.hipoGroup.putFloat(path, row, value);
    }

    @Override
    public void appendFloat(String path, float[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int[] getInt(String path) {
        int    nrows = this.hipoGroup.getRows();
        int[] result = new int[nrows];
        for(int i = 0; i < nrows; i++) result[i] = hipoGroup.getInt(path, i);
        return result;
    }

    @Override
    public int getInt(String path, int index) {
        return hipoGroup.getInt(path, index);
    }

    @Override
    public void setInt(String path, int[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putInt(path, i, arr[i]);
    }

    @Override
    public void setInt(String path, int row, int value) {
        hipoGroup.putInt(path, row, value);
    }

    @Override
    public void appendInt(String path, int[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public short[] getShort(String path) {
        int    nrows = this.hipoGroup.getRows();
        short[] result = new short[nrows];
        for(int i = 0; i < nrows; i++) result[i] = hipoGroup.getShort(path, i);
        return result;
    }

    @Override
    public short getShort(String path, int index) {
        return hipoGroup.getShort(path, index);        
    }

    @Override
    public void setShort(String path, short[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putShort(path, i, arr[i]);
    }

    @Override
    public void setShort(String path, int row, short value) {
        hipoGroup.putShort(path, row, value);
    }

    @Override
    public void appendShort(String path, short[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long[] getLong(String path) {
        int    nrows = this.hipoGroup.getRows();
        long[] result = new long[nrows];
        for(int i = 0; i < nrows; i++) result[i] = hipoGroup.getLong(path, i);
        return result;
    }

    @Override
    public long getLong(String path, int index) {
        return hipoGroup.getLong(path, index);        
    }

    @Override
    public void setLong(String path, long[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putLong(path, i, arr[i]);
    }

    @Override
    public void setLong(String path, int row, long value) {
        hipoGroup.putLong(path, row, value);
    }

    @Override
    public void appendLong(String path, long[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public byte[] getByte(String path) {
        int    nrows = this.hipoGroup.getRows();
        byte[] result = new byte[nrows];
        for(int i = 0; i < nrows; i++) result[i] = hipoGroup.getByte(path, i);
        return result;
    }

    @Override
    public byte getByte(String path, int index) {
        return hipoGroup.getByte(path, index);
    }

    @Override
    public void setByte(String path, byte[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putByte(path, i, arr[i]);
    }

    @Override
    public void setByte(String path, int row, byte value) {
        hipoGroup.putByte(path, row, value);
    }

    @Override
    public void appendByte(String path, byte[] arr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int columns() {
        return hipoGroup.getSchema().getElements();
    }

    @Override
    public int rows() {
        return hipoGroup.getRows();
    }

    @Override
    public void show() {
        System.out.println(" SHOWING BANK");
        this.hipoGroup.show();
    }

    @Override
    public void reset() {
        
    }

    @Override
    public void allocate(int rows) {
        
    }

    @Override
    public TableModel getTableModel(String mask) {
        return null;
    }
    
    @Override
    public byte getByte(int element, int index) {
        return this.hipoGroup.getByte(element, index);
    }
    @Override
    public short getShort(int element, int index) {
        return this.hipoGroup.getShort(element, index);
    }
    @Override
    public int getInt(int element, int index) {
        return this.hipoGroup.getInt(element, index);
    }
    @Override
    public long getLong(int element, int index) {
        return this.hipoGroup.getLong(element, index);
    }
    @Override
    public float getFloat(int element, int index) {
        return this.hipoGroup.getFloat(element, index);
    }
    @Override
    public double getDouble(int element, int index) {
        return this.hipoGroup.getDouble(element, index);
    }

    public void setFloat(int element, float[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putFloat(element, i, arr[i]);
    }
    public void setDouble(int element, double[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putDouble(element, i, arr[i]);
    }
    public void setByte(int element, byte[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putByte(element, i, arr[i]);
    }
    public void setShort(int element, short[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putShort(element, i, arr[i]);
    }
    public void setInt(int element, int[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putInt(element, i, arr[i]);
    }
    public void setLong(int element, long[] arr) {
        int nrows = this.hipoGroup.getRows();
        for (int i=0; i<nrows; i++)
            this.hipoGroup.putLong(element, i, arr[i]);
    }

    @Override
    public void setDouble(int element, int row, double value) {
        this.hipoGroup.putDouble(element, row, value);
    }

    @Override
    public void setFloat(int element, int row, float value) {
        this.hipoGroup.putFloat(element, row, value);
    }

    @Override
    public void setInt(int element, int row, int value) {
        this.hipoGroup.putInt(element, row, value);
    }

    @Override
    public void setShort(int element, int row, short value) {
        this.hipoGroup.putShort(element, row, value);
    }

    @Override
    public void setByte(int element, int row, byte value) {
        this.hipoGroup.putByte(element, row, value);
    }

    @Override
    public void setLong(int element, int row, long value) {
        this.hipoGroup.putLong(element, row, value);
    }
}
