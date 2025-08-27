package org.jlab.detector.qadb;

import java.util.List;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.scalers.DaqScalersSequence;

/**
 * A single bin for the Quality Assurance Database (QADB).
 * It may hold arbitrary data, such as a class instance, accessible by {@link getData} and {@link setData}.
 * A bin contains a (sub)sequence of scaler readouts, and therefore extends {@link DaqScalersSequence}.
 * @see QadbBinSequence
 * @author dilks
 */
public class QadbBin<T> extends DaqScalersSequence {

  private int binNum;
  private int evnumMin;
  private int evnumMax;
  private long timestampMin;
  private long timestampMax;

  private T binData;

  /**
   * construct a single bin
   * @param binNum the bin number, in the {@link QaBinSequence} which contains this bin
   * @param inputScalers the scaler sequence for this bin
   */
  public QadbBin(int binNum, List<DaqScalers> inputScalers) {
    super(inputScalers);
    this.binNum       = binNum;
    this.timestampMin = this.scalers.get(0).getTimestamp();
    this.timestampMax = this.scalers.get(scalers.size()-1).getTimestamp();
    this.evnumMin     = this.scalers.get(0).getEventNum();
    this.evnumMax     = this.scalers.get(scalers.size()-1).getEventNum();
  }

  /** @param binData the data to be associated with this bin */
  public void setData(T binData) {
    this.binData = binData;
  }

  /** @return the data associated with this bin */
  public T getData() {
    return binData;
  }

  /**
   * print a QA bin
   * @param printNames if {@code true}, print the variable names too
   */
  public void print(boolean printNames) {
    if(printNames)
      System.out.printf("%15s %15s %15s\n",
          "bin",
          "q_gated",
          "q_corrected"
          );
    System.out.printf("%15d %15.5f %15.5f\n",
        this.binNum,
        this.getInterval().getBeamChargeGated(),
        this.getBeamChargeLivetimeWeighted()
        );
  }

  /** print a QA bin; include header if bin 0 */
  public void print() {
    this.print(this.binNum==0);
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
