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

  /** bin type */
  public enum BinType {
    /** the first bin, for events before the first scaler readout */
    FIRST,
    /** any bin between two scaler readouts */
    INTERMEDIATE,
    /** the last bin, for events after the last scaler readout */
    LAST
  }

  private int binNum;
  private BinType binType;
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
  public QadbBin(int binNum, BinType binType, List<DaqScalers> inputScalers, T initData) {
    super(inputScalers);
    this.binNum       = binNum;
    this.binType      = binType;
    this.data         = initData;
    switch(this.binType) {
      case INTERMEDIATE -> {
        this.timestampMin = this.scalers.get(0).getTimestamp();
        this.timestampMax = this.scalers.get(scalers.size()-1).getTimestamp();
        this.evnumMin     = this.scalers.get(0).getEventNum();
        this.evnumMax     = this.scalers.get(scalers.size()-1).getEventNum();
      }
      case FIRST -> {
        if(this.scalers.size() != 1)
          throw new RuntimeException("a FIRST bin may only have ONE scaler readout");
        this.timestampMin = 0;
        this.timestampMax = this.scalers.get(0).getTimestamp();
        this.evnumMin     = 0;
        this.evnumMax     = this.scalers.get(0).getEventNum();
      }
      case LAST -> {
        if(this.scalers.size() != 1)
          throw new RuntimeException("a LAST bin may only have ONE scaler readout");
        this.timestampMin = this.scalers.get(0).getTimestamp();
        this.timestampMax = 10 * this.timestampMin;
        this.evnumMin     = this.scalers.get(0).getEventNum();
        this.evnumMax     = 10 * this.evnumMin;
      }
    }
  }

  /**
   * print a QA bin and its data
   * @param verbose if {@code true}, print more
   * @param dataPrinter a lambda which resolves {@link data} as a {@code String}
   */
  public void print(boolean verbose, DataPrinter<T> dataPrinter) {
    System.out.printf("BIN %d", this.binNum);
    if(verbose) {
      System.out.printf(" -----------\n");
      System.out.printf("%30s %d to %d, range %d\n", "event number interval:", this.evnumMin, this.evnumMax, this.evnumMax - this.evnumMin);
      System.out.printf("%30s %d to %d, range %d\n", "timestamp interval:", this.timestampMin, this.timestampMax, this.timestampMax - this.timestampMin);
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
