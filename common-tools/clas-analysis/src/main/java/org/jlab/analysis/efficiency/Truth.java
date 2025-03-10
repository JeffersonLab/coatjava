package org.jlab.analysis.efficiency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.utils.json.JsonArray;
import org.jlab.jnp.utils.json.JsonObject;
import org.jlab.utils.options.OptionParser;

/**
 * Efficiency matrix calculator based solely on the MC::GenMatch truth-matching
 * bank (which is purely hit-based), and a pid assignment match in MC::Particle
 * and REC::Particle.
 * 
 * @author baltzell
 */
public class Truth {
   
    static final int UDF = 0;
    static final List<Integer> NEGATIVES = Arrays.asList(11, -211, -321, -2212);
    static final List<Integer> POSITIVES = Arrays.asList(-11, 211, 321, 2212, 45);
    static final List<Integer> NEUTRALS = Arrays.asList(22, 2112);

    List<Integer> validPids;
    Schema mcGenMatch;
    Schema mcParticle;
    Schema recParticle;
    long[][] recTallies;
    long[] mcTallies;

    public Truth(SchemaFactory s) {
        validPids = new ArrayList(NEGATIVES);
        validPids.addAll(POSITIVES);
        validPids.addAll(NEUTRALS);
        validPids.add(UDF);
        mcTallies = new long[validPids.size()];
        recTallies = new long[validPids.size()][validPids.size()];
        mcGenMatch = s.getSchema("MC::GenMatch");
        mcParticle = s.getSchema("MC::Particle");
        recParticle = s.getSchema("REC::Particle");
    }

    /**
     * Get one element of the efficiency matrix.
     * @param truth true PID
     * @param rec reconstructed PID
     * @return probability
     */
    public float get(int truth, int rec) {
        long sum = mcTallies[validPids.indexOf(truth)];
        return sum>0 ? ((float)recTallies[validPids.indexOf(truth)][validPids.indexOf(rec)])/sum : 0;
    }

    /**
     * Add an event in the form of truth and reconstructed particle species.
     * @param truth truth PID
     * @param rec reconstructed PID
     */
    public synchronized void add(int truth, int rec) {
        final int t = validPids.indexOf(truth);
        if (t < 0) return;
        final int r = validPids.indexOf(rec);
        mcTallies[t]++;
        if (r < 0) recTallies[t][UDF]++;
        else recTallies[t][r]++;
    }

    /**
     * Add a HIPO event.
     * @param e 
     */
    public void add(Event e) {
        Bank bm = new Bank(mcParticle);
        Bank br = new Bank(recParticle);
        e.read(bm);
        e.read(br);
        TreeMap<Short,Short> good = getMapping(e);
        for (short row=0; row<bm.getRows(); ++row) {
            if (!good.containsKey(row)) add(bm.getInt("pid",row), UDF);
            else add(bm.getInt("pid",row), br.getInt("pid",good.get(row)));
        }
    }

    /**
     * Add input HIPO files by path.
     * @param filenames
     */
    public void add(List<String> filenames) {
        Event e = new Event();
        for (String f : filenames) {
            HipoReader r = new HipoReader();
            r.open(f);
            while (r.hasNext()) {
                r.nextEvent(e);
                add(e);
            }
        }
    }
   
    /**
     * Truth-matching banks contain pointers to MC::Particle and REC::Particle,
     * and here we cache that mapping to avoid nested loops.
     */
    private TreeMap getMapping(Event e) {
        Bank b = new Bank(mcGenMatch);
        e.read(b);
        TreeMap<Short,Short> m = new TreeMap<>();
        for (int row=0; row<b.getRows(); ++row)
            m.put(b.getShort("mcindex", row), b.getShort("pindex",row));
        return m;
    }

    /**
     * Get efficiencies as a plain, human-readable table.
     * @return 
     */
    public String toTable() {
        StringBuilder s = new StringBuilder();
        s.append("      ");
        for (int i=0; i<validPids.size(); ++i) {
            s.append(String.format("%7d",validPids.get(i)));
            if (validPids.size()==i+1) s.append("\n");
        }
        for (int i=0; i<validPids.size(); ++i) {
            s.append(String.format("\n%6d",validPids.get(i)));
            for (int j=0; j<validPids.size(); ++j) {
                if (mcTallies[i] > 0)
                    s.append(String.format("%7.4f",get(validPids.get(i),validPids.get(j))));
                else
                    s.append(String.format("%7s","-"));
            }
        }
        return s.toString();
    }

    /**
     * Get efficiencies as a JSON object.
     * @return 
     */
    public JsonObject toJson() {
        JsonObject effs = new JsonObject();
        JsonArray pids = new JsonArray();
        JsonObject gens = new JsonObject();
        for (int i=0; i<validPids.size(); ++i) {
            pids.add(validPids.get(i));
            JsonArray a = new JsonArray();
            for (int j=0; j<validPids.size(); ++j)
                a.add(get(validPids.get(i),validPids.get(j)));
            effs.add(Integer.toString(validPids.get(i)),a);
            gens.add(Integer.toString(validPids.get(i)), mcTallies[i]);
        }
        JsonObject ret = new JsonObject();
        ret.add("pids", pids);
        ret.add("effs", effs);
        ret.add("gens", gens);
        return ret;
    }

    /**
     * Get efficiencies as a Markdown table.
     * @return 
     */
    public String toMarkdown() {
        StringBuilder s = new StringBuilder();
        s.append("|");
        for (int i=0; i<validPids.size(); ++i) {
            s.append(String.format("%d|",validPids.get(i)));
            if (validPids.size()==i+1) s.append("\n");
        }
        s.append("|");
        for (int i=0; i<validPids.size(); ++i) s.append(" --- |");
        s.append("\n");
        for (int i=0; i<validPids.size(); ++i) {
            s.append(String.format("|%d|",validPids.get(i)));
            for (int j=0; j<validPids.size(); ++j) {
                if (mcTallies[i] > 0)
                    s.append(String.format("%f|",get(validPids.get(i),validPids.get(j))));
                else
                    s.append("|");
            }
        }
        return s.toString();
    }

    public static void main(String[] args) {
        OptionParser o = new OptionParser("trutheff");
        o.setRequiresInputList(true);
        o.parse(args);
        HipoReader r = new HipoReader();
        r.open(o.getInputList().get(0));
        Truth t = new Truth(r.getSchemaFactory());
        t.add(o.getInputList());
        System.out.println(t.toTable());
        System.out.println(t.toJson());
        System.out.println(t.toMarkdown());
    }

}