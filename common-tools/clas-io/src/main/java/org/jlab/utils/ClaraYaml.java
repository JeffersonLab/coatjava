package org.jlab.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONException;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

/**
 * Read a CLARA YAML file, convert it to JSON (because that's what COATJAVA
 * uses elsewhere), and provide the same filtering that CLARA does when it
 * presents a service its EngineData.
 * 
 * @author baltzell
 */
public class ClaraYaml {

    private JSONObject json = null;

    public ClaraYaml(String filename) {
        InputStream input;
        try {
            input = new FileInputStream(filename);
            Yaml yaml = new Yaml();
            Map<String, Object> yamlConf = (Map<String, Object>) yaml.load(input);
            this.json = new JSONObject(yamlConf);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ClaraYaml.class.getName()).log(Level.SEVERE, null, ex);
        }
        setStockSchemaDirectory();
    }

    /**
     * Whether it parsed ok.
     * @return 
     */
    public boolean valid() {
        return this.json != null;
    }

    /**
     * Print it to the screen, nicely.
     */
    public void show() {
        JsonUtils.show(this.json);
    }

    /**
     * Print the service's configuration as presented by CLARA.
     * @param serviceName 
     */
    public void showFiltered(String serviceName) {
        JsonUtils.show(this.filter(serviceName));
    }

    /**
     * Get a list of services.
     * @return services 
     */
    public List<JSONObject> services() {
        List<JSONObject> services = new ArrayList<>();
        try {
            for (Object obj : this.json.getJSONArray("services")) {
                if (obj instanceof JSONObject) {
                    services.add( (JSONObject)obj );
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(ClaraYaml.class.getName()).log(Level.SEVERE, null, ex);
        }
        return services;
    }

    public String schemaDirectory() {
        if (json.has("configuration")) {
            JSONObject jcfg = json.getJSONObject("configuration");
            if (jcfg.has("io-services")) {
                JSONObject jio = jcfg.getJSONObject("io-services");
                if (jio.has("writer")) {
                    JSONObject jwri = jio.getJSONObject("writer");
                    if (jwri.has("schema_dir")) {
                        return jwri.getString("schema_dir");
                    }
                }
            }
        }
        return null;
    }

    public String getSchemaDirectory() {
        return schemaDirectory();
    }

    /**
     * Set the schema_dir directory.
     * @param dir path to schema_dir directory
     */ 
    public void setSchemaDirectory(String dir) {
        if (!json.has("configuration"))
            json.put("configuration",new JSONObject());
        if (!json.getJSONObject("configuration").has("io_services")) 
            json.getJSONObject("configuration").put("io-services",new JSONObject());
        if (!json.getJSONObject("configuration").getJSONObject("io-services").has("writer"))
            json.getJSONObject("configuration").getJSONObject("io-services").put("writer",new JSONObject());
        this.json.getJSONObject("configuration").getJSONObject("io-services").
            getJSONObject("writer").put("schema_dir",dir);
    }
   
    /**
     * Set the schema_dir, assuming it's currently the short (basename) of a
     * "stock" schema, but only if it doesn't already look like an absolute path.
     */
    public final void setStockSchemaDirectory() {
        String s = getSchemaDirectory();
        if (s != null && !s.trim().startsWith("/")) 
            setSchemaDirectory(getStockSchemaDirectory(s));
    }

    /**
     * Convert short (base)name into absolute path, for "stock" schema.
     * @param name
     * @return 
     */
    public static String getStockSchemaDirectory(String name) {
        return ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4/singles/"+name);
    }

    /**
     * Get the service's configuration as presented by CLARA.
     * @param serviceName
     * @return json object 
     */
    public JSONObject filter(String serviceName) {
        return ClaraYaml.filter(this.json, serviceName);
    }

    /**
     * Emulate the way CLARA parses the full YAML and presents it in EngineData.
     * The "global" and "service" subsections in the "configuration" section get
     * squashed into one namespace, and service-specific keys override any
     * globals of the same name.
     *
     * @param claraJson the full CLARA YAML contents
     * @param serviceName the name of the service in CLARA YAML (not class name)
     * @return data in the format the given CLARA service would see
     */
    public static JSONObject filter(JSONObject claraJson, String serviceName) {
        JSONObject ret = new JSONObject();
        if (claraJson.has("configuration")) {
            JSONObject config = claraJson.getJSONObject("configuration");
            if (config.has("global")) {
                JSONObject globals = config.getJSONObject("global");
                for (String key : globals.keySet()) {
                    ret.accumulate(key, globals.getString(key));
                }
            }
            if (config.has("services")) {
                if (config.getJSONObject("services").has(serviceName)) {
                    JSONObject service = config.getJSONObject("services").getJSONObject(serviceName);
                    for (String key : service.keySet()) {
                        ret.put(key, service.getString(key));
                    }
                }
            }
        }
        return ret;
    }

    public static void main(String[] args) {
        ClaraYaml yaml = new ClaraYaml(System.getenv("HOME")+"/sw/coatjava/github/etc/services/data-ai.yaml");
        yaml.show();
        yaml.showFiltered("EBTB");
        yaml.setSchemaDirectory("dst");
        yaml.setStockSchemaDirectory();
        yaml.show();
    }

}
