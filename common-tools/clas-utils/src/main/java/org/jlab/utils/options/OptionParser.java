package org.jlab.utils.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import org.jlab.logging.DefaultLogger;

/**
 *
 * @author gavalian
 */
public class OptionParser {
  
    private Map<String,OptionValue> optionsDescriptors = new TreeMap<>();    
    private Map<String,OptionValue>    requiredOptions = new TreeMap<>();
    private Map<String,OptionValue>      parsedOptions = new TreeMap<>();
    private List<String>               parsedInputList = new ArrayList<>();
    private String                             program = "undefined";
    private boolean                  requiresInputList = true;
    private String                  programDescription = "";
    private boolean                  overrideVerbosity = false;

    public OptionParser(){
        init();
    }
    
    public OptionParser(String pname){
        this.program = pname;
        init();
    }
   
    private void init() {
        addOption("-v","FINE","logging verbosity level");
    }
  
    public void setDescription(String desc){
        this.programDescription = desc;
    }

    public void setRequiresInputList(boolean flag){
        this.requiresInputList = flag;
    }
    
    public void addRequired(String key){
        OptionValue option = new OptionValue(key);
        requiredOptions.put(key, option);
    }

    private void check(String key, Set<String> keys) {
        if (keys.contains(key)) {
            System.out.println("WARNING: overriding OptionParser option:  "+key);
            if (key.equals("-v")) overrideVerbosity = true;
        }
    }
    
    public void addRequired(String key,String desc){
        check(key,requiredOptions.keySet());
        OptionValue option = new OptionValue(key);
        option.setDescription(desc);
        requiredOptions.put(key, option);
    }
    
    public void addOption(String key, String defaultValue){
        check(key, optionsDescriptors.keySet());
        OptionValue option = new OptionValue(key,defaultValue);
        optionsDescriptors.put(key, option);
    }
    
    public void addOption(String key, String defaultValue, String description){
        check(key, optionsDescriptors.keySet());
        OptionValue option = new OptionValue(key,defaultValue);
        option.setDescription(description);
        optionsDescriptors.put(key, option);
    }
    
    public boolean hasOption(String option){
        return this.parsedOptions.containsKey(option);
    }
    
    public OptionValue getOption(String option){
        return this.parsedOptions.get(option);
    }

    public void show(){
        for(Map.Entry<String,OptionValue> entry : this.parsedOptions.entrySet()){
         System.out.printf("%12s : %s\n", entry.getKey(),entry.getValue().getValue());
        }
    }
    
    public boolean containsOptions(List<String> arguments, String... options){
        for(String argument : arguments){
            for(String option : options){
                if(argument.compareTo(option)==0) return true;
            }
        }        
        return false;
    }
    
    public String getUsageString(){
        
        StringBuilder str = new StringBuilder();
        
        str.append("     Usage : ").append(program).append(" ");
        for(Map.Entry<String,OptionValue> entry : this.requiredOptions.entrySet()){
            str.append(entry.getKey()).append(" [").
                    append(entry.getValue().getDescription()).append("] ");
        }
        
        if(this.requiresInputList==true) str.append(" [input1] [input2] ....");
        
        str.append("\n\n   Options :\n");
        for(Map.Entry<String,OptionValue> entry : this.optionsDescriptors.entrySet()){
            str.append("").append(String.format("%10s : %s (default = %s)", 
                    entry.getKey(),entry.getValue().getDescription(),entry.getValue().stringValue()));
            str.append("\n");
        }
        return str.toString();
    }
    
    public void printUsage(){
        System.out.println("\n\n");
        System.out.println("*******************************************");
        System.out.println("*      PROGRAM USAGE : by OptionParser    *");
        System.out.println("*******************************************");
        System.out.println("\n\n");
        System.out.println(this.getUsageString());
        System.out.println("\n\n");
    }
    
    public void parse(String[] args){

        List<String> arguments = new ArrayList<>();
        arguments.addAll(Arrays.asList(args));

        // Default, non-overridable, help option:
        if(this.containsOptions(arguments, "-h","-help")==true){
            this.printUsage();
            System.exit(0);
        }

        // Parse required options:
        for(Map.Entry<String,OptionValue> entry : this.requiredOptions.entrySet()){
            boolean status = entry.getValue().parse(arguments);
            if(status==false) { 
                this.parsedOptions.clear();
                this.printUsage();
                System.err.println(" \n*** ERROR *** Missing argument : " + entry.getValue().getOption());
                System.exit(100);
            }
            this.parsedOptions.put(entry.getValue().getOption(), entry.getValue());
        }

        // Parse non-required options:
        for(Map.Entry<String,OptionValue> entry : this.optionsDescriptors.entrySet()){
            boolean status = entry.getValue().parse(arguments);
            this.parsedOptions.put(entry.getKey(), entry.getValue());
        }
       
        // Parse input list:
        parsedInputList.clear();
        for(String item : arguments){
            if(item.startsWith("-")==false){
                this.parsedInputList.add(item);
            }
        }
        if (this.requiresInputList && this.parsedInputList.isEmpty()) {
            System.err.println(" \n*** ERROR *** Empty Input List.");
            System.exit(101);
        }

        // Configure logger:
        if (!overrideVerbosity) {
            setVerbosity(this.parsedOptions.get("-v").stringValue());
        }
    }

    private void setVerbosity(String level) {
        try {
            DefaultLogger.initialize(Level.parse(level));
        }
        catch (IllegalArgumentException e) {
            System.err.println("Invalid -v java.util.logging.Level:  "+level);
            System.exit(102);
        }
        catch (NullPointerException e) {
            System.err.println("Unavailable -v COATJAVA logging level:  "+level);
            System.exit(103);
        }
    }

    public List<String> getInputList(){
        return this.parsedInputList;
    }
    
    public static void main(String[] args){
        OptionParser parser = new OptionParser();
        parser.addRequired("-o");
        parser.addOption("-r", "10");
        parser.addOption("-t", "25.0");
        parser.addOption("-d", "35");
        parser.parse(args);
        parser.show();        
    }
}
