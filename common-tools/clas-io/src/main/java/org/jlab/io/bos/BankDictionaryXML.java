package org.jlab.io.bos;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;


/**
 *
 * @author gavalian
 */
@XmlRootElement(name="dictionary")
public class BankDictionaryXML {
    private ArrayList<BankDescriptorXML> descriptors = new ArrayList<BankDescriptorXML>();
    public BankDictionaryXML(){
        
    }
    @XmlElement(name="banks")
    public ArrayList<BankDescriptorXML> getDescriptors(){
        return descriptors;
    }
    
    public void setDescriptors(ArrayList<BankDescriptorXML> dc){
        descriptors = dc;
    }
    
    public BankDictionaryXML load(String filename){
         try {
            //File file = new File(filename);
            File stream = new File(filename);
            JAXBContext jaxbContext = JAXBContext.newInstance(BankDictionaryXML.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            BankDictionaryXML group = (BankDictionaryXML) jaxbUnmarshaller.unmarshal(stream);            
            return group;
        } catch (JAXBException ex) {
            Logger.getLogger(BankDictionaryXML.class.getName()).log(Level.SEVERE, null, ex);
        }
         return this;
    }
    
    public void save(String filename)
    {
        try {
            File file = new File(filename);
            JAXBContext jaxbContext = JAXBContext.newInstance(BankDictionaryXML.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(this, file);
        } catch (JAXBException ex) {
            Logger.getLogger(BankDictionaryXML.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public BankDictionaryXML loadResource(String filename){
        String CLAS12DIR = System.getenv("CLAS12DIR");
        if(CLAS12DIR==null){
            System.err.println("[XML-Dictionary]---> ERROR : CLAS12DIR environment "
            + " is not defined.");
            return this;
        }
        String dictPath = CLAS12DIR + "/etc/bankdefs/clas6/"+filename;
        return this.load(dictPath);
    }
}
