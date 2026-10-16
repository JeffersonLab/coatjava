package org.jlab.io.hipo;

import java.util.ArrayList;
import java.util.Arrays;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Wrapper to add a default schema directory for "hipo-utils -update".
 * 
 */
public class HipoUtilities extends org.jlab.jnp.hipo4.utils.HipoUtilities {
        
    public static void main(String[] args) {

        ArrayList<String> s = new ArrayList<>(Arrays.asList(args));

        if (s.get(0).equals("-update") && !s.contains("-d")) {
            s.add(1,"-d");
            s.add(2,ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
        }

        org.jlab.jnp.hipo4.utils.HipoUtilities.main(s.toArray(String[]::new));
    }

}
