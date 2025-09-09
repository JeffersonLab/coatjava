package org.jlab.utils.system;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 *
 * @author gavalian
 */
public class ClasUtilsFile {
    
    private static String moduleString = "[ClasUtilsFile] --> ";
            
    public static String getName(){ return moduleString; }

    /**
     * prints a log message with the module name included.
     * @param log 
     */
    public static void printLog(String log){
        System.out.println(ClasUtilsFile.getName() + " " + log);
    }

    /**
     * @param clazz
     * @return absolute path of the jar file containing clazz
     */
    public static String getJarPath(Class clazz) {
        try {
            return (new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI())).getAbsolutePath();
        } catch (Exception e) {
            System.getLogger(ClasUtilsFile.class.getName()).log(System.Logger.Level.ERROR, (String) null, e);
            return null;
        }
    }
    
    /**
     * @return absolute path to COATJAVA installation runtime directory
     */
    public static String getCoatjavaRuntimeDir() {
        String ret = getJarPath(ClasUtilsFile.class);
        if (ret != null) {
            String[] d = ret.split("/");
            if (System.console() == null)
                // When run from an IDE, the jar is in the coatjava source tree,
                // so assume the "coatjava" installation directory at the top:
                ret = "/" + String.join("/", Arrays.copyOfRange(d,0,d.length-4)) + "/coatjava";
            else
                // When running the JVM directly, the jar is already inside a
                // coatjava installation, so just get to the top of it: 
                ret = "/" + String.join("/", Arrays.copyOfRange(d,0,d.length-3));

        }
        return ret;
    }
    
    /**
     * returns package resource directory with given enviromental variable
     * and relative path.
     * @param env
     * @param rpath
     * @return 
     */
    public static String getResourceDir(String env, String rpath){
        
        String value = System.getenv(env);

        if(value==null){
            ClasUtilsFile.printLog("Environment variable ["+env+"] is not defined");
            value = System.getProperty(env);
        }
        
        if(value == null){
            ClasUtilsFile.printLog("System property ["+env+"] is not defined");
            if (env.equals("COATJAVA") || env.equals("CLAS12DIR")) {
                value = getCoatjavaRuntimeDir();
            }
        }

        if (value == null) return null;
        
        StringBuilder str = new StringBuilder();
        str.append(value);
        if (!value.endsWith("/") && !rpath.startsWith("/")) str.append('/');
        str.append(rpath);        
        return str.toString();
    }

    /**
     * returns list of files in the directory. absolute path is given.
     * This function will not exclude ".*" and "*~" files.
     * @param directory
     * @return 
     */
    public static List<String>  getFileList(String directory){        
        List<String> fileList = new ArrayList<>();
        File[] files = new File(directory).listFiles();
        System.out.println("FILE LIST LENGTH = " + files.length);
        for (File file : files) {
            if (file.isFile()) {
                if(file.getName().startsWith(".")==true||
                        file.getName().endsWith("~")){
                    System.out.println("[FileUtils] ----> skipping file : " + file.getName());
                } else {
                    fileList.add(file.getAbsolutePath());
                }
            }
        }
        return fileList;
    }
    /**
     * returns list of files in the directory defined by environment variable
     * and a relative path.
     * @param env
     * @param rpath
     * @return 
     */
    public static List<String>  getFileList(String env, String rpath){
        String directory = ClasUtilsFile.getResourceDir(env, rpath);
        if(directory==null){
            ClasUtilsFile.printLog("(error) directory does not exist : " + directory);
            return new ArrayList<>();
        }
        return ClasUtilsFile.getFileList(directory);
    }
    /**
     * returns a file list that contains files with given extension
     * @param env
     * @param rpath
     * @param ext
     * @return 
     */
    public static List<String>  getFileList(String env, String rpath, String ext){
        String directory = ClasUtilsFile.getResourceDir(env, rpath);
        if(directory!=null) return new ArrayList<>();
        
        List<String> files = ClasUtilsFile.getFileList(directory);
        List<String> selected = new ArrayList<>();
        for(String item : files){
            if(item.endsWith(ext)==true) selected.add(item);
        }
        return selected;
    }
    
    public static void   writeFile(String filename, List<String> lines){
        System.out.println("writing file --->  " + filename);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(filename));
            for(String line : lines){
                writer.write (line +"\n");
            }  writer.close();
        } catch (IOException ex) {
            Logger.getLogger(ClasUtilsFile.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                writer.close();
            } catch (IOException ex) {
                Logger.getLogger(ClasUtilsFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    /**
     * Reads a text file into a list of strings  
     * @param filename
     * @return 
     */
    public static List<String>   readFile(String filename){
        List<String>  lines = new ArrayList<>();
        String line = null;
        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader =  new FileReader(filename);
            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader =  new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                //System.out.println(line);
                lines.add(line);
            }   
            // Always close files.
            bufferedReader.close();         
        }
        catch(FileNotFoundException ex) {
            ClasUtilsFile.printLog("Unable to open file : '" + filename + "'");             
        }
        catch(IOException ex) {
            ClasUtilsFile.printLog( "Error reading file : '" + filename + "'");                  
            // Or we could just do this: 
            // ex.printStackTrace();
        }
        return lines;
    }
    /**
     * Reads a text file into one string.
     * @param filename
     * @return 
     */
    public static String readFileString(String filename){
        List<String> lines = ClasUtilsFile.readFile(filename);
        StringBuilder str = new StringBuilder();
        for(String line : lines) str.append(line);
        return str.toString();
    }
    /**
     * Returs relative paths of file names from list of absolute paths.
     * @param files
     * @return 
     */
    public static List<String>  getFileNamesRelative(List<String> files){
        List<String>  newList = new ArrayList<>();
        for(String file : files){
            int index = file.lastIndexOf('/');
            if(index>=0&&index<file.length()){
                newList.add(file.substring(index+1, file.length()));
            } else {
                newList.add(file);
            }
        }
        return newList;
    }
    
    /**
     * returns a new file name which is composed of the file name given and then by adding
     * given string to it. if flag preservePath is true, then file name will have the same
     * path as the original file name.
     * @param filename
     * @param addition
     * @param preservePath
     * @return 
     */
    public static String createFileName(String filename, String addition, boolean preservePath){
        
        String inputFile = filename;
        
        if(filename.contains("/")==true&&preservePath==false){
            int index_slash = filename.lastIndexOf("/");
            inputFile = filename.substring(index_slash+1,filename.length());
        }
        
        StringBuilder str = new StringBuilder();
        int index = inputFile.lastIndexOf(".");
        str.append(inputFile.substring(0, index));
        str.append(addition);
        str.append(inputFile.substring(index, inputFile.length()));
        return str.toString();
    }
    
    public static void main(String[] args){
        System.out.println(getCoatjavaRuntimeDir());
    }
}
