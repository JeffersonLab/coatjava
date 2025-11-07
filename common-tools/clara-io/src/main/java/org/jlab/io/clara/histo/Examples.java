package org.jlab.io.clara.histo;

import org.jlab.groot.data.H1F;
import org.jlab.jnp.hipo4.data.Event;

public class Examples {

    public static class DumbHistoMaker extends Hister {
        H1F q2 = new H1F("q2","Q^{2}",100,0,5);
        H1F m2 = new H1F("m2","M^{2}",100,0,5);
        @Override
        public void fill(Event event) {
            q2.fill(0.1);
        }
        @Override
        public void configure() {
            add("/TEST/dir1", q2);
            add("/TEST/dir2", m2);
        }
    }

}
