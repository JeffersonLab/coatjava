// a more general monitor, for things like <sinPhiH> or helicity
// - this reads DST files or skim files
// - can be run on slurm
// - note: search for 'CUT' to find which cuts are applied

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;

def filename = args[0];

HipoReader reader = new HipoReader();
reader.setTags(0);
reader.open(filename);
SchemaFactory schema = reader.getSchemaFactory();

while(reader.hasNext()) {
  Bank scalerBank = new Bank(schema.getSchema("RUN::scaler"));
  Event event = new Event();
  reader.nextEvent(event);
  event.read(scalerBank);
  if(scalerBank.getRows()>0) {
    System.out.println("beamCharge=${scalerBank.getFloat('fcup', 0)}   beamChargeGated=${scalerBank.getFloat('fcupgated', 0)}");
  }
}

reader.close();
