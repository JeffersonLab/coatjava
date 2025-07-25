package org.jlab.io.evio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.coda.jevio.ByteDataTransformer;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EventBuilder;
import org.jlab.coda.jevio.EvioBank;
import org.jlab.coda.jevio.EvioCompactStructureHandler;
import org.jlab.coda.jevio.EvioEvent;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioNode;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataDescriptor;
import org.jlab.io.base.DataDictionary;
import org.jlab.io.base.DataEntryType;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.utils.TablePrintout;

public class EvioDataEvent implements DataEvent {

    private HashMap<String, String> eventProperties = new HashMap<>();
    private ByteBuffer evioBuffer;
    private EvioDataEventHandler eventHandler = null;
    private EvioDataDictionary dictionary = null;
    private DataEventType eventType = DataEventType.EVENT_ACCUMULATE;

    public EvioDataEvent(byte[] buffer, ByteOrder b_order) {

        evioBuffer = ByteBuffer.wrap(buffer);
        evioBuffer.order(b_order);
        eventHandler = new EvioDataEventHandler(evioBuffer);
    }

    public EvioDataEvent(ByteBuffer buff) {
        evioBuffer = buff;
        eventHandler = new EvioDataEventHandler(evioBuffer);
    }

    public EvioDataEvent(ByteBuffer buff, EvioDataDictionary dict) {
        evioBuffer = buff;
        dictionary = dict;
        eventHandler = new EvioDataEventHandler(evioBuffer);
    }

    public ByteOrder getByteOrder() {
        return this.eventHandler.getStructure().getByteBuffer().order();
    }

    public EvioDataEvent(byte[] buffer, ByteOrder b_order, EvioDataDictionary dict) {
        evioBuffer = ByteBuffer.wrap(buffer);
        evioBuffer.order(b_order);
        this.eventHandler = new EvioDataEventHandler(buffer, b_order);
        dictionary = dict;
        this.setProperty("banks", "*");
        this.setProperty("variables", "*");
    }

