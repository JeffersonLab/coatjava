package org.jlab.rec.mucal;

import org.jlab.utils.groups.IndexedTable;



public class MUCALHit implements Comparable<MUCALHit>{
	// class implements Comparable interface to allow for sorting a collection of hits by Edep values
	public static final int REFCOMPONENT =245;
        
	// constructor 
	public MUCALHit(int i, int ICOMPONENT, int ADC, int TDC, IndexedTable charge2Energy, IndexedTable timeOffsets, IndexedTable timeWalk, IndexedTable cluster) {
		this._COMPONENT = ICOMPONENT;
		this._IDY = ((int) ICOMPONENT/44) + 1;
		this._IDX = ICOMPONENT + 1 - (this._IDY-1)*44;
		this._ADC = ADC;
		this._TDC = TDC;
		
                this._Charge = ((double) this._ADC)*charge2Energy.getDoubleValue("fadc_to_charge", 1,1,REFCOMPONENT);
		this.set_Edep(this._Charge*charge2Energy.getDoubleValue("mips_energy", 1,1,REFCOMPONENT)
				          /charge2Energy.getDoubleValue("mips_charge", 1,1,REFCOMPONENT)/1000.);
                double twCorr=0;
                if(this._Charge>0) {
                    twCorr = timeWalk.getDoubleValue("amplitude", 1,1,REFCOMPONENT)*Math.exp(-this._Charge*timeWalk.getDoubleValue("lambda", 1,1,REFCOMPONENT));
                }
		this.set_Time(((double) this._TDC)/MUCALConstants.TIMECONVFAC
                                                 -(MUCALConstants.CRYS_LENGTH-cluster.getDoubleValue("depth_z", 1,1,0))/MUCALConstants.VEFF
						 -timeOffsets.getDoubleValue("time_offset", 1,1,REFCOMPONENT)-twCorr);
//		if(this.get_Edep()>0.1) System.out.println(ICOMPONENT + " " + this._TDC + " " + 
//				MUCALConstantsLoader.TIMECONVFAC + " " + MUCALConstantsLoader.time_offset[0][0][ICOMPONENT-1] + " " +
//				this.get_Time());
		this.set_Dx( (this._IDX-MUCALConstants.CRYS_DELTA )* MUCALConstants.CRYS_WIDTH);
		this.set_Dy( (this._IDY-MUCALConstants.CRYS_DELTA )* MUCALConstants.CRYS_WIDTH);
		this.set_Dz(MUCALConstants.CRYS_ZPOS+cluster.getDoubleValue("depth_z", 1,1,0));
		this.set_DGTZIndex(i);
		this.set_ClusID(0);
	}

	public MUCALHit(int i, int ICOMPONENT, int ADC, float time, IndexedTable charge2Energy, IndexedTable timeOffsets, IndexedTable timeWalk, IndexedTable cluster) {
		this._COMPONENT = ICOMPONENT;
		this._IDY = ((int) ICOMPONENT/44) + 1;
		this._IDX = ICOMPONENT + 1 - (this._IDY-1)*44;
		this._ADC = ADC;
		
                this._Charge = ((double) this._ADC)*charge2Energy.getDoubleValue("fadc_to_charge", 1,1,REFCOMPONENT);
		this.set_Edep(this._Charge*charge2Energy.getDoubleValue("mips_energy", 1,1,REFCOMPONENT)
				          /charge2Energy.getDoubleValue("mips_charge", 1,1,REFCOMPONENT)/1000.);
                
                double twCorr=0;
                if(this._Charge>0) {
                    twCorr = timeWalk.getDoubleValue("amplitude", 1,1,REFCOMPONENT)*Math.exp(-this._Charge*timeWalk.getDoubleValue("lambda", 1,1,REFCOMPONENT));
                }
		
                this.set_Time(time -(MUCALConstants.CRYS_LENGTH-cluster.getDoubleValue("depth_z", 1,1,0))/MUCALConstants.VEFF
				   -timeOffsets.getDoubleValue("time_offset", 1,1,REFCOMPONENT)-twCorr); 
//		if(this.get_Edep()>0.1) System.out.println(ICOMPONENT + " " + this._TDC + " " + 
//				MUCALConstantsLoader.TIMECONVFAC + " " + MUCALConstantsLoader.time_offset[0][0][ICOMPONENT-1] + " " +
//				this.get_Time());
		this.set_Dx( (this._IDX-MUCALConstants.CRYS_DELTA )* MUCALConstants.CRYS_WIDTH);
		this.set_Dy( (this._IDY-MUCALConstants.CRYS_DELTA )* MUCALConstants.CRYS_WIDTH);
		this.set_Dz(MUCALConstants.CRYS_ZPOS+cluster.getDoubleValue("depth_z", 1,1,0));
		this.set_DGTZIndex(i);
		this.set_ClusID(0);
	}

