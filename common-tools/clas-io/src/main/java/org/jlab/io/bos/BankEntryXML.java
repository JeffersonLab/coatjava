package org.jlab.io.bos;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author gavalian
 */
@XmlRootElement(name="entry")
public class BankEntryXML {
    private String entryName;
    private String entryTypeString;
    
    public BankEntryXML(String name, String type){
        entryName = name;
        entryTypeString = type;
    }
    
    public BankEntryXML(){
        
    }
    
    @XmlAttribute(name="name")
    public String getEntryName(){
        return entryName;
    }
    
    public void setEntryName(String name){
        entryName = name;
    }
    @XmlAttribute(name="type")
    public String getEntryTypeString(){
        return entryTypeString;
    }
    
    public void setEntryTypeString(String type){
        entryTypeString = type;
    }
}
