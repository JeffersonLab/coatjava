package org.jlab.detector.qadb;

import java.util.List;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.scalers.DaqScalersSequence;

/**
 * A single bin for the Quality Assurance Database (QADB).
 * It may hold arbitrary data, such as a class instance, accessible by public member {@link QadbBin#data};
 * its type is set by a generic type parameter.
 * <p>
 * A bin contains a (sub)sequence of scaler readouts, and therefore extends {@link DaqScalersSequence}.
 * @see QadbBinSequence
 * @author dilks
 */
public class QadbBin<T> extends DaqScalersSequence {

  private int     binNum;
  private BinType binType;
  private int     evnumMin;
  private int     evnumMax;
  private long    timestampMin;
  private long    timestampMax;
  private double  charge; // ungated
  private double  chargeGated;

  /** arbitrary data that may be held by this bin; it is just public so the user can do anything with it */
  public T data;

  // ----------------------------------------------------------------------------------

  /** lambda type to print each bin's generic data as a string */
  public interface DataPrinter<T> {
    /**
     * @param data the public member {@link QadbBin#data}
     * @return a String representation of {@link QadbBin#data}
     */
    String run(T data);
  }

  /** bin type */
  public enum BinType {
    /** the first bin, for events before the first scaler readout */
    FIRST,
    /** any bin between two scaler readouts */
    INTERMEDIATE,
    /** the last bin, for events after the last scaler readout */
    LAST,
  }

  /** charge type */
  public enum ChargeType {
    /** full charge, DAQ-ungated */
    UNGATED,
    /** DAQ-gated charge */
    GATED,
  }

  // ----------------------------------------------------------------------------------

  /**
   * construct a single bin
   * @param binNum the bin number, in the {@link QadbBinSequence} which contains this bin
   * @param binType the bin type (see {@link BinType})
   * @param inputScalers the scaler sequence for this bin
   * @param initData the initial data for this bin (sets public member {@link data})
   */
  public QadbBin(int binNum, BinType binType, List<DaqScalers> inputScalers, T initData) {
    super(inputScalers);
    this.binNum  = binNum;
    this.binType = binType;
    this.data    = initData;
    switch(this.binType) {
      case INTERMEDIATE -> {
        this.timestampMin = this.scalers.get(0).getTimestamp();
        this.timestampMax = this.scalers.get(scalers.size()-1).getTimestamp();
        this.evnumMin     = this.scalers.get(0).getEventNum();
        this.evnumMax     = this.scalers.get(scalers.size()-1).getEventNum();
        this.charge       = this.getInterval().getBeamCharge();
        this.chargeGated  = this.getInterval().getBeamChargeGated();
      }
      case FIRST -> {
        if(this.scalers.size() != 1)
          throw new RuntimeException("a FIRST bin may only have ONE scaler readout");
        this.timestampMin = 0; // user may correct this using `correctLowerBound`
        this.timestampMax = this.scalers.get(0).getTimestamp();
        this.evnumMin     = 0; // user may correct this using `correctLowerBound`
        this.evnumMax     = this.scalers.get(0).getEventNum();
        this.charge       = 0; // since no lower bound
        this.chargeGated  = 0; // since no lower bound
      }
      case LAST -> {
        if(this.scalers.size() != 1)
          throw new RuntimeException("a LAST bin may only have ONE scaler readout");
        this.timestampMin = this.scalers.get(0).getTimestamp();
        this.timestampMax = 10 * this.timestampMin; // user may correct this using `correctUpperBound`
        this.evnumMin     = this.scalers.get(0).getEventNum();
        this.evnumMax     = 10 * this.evnumMin; // user may correct this using `correctUpperBound`
        this.charge       = 0; // since no upper bound
        this.chargeGated  = 0; // since no upper bound
      }
    }
  }

  // ----------------------------------------------------------------------------------

  /**
   * construct a single bin, with bin _boundary_ info only (no charge info)
   * @param binNum the bin number, in the {@link QadbBinSequence} which contains this bin
   * @param binType the bin type (see {@link BinType})
   * @param evnumMin the minimum event number
   * @param evnumMax the maximum event number
   * @param timestampMin the minimum timestamp
   * @param timestampMax the maximum timestamp
   */
  public QadbBin(int binNum, BinType binType, int evnumMin, int evnumMax, long timestampMin, long timestampMax)
  {
    this.binNum       = binNum;
    this.binType      = binType;
    this.evnumMin     = evnumMin;
    this.evnumMax     = evnumMax;
    this.timestampMin = timestampMin;
    this.timestampMax = timestampMax;
  }

