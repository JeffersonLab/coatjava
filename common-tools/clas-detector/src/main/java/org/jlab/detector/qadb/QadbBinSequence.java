package org.jlab.detector.qadb;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;
import java.util.logging.Logger;

import org.jlab.detector.scalers.DaqScalersSequence;

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;

/**
 * A sequence of bins for the Quality Assurance Database (QADB).
 * The bins may hold generic data, such as a class instance, accessible by {@link QadbBin#data}.
 * @see QadbBin
 * @author dilks
 */
public class QadbBinSequence<T> extends DaqScalersSequence {

  /** lambda type to initialize each bin's generic data */
  public interface DataInitializer<T> {
    T run(int n);
  }

  /** sequence of QA bins */
  private final List<QadbBin<T>> qaBins = new ArrayList<>();

  /** logger instance */
  private static final Logger logger = Logger.getLogger(QadbBinSequence.class.getName());

  /**
   * Read a list of HIPO files for a run and generate a sequence of QADB bins.
   * The original sequence of scalers ({@link DaqScalersSequence}) is sampled:
   * <ul>
   * <li> bin boundaries are set such that each bin contains {@code binWidth} consecutive scaler readouts (excluding the first); the last bin may contain less</li>
   * <li> {@link QadbBin} objects are defined between each pair of consecutive bin boundaries</li>
   * <li> an initial (final) {@link QadbBin} object is also defined, for events which occur before (after) the first (last) scaler readout</li>
   * <li> the {@code private} list of scalers becomes filled with ONLY the scaler readouts at the bin boundaries</li>
   * <li> each bin's scaler subsequence is stored within its {@link QadbBin}</li>
   * </ul>
   * @param filenames list of HIPO files to read
   * @param binWidth the number of consecutive scaler-readout intervals in each bin
   * @param initDataFunction a lambda to create the initial data for each bin; must be of the form {@code (binNumber) -> { return initData object }}
   */
  public QadbBinSequence(List<String> filenames, int binWidth, DataInitializer<T> initDataFunction) {
    // construct the full, sorted scaler sequence
    logger.info("QadbBinSequence::  constructing DAQ scalers sequence");
    this.readFiles(filenames);
    logger.fine("...done, now constructing QADB bin sequence...");
    // sanity checks
    if(binWidth <= 0)
      throw new RuntimeException("binWidth must be greater than 0");
    logger.fine("  initial sequence size = " + this.scalers.size());
    if(this.scalers.isEmpty())
      throw new RuntimeException("scalers sequence is empty");
    // add an initial, empty bin; its scaler sequence just contains the first scaler readout
    this.qaBins.add(new QadbBin<T>(0, QadbBin.BinType.FIRST, this.scalers.subList(0, 1), initDataFunction.run(0)));
    // sample the original scaler sequence: make a new `QadbBin` for each subsequence
    List<Integer> scalersToKeep = new ArrayList<>(); // list of `scalers` indices to keep, i.e., the ones at the bin boundaries
    scalersToKeep.add(0);
    for(int i=0; i<this.scalers.size(); i+=binWidth) {
      int end = Math.min(i+binWidth, this.scalers.size()-1); // the last sample may be smaller
      int binNum = this.qaBins.size();
      this.qaBins.add(new QadbBin<T>(binNum, QadbBin.BinType.INTERMEDIATE, this.scalers.subList(i, end), initDataFunction.run(binNum)));
      scalersToKeep.add(end);
    }
    logger.fine("  scalers to keep = " + scalersToKeep);
    // add a final, empty bin; its scaler sequence just contains the last scaler readout
    int binNum = this.qaBins.size();
    this.qaBins.add(new QadbBin<T>(binNum, QadbBin.BinType.LAST, this.scalers.subList(this.scalers.size()-1, this.scalers.size()), initDataFunction.run(binNum)));
    // remove all `scalers` elements which are not on bin boundaries
    for (int i=this.scalers.size()-1; i>=0; i--) {
      if (!scalersToKeep.contains(i))
        this.scalers.remove(i);
    }
    logger.fine("  sampled sequence size = " + this.scalers.size());
    logger.fine("  number of QADB bins = " + this.qaBins.size());
  }

  /**
   * print the QA bins
   * @param verbose if {@code true}, print more
   * @param dataPrinter a lambda which resolves each bin's {@link QadbBin#data} as a {@code String}
   */
  public void print(boolean verbose, QadbBin.DataPrinter<T> dataPrinter) {
    System.out.println("QA BINS:");
    for(var qaBin : this.qaBins)
      qaBin.print(verbose, dataPrinter);
  }

  /**
   * @return the bin which contains the timestamp
   * @param timestamp the timestamp
   */
  public Optional<QadbBin<T>> find(long timestamp) {
    logger.finest(" -> QadbBinSequence.find(" + timestamp + ")");
    var idx = this.findIndex(timestamp) + 1; // add 1, to account for the `FIRST` bin
    logger.finest(" -> found QADB bin at idx = " + idx);
    return idx>=0 && idx<this.qaBins.size() ? Optional.ofNullable(this.qaBins.get(idx)) : Optional.empty();
  }

  /**
   * Demonstrate how to use this class
   * @param args command-line arguments
   */
  public static void main(String[] args) {

    if(args.length == 0)
      throw new RuntimeException("arguments must be HIPO file(s)");
    List<String> filenames = new ArrayList<>();
    filenames.addAll(Arrays.asList(args));

    // define a QADB bin sequence
    // - as an example, we have each bin store an integer, which we will use to count the number of events in the bin
    // - each bin's integer is initialized to zero; the lambda argument `n` represents the bin number, and is unused here
    // - in practice, we can use any data type instead of an integer, such as a class full of histograms
    QadbBinSequence<Integer> seq = new QadbBinSequence<>(filenames, 2000, (n)->0);

    // read the list of HIPO files
    logger.info("===== begin event loop ====");
    for(String filename : filenames) {
      HipoReader reader = new HipoReader();
      reader.setTags(0);
      reader.open(filename);
      SchemaFactory schema = reader.getSchemaFactory();

      // event loop
      while(reader.hasNext()) {
        Bank configBank = new Bank(schema.getSchema("RUN::config"));
        Event event=new Event();
        reader.nextEvent(event);
        event.read(configBank);

        // increment each QADB bin's counter
        if(configBank.getRows()>0) {
          var thisBin = seq.find(configBank.getLong("timestamp", 0));
          if(thisBin.isPresent())
            thisBin.get().data++;
        }
      }

      reader.close();
    }
    logger.info("===== end event loop ====");

    // print the results: the bin number along with its number of events
    seq.print(true, (data) -> String.format("%30s %d", "number of events:", data));

  }

}
