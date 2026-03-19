package org.jlab.detector.scalers;

import org.jlab.utils.groups.IndexedTable;

public class DaqScaler {

    protected static final String RAWBANKNAME="RAW::scaler";
    protected static final int CRATE=64;

    protected double clockFreq=1;   // Hz
    protected long fcup=-1;         // counts
    protected long clock=-1;        // counts
    protected long slm=-1;          // counts
    protected long gatedFcup=-1;    // counts
    protected long gatedClock=-1;   // counts
    protected long gatedSlm=-1;     // counts

    public final void   setClock(long c) { this.clock = c; }
    public final void   setGatedClock(long c) { this.gatedClock = c; }
    public final long   getClock()       { return this.clock; }
    public final long   getFcup()        { return this.fcup; }
    public final long   getSlm()         { return this.slm; }
    public final long   getGatedClock()  { return this.gatedClock; }
    public final long   getGatedFcup()   { return this.gatedFcup; }
    public final long   getGatedSlm()    { return this.gatedSlm; }
    public final double getClockSeconds()   { return (double)this.clock / this.clockFreq; }
    public final double getGatedClockSeconds() { return (double)this.gatedClock / this.clockFreq; }
    public final double getLivetimeClock() { return (double)this.gatedClock / this.clock; }
    public final double getLivetimeFcup() {return (double)this.gatedFcup / this.fcup; } 
    public final double getLivetimeSLM() {return (double)this.gatedSlm / this.slm; } 

    protected double beamCharge=0;
    protected double beamChargeGated=0;
    protected double beamChargeSLM=0;
    protected double beamChargeGatedSLM=0;
    protected double livetime=0;
    protected void setBeamCharge(double q) { this.beamCharge=q; }
    protected void setBeamChargeGated(double q) { this.beamChargeGated=q; }
    protected void setBeamChargeSLM(double q) { this.beamChargeSLM=q; }
    protected void setBeamChargeGatedSLM(double q) { this.beamChargeGatedSLM=q; }
    protected void setLivetime(double l) { this.livetime=l; }
    public double getBeamCharge() { return beamCharge; }
    public double getBeamChargeGated() { return beamChargeGated; }
    public double getLivetime()   { return livetime; }
    public double getBeamChargeSLM() { return beamChargeSLM; }
    public double getBeamChargeGatedSLM() { return beamChargeGatedSLM; }

    @Override
    public String toString() {
        return String.format("clock=%d/%d fcup=%d/%d slm=%d/%d",clock,gatedClock,fcup,gatedFcup,slm,gatedSlm);
    }

    /**
     * Add raw values from another scaler reading to this one.
     * Note, "calibrate" will need to be called afterwards.
     * @param other 
     */
    public void add(DaqScaler other) {
        this.fcup += other.fcup;
        this.slm += other.slm;
        this.clock += other.clock;
        this.gatedFcup += other.gatedFcup;
        this.gatedClock += other.gatedClock;
        this.gatedSlm += other.gatedSlm;
    }

    /**
     * Manually choose dwell and live-dwell times, e.g. if clock rolls over.
     * @param fcupTable /runcontrol/fcup CCDB table
     * @param slmTable  /runcontrol/slm CCDB table
     * @param seconds dwell time
     * @param liveSeconds live dwell time
     */
    protected void calibrate(IndexedTable fcupTable,IndexedTable slmTable,double seconds,double liveSeconds) {

        if (this.clock > 0) {

            // complain if a hard-coded clock frequency was used
            String prefix = String.format("clockbug [%s]", this.getClass().getSimpleName());
            boolean clockbug = false;
            if(Math.abs(this.clockFreq - ((1e6)+1)) < 0.1) {
              System.err.println(String.format("%s: used hard-coded clockFreq from Dsc2Scaler(bank,table,table,seconds)", prefix));
              clockbug = true;
            }
            else if(Math.abs(this.clockFreq - ((1e6)+2)) < 0.1) {
              System.err.println(String.format("%s: used hard-coded clockFreq from StruckScaler()", prefix));
              clockbug = true;
            }
            else if(Math.abs(this.clockFreq - ((1e6)+3)) < 0.1) {
              System.err.println(String.format("%s: used hard-coded clockFreq from StruckScaler(table,table,table)", prefix));
              clockbug = true;
            }
            else
              System.err.println(String.format("%s: clockFreq OK (value=%f)", prefix, this.clockFreq));

            // the hard-coded clock frequency is 1 MHz, but if it was used, it'll be 1 MHz + a few Hz; correct it now, along
            // with the `seconds` and `liveSeconds`
            if(clockbug) {
              seconds     *= this.clockFreq / 1e6;
              liveSeconds *= this.clockFreq / 1e6;
              this.clockFreq = 1e6;
            }

            // print the clock
            System.err.println(String.format("%s: toString: %s", prefix, this.toString()));

            final double fcup_slope  = fcupTable.getDoubleValue("slope",0,0,0);  // Hz/nA
            final double fcup_offset = fcupTable.getDoubleValue("offset",0,0,0); // Hz
            final double fcup_atten  = fcupTable.getDoubleValue("atten",0,0,0);  // attenuation
            final double slm_slope   = slmTable.getDoubleValue("slope",0,0,0);   // Hz/nA
            final double slm_offset  = slmTable.getDoubleValue("offset",0,0,0);  // Hz
            final double slm_atten   = slmTable.getDoubleValue("atten",0,0,0);   // attenuation

            double q  = (double)this.slm      - slm_offset * seconds;
            double qg = (double)this.gatedSlm - slm_offset * liveSeconds;
            this.beamChargeSLM = q * slm_atten / slm_slope;
            this.beamChargeGatedSLM = qg * slm_atten / slm_slope;
            this.livetime = (double)this.gatedClock / this.clock;

            if (fcup_atten<1e-8 || fcup_slope<1e-8) {
                this.beamCharge = this.beamChargeSLM;
                this.beamChargeGated = this.beamChargeGatedSLM;
            }
            else {
                q  = (double)this.fcup      - fcup_offset * seconds;
                qg = (double)this.gatedFcup - fcup_offset * liveSeconds;
                this.beamCharge = q * fcup_atten / fcup_slope;
                this.beamChargeGated = qg * fcup_atten / fcup_slope;
            }

            // print the beam charge
            System.err.println(String.format("%s: clockFreq=%f  beamCharge=%f  beamChargeGated=%f", prefix, this.clockFreq, this.beamCharge, this.beamChargeGated));
        }
    }

    /**
     * Use the scaler's own clock to get dwell and live-dwell times
     * @param fcupTable /runcontrol/fcup CCDB table
     * @param slmTable  /runcontrol/slm CCDB table
     */
    protected final void calibrate(IndexedTable fcupTable,IndexedTable slmTable) {
        this.calibrate(fcupTable,slmTable,this.getClockSeconds(),this.getGatedClockSeconds());
    }
}
