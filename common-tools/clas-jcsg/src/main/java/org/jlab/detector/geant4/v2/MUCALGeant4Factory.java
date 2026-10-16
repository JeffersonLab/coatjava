package org.jlab.detector.geant4.v2;

import java.util.List;
import java.util.ArrayList;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.detector.units.SystemOfUnits.Length;
import org.jlab.detector.volume.G4Pcone;
import org.jlab.detector.volume.G4Trd;
import org.jlab.detector.volume.G4World;
import org.jlab.geom.base.ConstantProvider;
import static org.jlab.detector.units.SystemOfUnits.Length;

/**
 *
 * @author Ethan Cline
 */
public final class MUCALGeant4Factory extends Geant4Factory {
    
    public MUCALGeant4Factory(ConstantProvider provider) {
        motherVolume = new G4World("root");
        int nplanes = 4;
        double phiStart = Math.toRadians(0.0);
        double phiTotal = Math.toRadians(360.0);
        double[] mucal_iradius = {301.0*Length.mm, 72.8*Length.mm, 81.5*Length.mm, 98.7*Length.mm};
        double[] mucal_oradius = {301.1*Length.mm, 360.6*Length.mm, 401.0*Length.mm, 98.8*Length.mm};
        double[] mucal_zpos_root = {520.0*Length.mm, 625.0*Length.mm, 696.0*Length.mm, 836.0*Length.mm};
        G4Pcone mucalVolume = new G4Pcone("mucalVolume", phiStart, phiTotal, nplanes, mucal_zpos_root, mucal_iradius, mucal_oradius);
        mucalVolume.setMother(motherVolume);
        for (int sector = 1; sector <= 2; sector++) {
            List<G4Trd> layerVolume = createPanel(provider, sector, 1);
            for (G4Trd crystal : layerVolume) {
                crystal.setMother(mucalVolume);
            }
            // layerVolume.setMother(motherVolume);
        }
        properties.put("email", "ewcline@mit.edu");
        properties.put("author", "cline");
        properties.put("date", "3/23/26");
    }
    
    private List<G4Trd> createPanel(ConstantProvider cp, int sector, int layer) {
        return this.createLayer(cp, layer);
    }

    private List<G4Trd> createLayer(ConstantProvider cp, int layer) {
        
        double Dat25deg = 596.*Length.mm; // distance of the upstream face from the target at 25 deg
        double ThetaU   = Math.toRadians(25); // deg
        double ThetaMin = Math.toRadians(7); // deg
        double ThetaMax = Math.toRadians(30); // deg
        double CwidthU = 13*Length.mm;
        double CwidthD = 17*Length.mm;
        double Clength = 190*Length.mm;
        double CrminU = (Dat25deg+Clength/2.) * ( Math.tan(ThetaU) - Math.tan(ThetaU - ThetaMin) )*Math.cos(ThetaU);
        double CrmaxU = (Dat25deg+Clength/2.) * ( Math.tan(ThetaU) + Math.tan(ThetaMax - ThetaU) )*Math.cos(ThetaU);
        double microgap = 0.5*Length.mm;
        double Cwidth = (CwidthU+CwidthD)/2. + microgap;
        int nCrystal = 2*((int)(CrmaxU / Cwidth)+100);// add 100 to get full coverage
        
        double centerX, centerY, x12, x22, y12, y22, rad1, rad2, rad3, rad4, rxy;
        double centerZ, thetaX, thetaY, radius, posX, posY, posZ, dx1, dx2, dz;
        String vname;
        List<G4Trd> crystalVolumes = new ArrayList<>();
        
        for(int iX = 0; iX < nCrystal; iX++)
        {
            for(int iY = 0; iY < nCrystal; iY++)
            {
                centerX = - nCrystal/2.*Cwidth + (double)iX*Cwidth + 0.5*Cwidth;
                centerY = - nCrystal/2.*Cwidth + (double)iY*Cwidth + 0.5*Cwidth;
                
                x12 = (centerX - 0.5*Cwidth)*(centerX - 0.5*Cwidth);
                x22 = (centerX + 0.5*Cwidth)*(centerX + 0.5*Cwidth);
                y12 = (centerY - 0.5*Cwidth)*(centerY - 0.5*Cwidth);
                y22 = (centerY + 0.5*Cwidth)*(centerY + 0.5*Cwidth);
                
                rad1 = Math.sqrt(x12 + y12);
                rad2 = Math.sqrt(x22 + y22);
                rad3 = Math.sqrt(x12 + y22);
                rad4 = Math.sqrt(x22 + y12);
                if(rad1 > CrminU + microgap && rad1 < CrmaxU - microgap &&
                    rad2 > CrminU + microgap && rad2 < CrmaxU - microgap &&
                    rad3 > CrminU + microgap && rad3 < CrmaxU - microgap &&
                    rad4 > CrminU + microgap && rad4 < CrmaxU - microgap
                    )
                {
                    rxy     = Math.sqrt(centerX*centerX + centerY*centerY);
                    centerZ = (Dat25deg+Clength/2.)/Math.cos(ThetaU) - rxy*Math.sin(ThetaU);
                    thetaX  = -Math.atan(centerX/centerZ);
                    thetaY  = Math.atan(centerY/centerZ);
                    radius = Math.sqrt(rxy*rxy + centerZ*centerZ);
                    posX = centerX;
                    posY = centerY;
                    posZ = centerZ;
                    vname = String.format("sci_S%d_L%d_C%d", iX + 1, iY + 1, nCrystal);
                    dx1 = CwidthU/2.0;
                    dx2 = CwidthD/2.0;
                    dz = Clength/2.0;
                    G4Trd crvolume = new G4Trd("mucal_" + iX + "_" + iY, dx1, dx2, dx1, dx2, dz);
                    crvolume.makeSensitive();
                    crvolume.rotate("zyx",0, thetaX, thetaY);
                    crvolume.translate(posX, posY, posZ);
                    crystalVolumes.add(crvolume);
                }
                
            }
        }
        
        return crystalVolumes;
    }
    
    public G4Trd getComponent(int sector, int layer, int crystal) {
        int ivolume = (sector - 1) * 3 + layer - 1;
        
        if (sector >= 1 && sector <= 6
            && layer >= 1 && layer <= 3) {
            
            List<Geant4Basic> panel = motherVolume.getChildren().get(ivolume).getChildren();
            int ncrystals = panel.size();
            
            if (crystal >= 1 && crystal <= ncrystals) {
                return (G4Trd) panel.get(crystal - 1);
            }
        }
        
        System.err.println("ERROR!!!");
        System.err.println("Component: sector: " + sector + ", layer: " + layer + ", crystal: " + crystal + " doesn't exist");
        throw new IndexOutOfBoundsException();
    }
    
    public G4World getMother() {
        return motherVolume;
    }
    
    public static void main(String[] args) {
        ConstantProvider cp = null;//GeometryFactory.getConstants(DetectorType.MUCAL);
        MUCALGeant4Factory factory = new MUCALGeant4Factory(cp);
        
        factory.getAllVolumes().forEach(volume -> {
            System.out.println(volume.gemcString());
        });
    }
}
