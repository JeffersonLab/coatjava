package org.jlab.detector.qadb;

import java.util.List;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.scalers.DaqScalersSequence;

/**
 * A single bin for the Quality Assurance Database (QADB).
 * It may hold arbitrary data, such as a class instance, accessible by public member {@link QadbBin#data}.
 * A bin contains a (sub)sequence of scaler readouts, and therefore extends {@link DaqScalersSequence}.
 * @see QadbBinSequence
 * @author dilks
 */
public class QadbBin<T> extends DaqScalersSequence {

  /** lambda type to print each bin's generic data as a string */
  public interface DataPrinter<T> {
    String run(T data);
  }

  private int binNum;
  private int evnumMin;
  private int evnumMax;
  private long timestampMin;
  private long timestampMax;

  /** arbitrary data that may be held by this bin; it is just public so the user can do anything with it */
  public T data;

  /**
   * construct a single bin
   * @param binNum the bin number, in the {@link QadbBinSequence} which contains this bin
   * @param inputScalers the scaler sequence for this bin
   * @param initData the initial data for this bin
   */
  public QadbBin(int binNum, List<DaqScalers> inputScalers, T initData) {
    super(inputScalers);
    this.binNum       = binNum;
    this.data         = initData;
    this.timestampMin = this.scalers.get(0).getTimestamp();
    this.timestampMax = this.scalers.get(scalers.size()-1).getTimestamp();
    this.evnumMin     = this.scalers.get(0).getEventNum();
    this.evnumMax     = this.scalers.get(scalers.size()-1).getEventNum();
  }

  /**
   * print a QA bin and its data
   * @param verbose if {@code true}, print more
   * @param dataPrinter a lambda which resolves {@link data} as a {@code String}
   */
  public void print(boolean verbose, DataPrinter<T> dataPrinter) {
    System.out.printf("BIN %d", this.binNum);
    if(verbose) {
      System.out.printf("\n");
      System.out.printf("event number range: %d to %d\n", this.evnumMin, this.evnumMax);
      System.out.printf("timestamp range:    %d to %d\n", this.timestampMin, this.timestampMax);
      // FIXME: add charges etc.
    } else {
      System.out.printf(" :: ");
    }
    System.out.println(dataPrinter.run(this.data));
  }

  /** @return minimum timestamp for this bin */
  public long getTimestampMin() { return this.timestampMin; }

  /** @return maximum timestamp for this bin */
  public long getTimestampMax() { return this.timestampMax; }

  /** @return minimum event number for this bin */
  public long getEventNumMin() { return this.evnumMin; }

  /** @return maximum event number for this bin */
  public long getEventNumMax() { return this.evnumMax; }

  /**
   * @param timestamp the timestamp
   * @return {@code true} if the bin contains this timestamp
   */
  public boolean containsTimestamp(long timestamp) {
    return timestamp >= this.timestampMin && timestamp <= this.timestampMax;
  }

}
