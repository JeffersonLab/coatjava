package org.jlab.detector.qadb;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Iterator;

import org.jlab.detector.scalers.DaqScalersSequence;

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;

/**
 * A sequence of bins for the Quality Assurance Database (QADB).
 * <p>
 * The bins may hold generic data, such as a class instance, accessible by {@link QadbBin#data}; the data
 * type is set by a generic type parameter, and all bins will hold the same type of data.
 * @see QadbBin
 * @author dilks
 */
public class QadbBinSequence<T> extends DaqScalersSequence implements Iterable<QadbBin<T>> {

  /** sequence of QA bins */
  private final List<QadbBin<T>> qaBins = new ArrayList<>();

  // ----------------------------------------------------------------------------------

  /** lambda type to initialize each bin's generic data */
  public interface DataInitializer<T> {
    /**
     * @param n the bin number
     * @return the initial public member {@link QadbBin#data} for bin number {@code n}
     */
    T run(int n);
  }

  // ----------------------------------------------------------------------------------

  /**
   * read a list of HIPO files for a run and generate a sequence of QADB bins.
   * The original sequence of scalers ({@link DaqScalersSequence}) is sampled:
   * <ul>
   * <li> bin boundaries are set such that each bin contains {@code binWidth} consecutive scaler readouts (excluding the first); the last bin may contain less</li>
   * <li> {@link QadbBin} objects are defined for each pair of consecutive bin boundaries</li>
   * <li> an initial (final) {@link QadbBin} object is also defined, for events which occur before (after) the first (last) scaler readout</li>
   * <li> the {@code private} list of scalers becomes filled with ONLY the scaler readouts at the bin boundaries</li>
   * <li> each bin's scaler subsequence is stored within its {@link QadbBin}</li>
   * </ul>
   * @param filenames list of HIPO files to read
   * @param binWidth the number of consecutive scaler-readout intervals in each bin
   * @param initDataFunction a lambda to create the initial data for each bin; must be of the form {@code (binNumber) -> { return initData object }}
   */
  public QadbBinSequence(List<String> filenames, int binWidth, DataInitializer<T> initDataFunction) {
    if(binWidth <= 0)
      throw new RuntimeException("binWidth must be greater than 0");
    // construct the full, sorted scaler sequence
    logger.info("QadbBinSequence::  constructing DAQ scalers sequence");
    this.readFiles(filenames);
    if(this.scalers.isEmpty())
      throw new RuntimeException("scalers sequence is empty");
    // validate ordering: currently, QADBs use event number for lookups, so event number vs. timestamp should monotonically increase
    logger.fine("...validating ordering...");
    if(!this.validateOrdering())
      logger.severe("ERROR: scaler readout ordering is NOT VALID!"); // continue anyway, since the user may still want to see the QADB results
    logger.fine("...done, now constructing QADB bin sequence...");
    logger.fine("  initial sequence size = " + this.scalers.size());
    // add an initial, empty bin; its scaler sequence just contains the first scaler readout
    int binNum = 0;
    this.qaBins.add(new QadbBin<T>(binNum, QadbBin.BinType.FIRST, this.scalers.subList(0, 1), initDataFunction.run(binNum)));
    // sample the original scaler sequence: make a new `QadbBin` for each subsequence
    List<Integer> scalersToKeep = new ArrayList<>(); // list of `scalers` indices to keep, i.e., the ones at the bin boundaries
    scalersToKeep.add(0);
    for(int i=0; i<this.scalers.size(); i+=binWidth) {
      int end = Math.min(i+binWidth, this.scalers.size()-1); // the penultimate bin is allowed to have less than `binWidth` scalers
      binNum = this.qaBins.size();
      this.qaBins.add(new QadbBin<T>(binNum, QadbBin.BinType.INTERMEDIATE, this.scalers.subList(i, end+1), initDataFunction.run(binNum)));
      scalersToKeep.add(end);
    }
    logger.fine("  scalers to keep = " + scalersToKeep);
    // add a final, empty bin; its scaler sequence just contains the last scaler readout
    binNum = this.qaBins.size();
    this.qaBins.add(new QadbBin<T>(binNum, QadbBin.BinType.LAST, this.scalers.subList(this.scalers.size()-1, this.scalers.size()), initDataFunction.run(binNum)));
    // remove all `scalers` elements which are not on bin boundaries
    for(int i=this.scalers.size()-1; i>=0; i--) {
      if(!scalersToKeep.contains(i))
        this.scalers.remove(i);
    }
    logger.fine("  sampled sequence size = " + this.scalers.size());
    logger.fine("  number of QADB bins = " + this.qaBins.size());
  }

  /**
   * alternative constructor, with no {@link QadbBin#data} initialization parameter
   * <p>
   * {@link QadbBin#data} will be initialized to {@code null}
   * @param filenames list of HIPO files to read
   * @param binWidth the number of consecutive scaler-readout intervals in each bin
   */
  public QadbBinSequence(List<String> filenames, int binWidth) {
    this(filenames, binWidth, (binNum)->null);
  }

  // ----------------------------------------------------------------------------------

  /** iterable interface implementation */
  @Override
  public Iterator<QadbBin<T>> iterator() {
    return this.qaBins.iterator();
  }