    @Override
    public String[] getBankList() {
        try {
            List<EvioNode> nodes = this.eventHandler.getStructure().getNodes();
            ArrayList<String> list = new ArrayList<>();
            String[] descList = dictionary.getDescriptorList();
            for (EvioNode item : nodes) {
                if (item.getDataTypeObj() == DataType.ALSOBANK && item.getNum() == 0) {
                    for (String di : descList) {
                        if (Integer.parseInt(dictionary.getDescriptor(di).getPropertyString("container_tag")) == item.getTag()) {
                            list.add(di);
                        }
                    }
                }
            }
            String[] banks = new String[list.size()];
            for (int loop = 0; loop < list.size(); loop++)
                banks[loop] = list.get(loop);
            return banks;
        } catch (EvioException ex) {
            Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public EvioCompactStructureHandler getStructureHandler() {
        return this.eventHandler.getStructure();
    }

    public EvioDataEventHandler getHandler() {
        return this.eventHandler;
    }

    public void initEvent(ByteBuffer buffer) {
        evioBuffer = buffer;
    }

    @Override
    public String[] getColumnList(String bank_name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DataDictionary getDictionary() {
        return this.dictionary;
    }

    private int[] getTagNum(String path) {
        String[] split_path = path.split("[.]+");
        String bank = split_path[0];
        String col = split_path[1];

        DataDescriptor desc = this.dictionary.getDescriptor(bank);
        int tag = desc.getProperty("tag", col);
        int num = desc.getProperty("num", col);
        int[] ret = { tag, num };
        return ret;
    }

    @Override
    public float[] getFloat(String path) {
        if (path.contains("/") == true) {
            String[] tokens = path.split("/");
            return this.getFloat(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        } else {
            int[] tagnum = this.getTagNum(path);
            return this.getFloat(tagnum[0], tagnum[1]);
        }
    }

    public float[] getFloat(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.FLOAT32);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                float[] nodedata = ByteDataTransformer.toFloatArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    @Override
    public void setFloat(String path, float[] arr) {
    }

    @Override
    public void appendFloat(String path, float[] arr) {
    }

    @Override
    public int[] getInt(String path) {
        if (path.contains("/") == true) {
            String[] tokens = path.split("/");
            return this.getInt(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        } else {
            int[] tagnum = this.getTagNum(path);
            return this.getInt(tagnum[0], tagnum[1]);
        }
    }

    public String[] getString(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.CHARSTAR8);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                String[] nodedata = ByteDataTransformer.toStringArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public int[] getInt(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.INT32);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                int[] nodedata = ByteDataTransformer.toIntArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

        public long[] getLong(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.LONG64);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                long[] nodedata = ByteDataTransformer.toLongArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
        
    @Override
    public void setInt(String path, int[] arr) {
    }

    @Override
    public void appendInt(String path, int[] arr) {
    }

    @Override
    public short[] getShort(String path) {
        int[] tagnum = this.getTagNum(path);
        return this.getShort(tagnum[0], tagnum[1]);
    }

    public short[] getShort(int tag, int num) {
        return null;
    }

    @Override
    public void setShort(String path, short[] arr) {
    }

    @Override
    public void appendShort(String path, short[] arr) {
    }

    public byte[] getComposite(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.COMPOSITE);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                byte[] nodedata = ByteDataTransformer.toByteArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public boolean hasBank(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.COMPOSITE);
        return node != null;
    }

    @Override
    public boolean hasBank(String bank_name) {

        if (this.dictionary.getDescriptor(bank_name) == null) {
            System.err.println("[EvioDataEvent::hasBank] ( ERROR ) ---> " + " there is no descriptor with name " + bank_name);
            return false;
        }
        if (this.eventHandler == null) {
            System.out.println("SEVERE ERROR Event handler is NULL");
            return false;
        }

        EvioDataDescriptor desc = (EvioDataDescriptor) this.dictionary.getDescriptor(bank_name);
        if (desc == null) {
            return false;
        }
        int parenttag = Integer.parseInt(desc.getPropertyString("parent_tag"));
        int nodetag = Integer.parseInt(desc.getPropertyString("container_tag"));

        EvioNode parentNode = this.eventHandler.getRootNode(parenttag, 0, DataType.ALSOBANK);
        if (bank_name.compareTo("GenPart::header") == 0 && parentNode != null)
            return true;
        if (bank_name.compareTo("GenPart::true") == 0 && parentNode != null)
            return true;
        if (bank_name.compareTo("Lund::header") == 0 && parentNode != null)
            return true;
        if (bank_name.compareTo("Lund::true") == 0 && parentNode != null)
            return true;
        if (parentNode == null)
            return false;
        EvioNode leafNode = this.eventHandler.getChildNode(parentNode, nodetag, 0, DataType.ALSOBANK);
        return leafNode != null;
    }

    @Override
    public DataBank getBank(String bank_name) {
        EvioDataDescriptor desc = (EvioDataDescriptor) this.dictionary.getDescriptor(bank_name);
        if (desc == null)
            return null;

        int parenttag = Integer.parseInt(desc.getPropertyString("parent_tag"));
        int nodetag = Integer.parseInt(desc.getPropertyString("container_tag"));

        EvioNode parentNode = this.eventHandler.getRootNode(parenttag, 0, DataType.ALSOBANK);
        if (parentNode == null)
            return null;

        EvioNode leafNode = this.eventHandler.getChildNode(parentNode, nodetag, 0, DataType.ALSOBANK);
        if (leafNode == null && bank_name.compareTo("GenPart::header") != 0 && bank_name.compareTo("GenPart::true") != 0 
                                     && bank_name.compareTo("Lund::header") != 0 && bank_name.compareTo("Lund::true") != 0)
            return null;

        TreeMap<Integer, Object> dataTree = null;
        if (bank_name.compareTo("GenPart::header") == 0) {
            dataTree = this.eventHandler.getNodeData(parentNode);
                } else if (bank_name.compareTo("GenPart::true") == 0) {
            dataTree = this.eventHandler.getNodeData(parentNode);
        } else if (bank_name.compareTo("Lund::header") == 0) {
            dataTree = this.eventHandler.getNodeData(parentNode);
        } else if (bank_name.compareTo("Lund::true") == 0) {
            dataTree = this.eventHandler.getNodeData(parentNode);
        } else {
            dataTree = this.eventHandler.getNodeData(leafNode);
        }

        EvioDataBank bank = new EvioDataBank(desc);
        String[] entries = desc.getEntryList();

        for (String item : entries) {
            int type = desc.getProperty("type", item);
            int num = desc.getProperty("num", item);

            if (DataEntryType.getType(type) == DataEntryType.INTEGER) {
                bank.setInt(item, (int[]) dataTree.get(num));
            }

            if (DataEntryType.getType(type) == DataEntryType.DOUBLE) {
                bank.setDouble(item, (double[]) dataTree.get(num));
            }

            if (DataEntryType.getType(type) == DataEntryType.FLOAT) {
                bank.setFloat(item, (float[]) dataTree.get(num));
            }
            if (DataEntryType.getType(type) == DataEntryType.SHORT) {
                bank.setShort(item, (short[]) dataTree.get(num));
            }

            if (DataEntryType.getType(type) == DataEntryType.BYTE) {
                bank.setByte(item, (byte[]) dataTree.get(num));
            }
        }
        return bank;
    }

    @Override
    public void getBank(String bank_name, DataBank bank) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void show() {

        String[] bankList = this.getBankList();
        TablePrintout table = new TablePrintout("bank:nrows:ncols", "48:12:12");

        if (bankList != null) {
            ArrayList<String> bankNames = new ArrayList<>();
            bankNames.addAll(Arrays.asList(bankList));

            Collections.sort(bankNames);

            for (String bank : bankNames) {
                String[] tokens = new String[3];
                tokens[0] = bank;
                DataBank dbank = null;
                try {
                    dbank = this.getBank(bank);
                } catch (Exception e) {
                    System.out.println(" ERROR : getbank failed for bank name " + bank);
                    e.printStackTrace();
                }
                if (dbank == null) {
                    System.err.println("[EvioDataEvent::show] ERROR : bank " + bank + " does not exist");
                    continue;
                }
                Integer ncols = dbank.columns();
                Integer nrows = dbank.rows();
                tokens[1] = nrows.toString();
                tokens[2] = ncols.toString();
                table.addData(tokens);
            }
        }
        table.show();
    }

    public EvioNode getNodeFromTree(int parent_tag, int tag, int num, DataType type) {
        return null;
    }

    public EvioNode getNodeFromTree(int tag, int num, DataType type) {

        try {
            List<EvioNode> nodes = this.eventHandler.getStructure().getNodes();

            if (nodes == null) {
                System.out.println("EVENT NODES = NULL");
                return null;
            }

            for (EvioNode item : nodes) {
                if (item.getDataTypeObj() == type) {
                }
                if (type == DataType.INT32) {
                    if (item.getTag() == tag && item.getNum() == num
                            && (item.getDataTypeObj() == DataType.INT32 || item.getDataTypeObj() == DataType.UINT32))
                        return item;
                }                     
                                
                if(type == DataType.LONG64){
                    if (item.getTag() == tag && item.getNum() == num
                        && (item.getDataTypeObj() == DataType.LONG64 || item.getDataTypeObj() == DataType.ULONG64))
                        return item;
                }
                if (item.getTag() == tag && item.getNum() == num && item.getDataTypeObj() == type)
                    return item;
            }

        } catch (EvioException ex) {
            Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public double[] getDouble(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.DOUBLE64);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                double[] nodedata = ByteDataTransformer.toDoubleArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    @Override
    public double[] getDouble(String path) {
        if (path.contains("/") == true) {
            String[] tokens = path.split("/");
            return this.getDouble(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        } else {
            int[] tagnum = this.getTagNum(path);
            return this.getDouble(tagnum[0], tagnum[1]);
        }
    }

    @Override
    public void setDouble(String path, double[] arr) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void appendDouble(String path, double[] arr) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
    }

    public void appendGeneratedBank(DataBank bank) {
        String parent_tag = bank.getDescriptor().getPropertyString("parent_tag");
        String container_tag = bank.getDescriptor().getPropertyString("container_tag");
        EvioEvent baseBank = new EvioEvent(Integer.parseInt(parent_tag), DataType.ALSOBANK, 0);
        EvioBank sectionBank = new EvioBank(Integer.parseInt(container_tag), DataType.ALSOBANK, 0);

        EventBuilder builder = new EventBuilder(baseBank);

        ByteOrder byteOrder = this.eventHandler.getStructure().getByteBuffer().order();

        baseBank.setByteOrder(byteOrder);
        sectionBank.setByteOrder(byteOrder);
        try {
            String[] entries = bank.getDescriptor().getEntryList();
            for (String entry : entries) {
                int e_tag = bank.getDescriptor().getProperty("tag", entry);
                int e_num = bank.getDescriptor().getProperty("num", entry);
                int e_typ = bank.getDescriptor().getProperty("type", entry);
                if (DataEntryType.getType(e_typ) == DataEntryType.INTEGER) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.INT32, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendIntData(bank.getInt(entry));
                    builder.addChild(baseBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.DOUBLE) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.DOUBLE64, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendDoubleData(bank.getDouble(entry));
                    builder.addChild(baseBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.FLOAT) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.FLOAT32, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendFloatData(bank.getFloat(entry));
                    builder.addChild(baseBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.SHORT) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.SHORT16, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendShortData(bank.getShort(entry));
                    builder.addChild(baseBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.BYTE) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.CHAR8, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendByteData(bank.getByte(entry));
                    builder.addChild(baseBank, dataBank);
                }
            }

            int byteSize = baseBank.getTotalBytes();
            ByteBuffer bb = ByteBuffer.allocate(byteSize);
            bb.order(byteOrder);
            baseBank.write(bb);
            bb.flip();
            ByteBuffer newBuffer = this.eventHandler.getStructure().addStructure(bb);
            EvioCompactStructureHandler handler =
                    new EvioCompactStructureHandler(this.eventHandler.getStructure().getByteBuffer(), DataType.BANK);
            this.eventHandler.setStructure(handler);
        } catch (EvioException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void appendBank(DataBank bank) {
        String parent_tag = bank.getDescriptor().getPropertyString("parent_tag");
        String container_tag = bank.getDescriptor().getPropertyString("container_tag");
        EvioEvent baseBank = new EvioEvent(Integer.parseInt(parent_tag), DataType.ALSOBANK, 0);
        EvioBank sectionBank = new EvioBank(Integer.parseInt(container_tag), DataType.ALSOBANK, 0);

        EventBuilder builder = new EventBuilder(baseBank);

        ByteOrder byteOrder = this.eventHandler.getStructure().getByteBuffer().order();

        baseBank.setByteOrder(byteOrder);
        sectionBank.setByteOrder(byteOrder);
        try {
            String[] entries = bank.getDescriptor().getEntryList();
            for (String entry : entries) {
                int e_tag = bank.getDescriptor().getProperty("tag", entry);
                int e_num = bank.getDescriptor().getProperty("num", entry);
                int e_typ = bank.getDescriptor().getProperty("type", entry);
                if (DataEntryType.getType(e_typ) == DataEntryType.INTEGER) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.INT32, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendIntData(bank.getInt(entry));
                    builder.addChild(sectionBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.DOUBLE) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.DOUBLE64, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendDoubleData(bank.getDouble(entry));
                    builder.addChild(sectionBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.FLOAT) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.FLOAT32, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendFloatData(bank.getFloat(entry));
                    builder.addChild(sectionBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.SHORT) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.SHORT16, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendShortData(bank.getShort(entry));
                    builder.addChild(sectionBank, dataBank);
                }

                if (DataEntryType.getType(e_typ) == DataEntryType.BYTE) {
                    EvioBank dataBank = new EvioBank(e_tag, DataType.CHAR8, e_num);
                    dataBank.setByteOrder(byteOrder);
                    dataBank.appendByteData(bank.getByte(entry));
                    builder.addChild(sectionBank, dataBank);
                }
            }

            builder.addChild(baseBank, sectionBank);

            int byteSize = baseBank.getTotalBytes();
            ByteBuffer bb = ByteBuffer.allocate(byteSize);
            bb.order(byteOrder);
            baseBank.write(bb);
            bb.flip();
            ByteBuffer newBuffer = this.eventHandler.getStructure().addStructure(bb);
            EvioCompactStructureHandler handler =
                    new EvioCompactStructureHandler(this.eventHandler.getStructure().getByteBuffer(), DataType.BANK);
            this.eventHandler.setStructure(handler);
        } catch (EvioException e) {
            e.printStackTrace();
        }

    }

    @Override
    public ByteBuffer getEventBuffer() {
        return this.eventHandler.getStructure().getByteBuffer();
    }

    @Override
    public final void setProperty(String property, String value) {
        if (eventProperties.containsKey(property) == true) {
            eventProperties.remove(property);
        }
        eventProperties.put(property, value);
    }

    @Override
    public String getProperty(String property) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
    }

    public byte[] getByte(int tag, int num) {
        EvioNode node = this.getNodeFromTree(tag, num, DataType.CHAR8);
        if (node != null) {
            try {
                ByteBuffer buffer = this.eventHandler.getStructure().getData(node);
                byte[] nodedata = ByteDataTransformer.toByteArray(buffer);
                return nodedata;
            } catch (EvioException ex) {
                Logger.getLogger(EvioDataEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    @Override
    public byte[] getByte(String path) {
        if (path.contains("/") == true) {
            String[] tokens = path.split("/");
            return this.getByte(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        } else {
            int[] tagnum = this.getTagNum(path);
            return this.getByte(tagnum[0], tagnum[1]);
        }
    }

    @Override
    public void setByte(String path, byte[] arr) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void appendByte(String path, byte[] arr) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
    }

    public void copyEvent(EvioDataEvent event) {
        byte[] eventBytes = event.getEventBuffer().array();
        this.eventHandler = new EvioDataEventHandler(eventBytes, event.getByteOrder());
    }

    @Override
    public void appendBanks(DataBank... banklist) {

        String common_tag = banklist[0].getDescriptor().getPropertyString("parent_tag");
        for (int loop = 0; loop < banklist.length; loop++) {
            String btag = banklist[loop].getDescriptor().getPropertyString("parent_tag");
            if (btag.compareTo(common_tag) != 0) {
            }
        }

        String parent_tag = common_tag;

        try {

            EvioEvent baseBank = new EvioEvent(Integer.parseInt(parent_tag), DataType.ALSOBANK, 0);
            ByteOrder byteOrder = this.eventHandler.getStructure().getByteBuffer().order();
            baseBank.setByteOrder(byteOrder);
            EventBuilder builder = new EventBuilder(baseBank);

            for (DataBank bank : banklist) {
                String container_tag = bank.getDescriptor().getPropertyString("container_tag");
                EvioBank sectionBank = new EvioBank(Integer.parseInt(container_tag), DataType.ALSOBANK, 0);
                sectionBank.setByteOrder(byteOrder);

                String[] entries = bank.getDescriptor().getEntryList();
                for (String entry : entries) {
                    int e_tag = bank.getDescriptor().getProperty("tag", entry);
                    int e_num = bank.getDescriptor().getProperty("num", entry);
                    int e_typ = bank.getDescriptor().getProperty("type", entry);
                    if (DataEntryType.getType(e_typ) == DataEntryType.INTEGER) {
                        EvioBank dataBank = new EvioBank(e_tag, DataType.INT32, e_num);
                        dataBank.setByteOrder(byteOrder);
                        dataBank.appendIntData(bank.getInt(entry));
                        builder.addChild(sectionBank, dataBank);
                    }

                    if (DataEntryType.getType(e_typ) == DataEntryType.DOUBLE) {
                        EvioBank dataBank = new EvioBank(e_tag, DataType.DOUBLE64, e_num);
                        dataBank.setByteOrder(byteOrder);
                        dataBank.appendDoubleData(bank.getDouble(entry));
                        builder.addChild(sectionBank, dataBank);
                    }

                    if (DataEntryType.getType(e_typ) == DataEntryType.FLOAT) {
                        EvioBank dataBank = new EvioBank(e_tag, DataType.FLOAT32, e_num);
                        dataBank.setByteOrder(byteOrder);
                        dataBank.appendFloatData(bank.getFloat(entry));
                        builder.addChild(sectionBank, dataBank);
                    }

                    if (DataEntryType.getType(e_typ) == DataEntryType.SHORT) {
                        EvioBank dataBank = new EvioBank(e_tag, DataType.SHORT16, e_num);
                        dataBank.setByteOrder(byteOrder);
                        dataBank.appendShortData(bank.getShort(entry));
                        builder.addChild(sectionBank, dataBank);
                    }

                    if (DataEntryType.getType(e_typ) == DataEntryType.BYTE) {
                        EvioBank dataBank = new EvioBank(e_tag, DataType.CHAR8, e_num);
                        dataBank.setByteOrder(byteOrder);
                        dataBank.appendByteData(bank.getByte(entry));
                        builder.addChild(sectionBank, dataBank);
                    }
                }

                builder.addChild(baseBank, sectionBank);
            }

            int byteSize = baseBank.getTotalBytes();
            ByteBuffer bb = ByteBuffer.allocate(byteSize);
            bb.order(byteOrder);
            baseBank.write(bb);
            bb.flip();
            ByteBuffer newBuffer = this.eventHandler.getStructure().addStructure(bb);
            EvioCompactStructureHandler handler =
                    new EvioCompactStructureHandler(this.eventHandler.getStructure().getByteBuffer(), DataType.BANK);
            this.eventHandler.setStructure(handler);
        } catch (EvioException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setType(DataEventType type) {
        this.eventType = type;
    }

    @Override
    public DataEventType getType() {
        return this.eventType;
    }

    @Override
    public DataBank createBank(String bank_name, int rows) {
        return this.dictionary.createBank(bank_name, rows);
    }

    @Override
    public void removeBank(String bankName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeBanks(String... bankNames) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long[] getLong(String path) {
        if (path.contains("/") == true) {
            String[] tokens = path.split("/");
            return this.getLong(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        } else {
            int[] tagnum = this.getTagNum(path);
            return this.getLong(tagnum[0], tagnum[1]);
        }
    }

    @Override
    public void setLong(String path, long[] arr) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void appendLong(String path, long[] arr) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    
}
