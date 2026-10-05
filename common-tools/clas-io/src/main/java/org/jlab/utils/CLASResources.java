package org.jlab.utils;

import org.jlab.jnp.hipo4.data.SchemaFactory;

/**
 *
 * @author gavalian
 * @author kenjo
 */
public class CLASResources {
   
    public static volatile SchemaFactory FULL_SCHEMA = null;

    public static synchronized SchemaFactory getFullSchema() {
        if (FULL_SCHEMA == null) {
            FULL_SCHEMA = new SchemaFactory();
            FULL_SCHEMA.initFromDirectory(getResourcePath("etc/bankdefs/hipo4"));
        }
        SchemaFactory s = new SchemaFactory();
        s.copy(FULL_SCHEMA);
        return s;
    }

    public static String getResourcePath(String resource){
        String CLAS12DIR = System.getenv("CLAS12DIR");
        String CLAS12DIRPROP = System.getProperty("CLAS12DIR");
        if(CLAS12DIR!=null){
            return CLAS12DIR + "/" + resource;
        } else {
            System.err.println("[getResourcePath]---> warning the system "
                + " environment CLAS12DIR is not set.");
            if(CLAS12DIRPROP!=null){
                return CLAS12DIRPROP + "/" + resource;
            } else {
                System.err.println("[getResourcePath]---> warning the system "
                + " property CLAS12DIR is not set.");
            }
        }
        
        return null;
    }

    public static String getEnvironmentVariable(String envvarname){
        String envvar = System.getenv(envvarname);
        if(envvar!=null){
            return envvar;
        } else {
            System.err.println("[getEnvironmentVariable]---> warning the system "
                + " environment " + envvarname + " is not set.");
        	  envvar = System.getProperty(envvarname);
            if(envvar!=null){
                return envvar;
            } else {
                System.err.println("[getEnvironmentVariable]---> warning the system "
                + " property " + envvarname + " is not set.");
            }
        }
        
        return null;
    }

}