  /** @return the number of bins in this sequence */
  @Override
  public int size() {
    return this.qaBins.size();
  }

  /**
   * @param idx bin index
   * @return a bin for a given index
   */
  public QadbBin<T> getBin(int idx) {
    return this.qaBins.get(idx);
  }

  // ----------------------------------------------------------------------------------

  /**
   * @return the bin which contains the timestamp, if found
   * @param timestamp the timestamp
   */
  public Optional<QadbBin<T>> findBin(long timestamp) {
    logger.finest(" -> QadbBinSequence.findBin(" + timestamp + ")");
    var idx = this.findIndex(timestamp);
    if(idx>=0 && idx<this.scalers.size() && this.scalers.get(idx).getTimestamp() == timestamp)
      idx--; // if on bin boundary, choose the earlier bin (QADB convention)
    idx++; // add 1, to account for the `FIRST` bin (fenceposting)
    logger.finest(" -> found QADB bin at idx = " + idx);
    return idx>=0 && idx<this.qaBins.size() ? Optional.ofNullable(this.qaBins.get(idx)) : Optional.empty();
  }

  // ----------------------------------------------------------------------------------

  /**
   * correct the first bin's lower bound, if you know it from tag-0 events
   * @param evnumMin the correct minimum event number
   * @param timestampMin the correct minimum timestamp
   */
  public void correctLowerBound(int evnumMin, long timestampMin) {
    this.getBin(0).correctLowerBound(evnumMin, timestampMin);
  }

  /**
   * correct the last bin's upper bound, if you know it from tag-0 events
   * @param evnumMax the correct maximum event number
   * @param timestampMax the correct maximum timestamp
   */
  public void correctUpperBound(int evnumMax, long timestampMax) {
    this.getBin(this.size()-1).correctUpperBound(evnumMax, timestampMax);
  }

  // ----------------------------------------------------------------------------------

  /**
   * Demonstrate how to use this class
   * @param args command-line arguments
   */
  public static void main(String[] args) {

    // parse arguments, which must be a list of HIPO files
    if(args.length == 0)
      throw new RuntimeException("argument(s) must be HIPO file(s)");
    List<String> filenames = new ArrayList<>();
    filenames.addAll(Arrays.asList(args));

    // define a QADB bin sequence
    // - as an example, we have each bin store an integer, which we will use to count the number of tag-0 events in the bin
    // - each bin's integer is initialized to zero; the lambda argument `binNum` represents the bin number, and is unused here
    // - in practice, we can use any data type instead of an integer, such as a class full of histograms
    //   - the lambda argument `binNum` can be used, for example, as part of the histogram titles
    QadbBinSequence<Integer> seq = new QadbBinSequence<>(filenames, 2000, (binNum)->0);
    /* alternatively, if you do not want to store data with this class instance, use `Object` as the type, and no initializer lambda:
    QadbBinSequence<Object> seeq = new QadbBinSequence<>(filenames, 2000);
    for(var bin : seeq) bin.print();
    System.exit(0);
    */

    // apply a charge correction method
    // for(var bin : seq)
    //   bin.correctCharge(QadbBin.ChargeCorrectionMethod.BY_MEAN_LIVETIME);

    // initialize a minimum and maximum event number and timestamp for tag-0 events
    int evnumMin = -1;
    int evnumMax = -1;
    long timestampMin = -1;
    long timestampMax = -1;

    // read the list of HIPO files
    logger.info("===== begin event loop ====");
    for(String filename : filenames) {
      HipoReader reader = new HipoReader();
      reader.setTags(0);
      reader.open(filename);
      SchemaFactory schema = reader.getSchemaFactory();

      // tag-0 event loop
      while(reader.hasNext()) {
        Bank configBank = new Bank(schema.getSchema("RUN::config"));
        Event event = new Event();
        reader.nextEvent(event);
        event.read(configBank);

        // find the bin which contains this event
        if(configBank.getRows()>0) {
          var timestamp = configBank.getLong("timestamp", 0);
          var evnum     = configBank.getInt("event", 0);
          var thisBin   = seq.findBin(timestamp);
          if(thisBin.isPresent()) {
            thisBin.get().data++; // increment the counter for tag-0 events
            evnumMin     = evnumMin     == -1 ? evnum     : Math.min(evnum,     evnumMin); // set event number and timestamp extrema
            evnumMax     = evnumMax     == -1 ? evnum     : Math.max(evnum,     evnumMax);
            timestampMin = timestampMin == -1 ? timestamp : Math.min(timestamp, timestampMin);
            timestampMax = timestampMax == -1 ? timestamp : Math.max(timestamp, timestampMax);
          }
          else logger.warning("WARNING: failed to find a bin containing timestamp " + timestamp);
        }
      }

      reader.close();
    }
    logger.info("===== end event loop ====");

    // correct the first and last bin with the tag-0 event number and timestamp extrema;
    // this is done such that the event number and timestamp ranges are correct for these bins
    seq.correctLowerBound(evnumMin, timestampMin);
    seq.correctUpperBound(evnumMax, timestampMax);

    // print the results: the bin number along with its number of events
    System.out.println(">>> QA BINS <<<");
    for(var bin : seq)
      bin.print((data) -> String.format("%30s %d", "counted tag-0 events:", data), true);
  }

}