  // ----------------------------------------------------------------------------------

  /** @return the bin number for this bin */
  public int getBinNum() { return this.binNum; }

  /** @return minimum timestamp for this bin */
  public long getTimestampMin() { return this.timestampMin; }

  /** @return maximum timestamp for this bin */
  public long getTimestampMax() { return this.timestampMax; }

  /** @return minimum event number for this bin */
  public int getEventNumMin() { return this.evnumMin; }

  /** @return maximum event number for this bin */
  public int getEventNumMax() { return this.evnumMax; }

  /** @return the beam charge, not gated by DAQ, for this bin */
  public double getBeamCharge() { return this.charge; }

  /** @return the beam charge, gated by DAQ, for this bin */
  public double getBeamChargeGated() { return this.chargeGated; }

  /**
   * @return the beam charge, gated or ungated
   * @param chargeType the type of charge
   */
  public double getBeamCharge(ChargeType chargeType) {
    return switch(chargeType) {
      case UNGATED -> this.getBeamCharge();
      case GATED   -> this.getBeamChargeGated();
    };
  }

  // ----------------------------------------------------------------------------------

  /** @return the mean livetime for this bin */
  public double getMeanLivetime() {
    double sumLivetime   = 0;
    int numLivetimeEvents = 0;
    for(int i=1; i<scalers.size(); i++) { // skip the first scaler, since that's for the previous bin
      var livetime = scalers.get(i).dsc2.getLivetime();
      if(livetime >= 0) { // filter out livetime = -1
        sumLivetime += livetime;
        numLivetimeEvents++;
      }
    }
    return numLivetimeEvents > 0 ? sumLivetime / numLivetimeEvents : 0;
  }

  /** @return the duration of the bin, in seconds */
  public double getDuration() {
    return (this.getTimestampMax() - this.getTimestampMin()) * 4e-9; // convert timestamp units [4ns] -> [s]
  }

  // ----------------------------------------------------------------------------------

  /** charge correction method */
  public enum ChargeCorrectionMethod {
    /** interchange the DAQ-gated and ungated charges */
    BY_FLIP,
    /** calculate the DAQ-gated charge as the mean livetime multiplied by the ungated charge */
    BY_MEAN_LIVETIME,
  }

  /**
   * correct the beam charge for this bin, using a correction method
   * @param method the correction method to use
   * @see ChargeCorrectionMethod
   */
  public void correctCharge(ChargeCorrectionMethod method) {
    logger.fine("correcting beam charge for bin " + this.binNum + " using method " + method);
    logger.fine("  before:   gated = " + this.chargeGated);
    logger.fine("          ungated = " + this.charge);
    switch(method) {
      case BY_FLIP -> { // interchange the gated and ungated charge
        var tmp          = this.charge;
        this.charge      = this.chargeGated;
        this.chargeGated = tmp;
      }
      case BY_MEAN_LIVETIME -> { // gated = <livetime> * ungated
        var meanLivetime = this.getMeanLivetime();
        logger.fine("    mean livetime = " + meanLivetime);
        this.chargeGated = meanLivetime * this.charge;
      }
    }
    logger.fine("  after:    gated = " + this.chargeGated);
    logger.fine("          ungated = " + this.charge);
  }

  /**
   * correct the beam charge for this bin, using specific values from the caller
   * @param charge the charge, not gated by the DAQ
   * @param chargeGated the DAQ-gated charge
   */
  public void correctCharge(double charge, double chargeGated) {
    logger.fine("correcting beam charge for bin " + this.binNum + " using user-specified values");
    logger.fine("  before:   gated = " + this.chargeGated);
    logger.fine("          ungated = " + this.charge);
    this.charge      = charge;
    this.chargeGated = chargeGated;
    logger.fine("  after:    gated = " + this.chargeGated);
    logger.fine("          ungated = " + this.charge);
  }

  // ----------------------------------------------------------------------------------

