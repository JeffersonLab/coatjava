package org.jlab.io.evio;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataDescriptor;
import org.jlab.io.base.DataDictionary;
import org.jlab.io.utils.DictionaryLoader;
import org.jlab.utils.FileUtils;
import org.jlab.utils.TablePrintout;

/**
 *
 * @author gavalian
 */
public class EvioDataDictionary implements DataDictionary {

	static final Logger LOGGER = Logger.getLogger(EvioDataDictionary.class.getName());
	private HashMap<String, EvioDataDescriptor> descriptors = new HashMap<>();

	public EvioDataDictionary() {

	}

	public EvioDataDictionary(String env, String relative_path) {
		this.initWithEnv(env, relative_path);
	}

	public EvioDataDictionary(String directory) {
		this.initWithDir(directory);
	}

    @Override
	public void init(String format) {

	}

    @Override
	public String getXML() {
		return "some xml";
	}

    @Override
	public String[] getDescriptorList() {
		String[] names = new String[descriptors.keySet().size()];
		int icounter = 0;
		for (String key : descriptors.keySet()) {
			names[icounter] = key;
			icounter++;
		}
		return names;
	}

    @Override
	public DataDescriptor getDescriptor(String desc_name) {
		if (descriptors.containsKey(desc_name) == true) {
			return descriptors.get(desc_name);
		}
		return null;
	}

	public final void initWithEnv(String envname, String relative_path) {
		String ENVDIR = System.getenv(envname);
		if (ENVDIR == null) {
			LOGGER.log(Level.SEVERE,"---> Warning the CLAS12DIR environment is not defined.");
			return;
		}
		String dict_path = ENVDIR + "/" + relative_path;
		this.initWithDir(dict_path);
	}

	public final void clear() {
		this.descriptors.clear();
	}

	public final void initWithEnv(String envname) {
		this.initWithEnv(envname, "etc/bankdefs/clas12");
	}

	public final void initWithDir(String dirname) {
		ArrayList<String> ignorePrefixes = new ArrayList<>();
		ignorePrefixes.add(".");
		ignorePrefixes.add("_");
		LOGGER.log(Level.INFO, "[EvioDataDictionary]---> loading bankdefs from directory : {0}", dirname);
		File dict_dir = new File(dirname);

		if (dict_dir.exists() == false) {
			LOGGER.log(Level.SEVERE,"[EvioDataDictionary]---> Directory does not exist.....");
			return;
		}

		ArrayList<String> xmlFileList = FileUtils.filesInFolder(dict_dir, "xml", ignorePrefixes);
		LOGGER.log(Level.INFO, "[EvioDataDictionary]------> number of XML files located  : {0}", xmlFileList.size());
		Integer counter = 0;
		for (String file : xmlFileList) {
			ArrayList<EvioDataDescriptor> descList = DictionaryLoader.getDescriptorsFromFile(file);
			for (EvioDataDescriptor desc : descList) {
				descriptors.put(desc.getName(), desc);
				counter++;
			}
		}
		LOGGER.log(Level.INFO, "[EvioDataDictionary]--> total number of descriptors found  : {0}", counter.toString());
	}

	public void show() {
		TablePrintout table = new TablePrintout("Bank:Columns:Tag:Number", "42:8:8:8");
		for (Map.Entry<String, EvioDataDescriptor> entry : descriptors.entrySet()) {
			String name = entry.getKey();
			String[] info = new String[4];
			info[0] = name;
			Integer nentries = descriptors.get(name).getEntryList().length;
			info[1] = nentries.toString();
			Integer tag = descriptors.get(name).getProperty("tag");
			Integer num = descriptors.get(name).getProperty("num");
			info[2] = tag.toString();
			info[3] = num.toString();
			table.addData(info);
		}
		table.show();
	}

	/**
	 * returns a name for the variable given tag and number.
	 * 
	 * @param tag
	 *            tag of the variable
	 * @param num
	 *            num of the variable
	 * @return
	 */
	public String getNameByTagNum(int tag, int num) {

		String name = "undefined";
		for (Map.Entry<String, EvioDataDescriptor> desc : descriptors.entrySet()) {
			String[] entries = desc.getValue().getEntryList();
			for (String entryname : entries) {
				if (num == 0) {
					if (Integer.parseInt(desc.getValue().getPropertyString("parent_tag")) == tag) {
						return desc.getValue().getName().split("::")[0];
					}
					if (Integer.parseInt(desc.getValue().getPropertyString("container_tag")) == tag) {
						return desc.getValue().getName();
					}
				}
				if (desc.getValue().getProperty("tag", entryname) == tag && desc.getValue().getProperty("num", entryname) == num) {
					return desc.getKey() + "." + entryname;
				}
			}
		}
		return name;
	}

    @Override
	public DataBank createBank(String name, int rows) {
		if (descriptors.containsKey(name) == false) {
			LOGGER.log(Level.SEVERE, "[EvioDataDictionary]:: ERROR ---> no descriptor with name = {0} is found", name);
		}
		EvioDataDescriptor desc = descriptors.get(name);
		EvioDataBank bank = new EvioDataBank(desc);
		bank.allocate(rows);
		return bank;
	}

	public void addDescriptor(EvioDataDescriptor desc) {
		this.descriptors.put(desc.getName(), desc);
	}
}
