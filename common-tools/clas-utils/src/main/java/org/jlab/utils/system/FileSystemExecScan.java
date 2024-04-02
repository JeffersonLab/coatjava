package org.jlab.utils.system;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.text.StringSubstitutor;

/**
 * Find a location for a usable temporary directory, on a filesystem mounted
 * without noexec, and set a system property accordingly.
 * 
 * @author gavalian
 * @author baltzell
 */
public class FileSystemExecScan {
    
    static final Logger LOGGER = Logger.getLogger(FileSystemExecScan.class.getName());

    static final StringSubstitutor SUBSTITUTOR = new StringSubstitutor(System.getenv());

    static final String[] DEFAULT_TMPDIRS = new String[] {
        "/scratch/slurm/${SLURM_JOB_ID}",
        "/scratch/${USER}",
        "/tmp",
        ".",
        "${HOME}",
    };

    String systemProperty;
    
    public FileSystemExecScan(String prop){
        systemProperty = prop;
    }
    
    public boolean scan() {
        return scan(DEFAULT_TMPDIRS);
    }
    
    public boolean scan(String... dirs){
        for(String dir : dirs){
            if (checkDirectory(dir)) {
                LOGGER.info(String.format("Setting property : %s to %s",systemProperty,dir));
                System.setProperty(systemProperty, dir);
                return true;
            }
        }
        LOGGER.log(Level.SEVERE, "No suitable tmp dir found in:  {0}", String.join(":", dirs));
        return false;
    }
    
    public boolean checkDirectory(String dir){
        dir = SUBSTITUTOR.replace(dir);
        LOGGER.config(String.format("Checking directory:  "+dir));
        File f = new File(dir);
        if (!f.canWrite()) return false;
        String path = String.format("%s/list-%d.sh", dir,ProcessHandle.current().pid());
        if (!writeFile(path)) return false;        
        File e = new File(path);       
        e.setExecutable(true, false);
        boolean canExecute = e.canExecute();
        e.delete();
        return canExecute;
    }
    
    public boolean writeFile(String path){
        try (FileWriter w = new FileWriter(path)) {
            w.write("#!/bin/sh\nls -l \n");
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "() error writing file: {0}", path);
            return false;
        }
        return true;
    }

    public static void main(String[] args){
        FileSystemExecScan scan = new FileSystemExecScan("java.io.tmpdir");
        scan.scan("/usr/bin","/sbin");
        scan.scan("/tmp",".");
        scan.scan(DEFAULT_TMPDIRS);
    }
}