  /**
   * correct the first bin's lower bound, if you know it from tag-0 events
   * @param evnumMin the correct minimum event number
   * @param timestampMin the correct minimum timestamp
   */
  public void correctLowerBound(int evnumMin, long timestampMin) {
    if(this.binType == BinType.FIRST) {
      this.evnumMin     = evnumMin;
      this.timestampMin = timestampMin;
    }
    else logger.warning("not allowed to correct the lower bound of a bin with type " + this.binType);
  }

  /**
   * correct the last bin's upper bound, if you know it from tag-0 events
   * @param evnumMax the correct maximum event number
   * @param timestampMax the correct maximum timestamp
   */
  public void correctUpperBound(int evnumMax, long timestampMax) {
    if(this.binType == BinType.LAST) {
      this.evnumMax     = evnumMax;
      this.timestampMax = timestampMax;
    }
    else logger.warning("not allowed to correct the upper bound of a bin with type " + this.binType);
  }

  // ----------------------------------------------------------------------------------

  /** extremum type, used with {@link QadbBin#getChargeExtremum} */
  public enum ExtremumType {
    /** from the first scaler readout */
    FIRST,
    /** from the last scaler readout */
    LAST,
    /** the maximum */
    MAX,
    /** the minimum */
    MIN,
  }

  /**
   * Get the min/max or initial/final charge.
   * <p>
   * WARNING: this is likely NOT corrected by {@link correctCharge}
   * @param extremumType the type of extremum
   * @param chargeType the type of charge
   * @return the charge for the given extremum
   */
  public double getChargeExtremum(ExtremumType extremumType, ChargeType chargeType) {
    return switch(extremumType) {
      case FIRST -> getDsc2Charge(this.scalers.get(0), chargeType);
      case LAST  -> getDsc2Charge(this.scalers.get(this.scalers.size()-1), chargeType);
      case MIN   -> getDsc2Charge(this.scalers.stream().reduce((a,b) -> getDsc2Charge(a, chargeType) < getDsc2Charge(b, chargeType) ? a : b).get(), chargeType);
      case MAX   -> getDsc2Charge(this.scalers.stream().reduce((a,b) -> getDsc2Charge(a, chargeType) > getDsc2Charge(b, chargeType) ? a : b).get(), chargeType);
    };
  }

  /** helper method for {@link getChargeExtremum}
   * @param ds the scaler readout
   * @param chargeType the charge type
   * @return the charge from this scaler object
   */
  private static double getDsc2Charge(DaqScalers ds, ChargeType chargeType) {
    return switch(chargeType) {
      case UNGATED -> ds.dsc2.getBeamCharge();
      case GATED   -> ds.dsc2.getBeamChargeGated();
    };
  }

  // ----------------------------------------------------------------------------------

  /** print a QA bin, and some basic information */
  public void print() {
    System.out.printf("BIN %d", this.getBinNum());
    System.out.printf(" -----------\n");
    System.out.printf("%30s %d to %d\n", "timestamp interval:", this.getTimestampMin(), this.getTimestampMax());
    System.out.printf("%30s %d to %d\n", "event number interval:", this.getEventNumMin(), this.getEventNumMax());
    System.out.printf("%30s %f s\n", "duration:", this.getDuration());
    System.out.printf("%30s %d events\n", "event number range:", this.getEventNumMax() - this.getEventNumMin());
    System.out.printf("%30s %f / %f\n", "beam charge gated / ungated:", this.getBeamChargeGated(), this.getBeamCharge());
  }

  /**
   * print a QA bin's stored {@link data}
   * @param dataPrinter a lambda which resolves {@link data} as a {@code String}
   */
  public void print(DataPrinter<T> dataPrinter) {
    System.out.printf("BIN %d :: ", this.getBinNum());
    System.out.println(dataPrinter.run(this.data));
  }

  /**
   * print a QA bin's stored {@link data}, and optionally the bin's basic information
   * @param dataPrinter a lambda which resolves {@link data} as a {@code String}
   * @param verbose if {@code true}, print more
   */
  public void print(DataPrinter<T> dataPrinter, boolean verbose) {
    if(verbose) {
      this.print();
      System.out.println(dataPrinter.run(this.data));
    }
    else
      this.print(dataPrinter);
  }

}