	private int _COMPONENT;		         	//	   Component number
	private int _IDX;    	 				//	   Crystal ID: X
	private int _IDY;    	 				//	   Crystal ID: Y
	private int _ADC;    	 				//	   ADC
	private int _TDC;    	 				//	   TDC 
		
	private double _Charge;      				//	   Reconstructed energy deposited by the hit in the crystal 
	private double _Edep;      				//	   Reconstructed energy deposited by the hit in the crystal 
	private double _Time;      				//	   Reconstructed time, for now it is the gemc time
	private double _Dx;
	private double _Dy;
	private double _Dz;
	private int    _DGTZIndex;				//		Pointer to cluster
	private int    _ClusIndex;				//		Pointer to cluster
	

	public int get_COMPONENT() {
		return _COMPONENT;
	}



	public void set_COMPONENT(int COMPONENT) {
		this._COMPONENT = COMPONENT;
	}


	public int get_IDX() {
		return _IDX;
	}



	public void set_IDX(int IDX) {
		this._IDX = IDX;
	}



	public int get_IDY() {
		return _IDY;
	}



	public void set_IDY(int IDY) {
		this._IDY = IDY;
	}



	public int get_ADC() {
		return _ADC;
	}



	public void set_ADC(int ADC) {
		this._ADC = ADC;
	}



	public int get_TDC() {
		return _TDC;
	}



	public void set_TDC(int TDC) {
		this._TDC = TDC;
	}


	public double get_Edep() {
		return _Edep;
	}


	public final void set_Edep(double edep) {
		this._Edep = edep;
	}



	public double get_Time() {
		return _Time;
	}


	public final void set_Time(double Time) {
		this._Time = Time;
	}
	
	
	public double get_Dx() {
		return _Dx;
	}


	public final void set_Dx(double Dx) {
		this._Dx = Dx;
	}


	public double get_Dy() {
		return _Dy;
	}


	public final void set_Dy(double Dy) {
		this._Dy = Dy;
	}


	public double get_Dz() {
		return _Dz;
	}


	public final void set_Dz(double Dz) {
		this._Dz = Dz;
	}


	public int get_DGTZIndex() {
		return _DGTZIndex;
	}


	public final void set_DGTZIndex(int _DGTZIndex) {
		this._DGTZIndex = _DGTZIndex;
	}
	
	
	public int get_ClusID() {
		return _ClusIndex;
	}


	public final void set_ClusID(int _ClusIndex) {
		this._ClusIndex = _ClusIndex;
	}
	
	public static boolean passHitSelection(MUCALHit hit, IndexedTable thresholds) {
		// a selection cut to pass the hit. 
		if(hit.get_Edep() > thresholds.getDoubleValue("thresholdHit", 1,1,REFCOMPONENT)) {
			return true;
		} else {
			return false;
		}		
	}

        @Override
	public int compareTo(MUCALHit arg0) {
		if(this.get_Edep()<arg0.get_Edep()) {
			return 1;
		} else {
			return -1;
		}
	}
        
        public void show() {
            System.out.println(+ this.get_COMPONENT() + "\t" 
                        + this.get_IDX()       + "\t " 
                        + this.get_IDY()       + "\t"
                        + this.get_Edep()      + "\t"
                        + this.get_Time()      + "\t"
                        + this.get_DGTZIndex() + "\t"
                        + this.get_ClusID());
        }
		
}
