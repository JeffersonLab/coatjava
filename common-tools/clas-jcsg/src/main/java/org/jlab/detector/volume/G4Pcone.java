package org.jlab.detector.volume;

import org.jlab.geometry.prim.Pcone;
import org.jlab.detector.units.Measurement;
import org.jlab.detector.units.SystemOfUnits.Angle;
import org.jlab.detector.units.SystemOfUnits.Length;

/**
 * @author pdavies/devita
 */
// FIXME: currently support only polyheadra defintion to gemc geometry
public class G4Pcone extends Geant4Basic {

	public G4Pcone(String name, double phiStart, double phiTotal, int numZPlanes,
                        double[] zPlane, double[] rInner, double[] rOuter ) {
            
              super( new Pcone(phiStart, phiTotal, numZPlanes, zPlane, rInner, rOuter));
              setName( name );
              setType("Polycone");
              
              Measurement[] dimensions = new Measurement[3+3*numZPlanes];
              dimensions[0] = Angle.value(phiStart);
              dimensions[1] = Angle.value(phiTotal);
              dimensions[2] = new Measurement(numZPlanes,"counts");
              for(int i=0; i<numZPlanes; i++) dimensions[3+0*numZPlanes+i] = Length.value(rInner[i]);
              for(int i=0; i<numZPlanes; i++) dimensions[3+1*numZPlanes+i] = Length.value(rOuter[i]);
              for(int i=0; i<numZPlanes; i++) dimensions[3+2*numZPlanes+i] = Length.value(zPlane[i]);
              setDimensions(dimensions);
        	}

}
