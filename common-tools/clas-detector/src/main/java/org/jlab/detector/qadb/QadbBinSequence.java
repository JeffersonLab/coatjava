package org.jlab.detector.qadb;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import org.jlab.detector.scalers.DaqScalersSequence;

/**
 * A sequence of bins for the Quality Assurance Database (QADB).
 * The bins may hold generic data, such as a class instance, accessible by {@link QadbBin#getData} and {@link QadbBin#setData}.
 * @see QadbBin
 * @author dilks
 */
public class QadbBinSequence<T> extends DaqScalersSequence {

  /** sequence of QA bins */
  private final List<QadbBin<T>> qaBins = new ArrayList<>();

  /**
   * @param filenames list of HIPO files to read
   * @param binWidth the number of scaler readouts in each bin
   */
  public QadbBinSequence(List<String> filenames, int binWidth) {
    if(binWidth <= 0)
      throw new RuntimeException("binWidth must be greater than 0");
    this.readFiles(filenames);
    System.out.println("DEBUG: original size = " + this.scalers.size()); // FIXME: remove
    if(this.scalers.isEmpty())
      throw new RuntimeException("scalers is empty");
    List<Integer> keep = new ArrayList<>();
    keep.add(0);
    for(int i=0; i<this.scalers.size(); i+=binWidth) {
      int end = Math.min(i+binWidth, this.scalers.size()-1); // the last sample may be smaller
      this.qaBins.add(new QadbBin<T>(this.qaBins.size(), this.scalers.subList(i, end)));
      keep.add(end);
    }
    System.out.println("DEBUG: keep indices = " + keep); // FIXME: remove
    for (int i=this.scalers.size()-1; i>=0; i--) {
      if (!keep.contains(i))
        this.scalers.remove(i);
    }
    System.out.println("DEBUG: sampled size = " + this.scalers.size()); // FIXME: remove
    System.out.println("DEBUG: num qaBins = " + this.qaBins.size()); // FIXME: remove
  }

  /** print the QA bins */
  public void print() {
    System.out.println("QA BINS:");
    for(var qaBin : this.qaBins)
      qaBin.print();
  }

  /**
   * @return the bin which contains the timestamp
   * @param timestamp the timestamp
   */
  public Optional<QadbBin<T>> find(long timestamp) {
    var idx = this.findIndex(timestamp);
    return Optional.ofNullable(this.qaBins.get(idx));
  }

}
