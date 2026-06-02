/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.detector.geant4.v2;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.detector.volume.G4Trd;
import org.jlab.detector.volume.G4Box;
import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.units.SystemOfUnits.Length;

import static org.jlab.detector.units.SystemOfUnits.Length;
import org.jlab.detector.volume.G4World;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Trap3D;
import org.jlab.geometry.prim.Line3d;

/**
 *
 * @author kenjo
 */
public final class MUCALGeant4Factory extends Geant4Factory {

  private final double motherGap = 4.0 * Length.cm;
    private final double pbthickness = 0.005 * Length.in;
    private final double microgap = 0.001;
    private final double[][] align_deltaX = new double[6][3];
    private final double[][] align_deltaY = new double[6][3];
    private final double[][] align_deltaZ = new double[6][3];
    private final double[][] align_rotX = new double[6][3];
    private final double[][] align_rotY = new double[6][3];
    private final double[][] align_rotZ = new double[6][3];

    private final String[] gemcLayerNames = new String[]{
        "1a", "1b", "2"
    };
        private final String[] stringLayers = new String[]{
        "/geometry/ftof/panel1a",
        "/geometry/ftof/panel1b",
        "/geometry/ftof/panel2"};

    private final double CwidthU =   13.0 * Length.mm;    // Upstream   crystal width in mm (side of the squared front face)
    private final double CwidthD =   17.0 * Length.mm;    // Downstream crystal width in mm (side of the squared front face)
    private final double Clength =  200.0 * Length.mm;    // Crystal length in mm

    private final double CZpos      =  600.0 * Length.mm;    // Position of the front face of the crystals
    private final double CentryAngle = Math.toRadians(7);
    private final double CexitAngle  = Math.toRadians(30);

    private final double CrminU = CZpos*Math.tan(CentryAngle);
    private final double CrmaxU = CZpos*Math.tan(CexitAngle);
    private final double CrminD = CrminU + Clength*Math.tan(CentryAngle);
    private final double CrmaxD = CrmaxU + Clength*Math.tan(CexitAngle);


    private final double nplanes = 4;
    private final double[] ecal_iradius = new double[]{301* Length.mm, 72.8* Length.mm, 81.5* Length.mm, 98.7* Length.mm};
    private final double[] ecal_oradius = new double[]{301.1* Length.mm, 360.6* Length.mm, 401* Length.mm, 98.8* Length.mm};
    private final double[] ecal_zpos_root = new double[]{520* Length.mm, 625* Length.mm, 696* Length.mm, 836* Length.mm};
    
    //     private final double[] mucal_zpos    = { CZpos - microgap, CZpos + Clength + microgap };

	// // keeping radius flat as crystals are not tilted yet
	// private final double[] mucal_iradius = { CrminU - 40*microgap, CrminU - 40*microgap };

	// private final double[] mucal_oradius = { CrmaxU + 40*microgap, CrmaxD + 40*microgap };
    private final int[] nPaddles = new int[stringLayers.length];

    private final int npaddles = 48;
    private double globalOffset = 0;
    
    public MUCALGeant4Factory(ConstantProvider provider) {
        motherVolume = new G4World("ddvcs_ecal");


        for (int sector = 1; sector <= 2; sector++) {
                List<G4Trd> layerVolume = createPanel(provider, sector, 1);
                for (G4Trd paddle : layerVolume) {
                    paddle.setMother(motherVolume);
                }
                // layerVolume.setMother(motherVolume);
        }
        properties.put("email", "ewcline@mit.edu");
        properties.put("author", "cline");
        properties.put("date", "3/23/26");
    }

    private List<G4Trd> createPanel(ConstantProvider cp, int sector, int layer) {


        List<G4Trd> paddles = this.createLayer(cp, layer);

        // x is along the paddle, y perpendicular to the panel and z in the panel plane pointing outward
        // double panel_mother_dx1 = paddles.get(0).getXHalfLength();
        // double panel_mother_dx2 = paddles.get(paddles.size() - 1).getXHalfLength()
        //         + (paddles.get(paddles.size() - 1).getXHalfLength() - paddles.get(paddles.size() - 2).getXHalfLength());

        // double panel_mother_dy = paddles.get(0).getYHalfLength();
        // double panel_width = (paddles.get(paddles.size() - 1).getLocalPosition().z - paddles.get(0).getLocalPosition().z)
        //         + 2 * paddles.get(0).getZHalfLength();
        // double panel_mother_dz = panel_width / 2.0;

        // G4Trd panelVolume = new G4Trd("mucal_p" + gemcLayerNames[layer - 1] + "_s" + sector,
        //         panel_mother_dx1 + motherGap, panel_mother_dx2 +motherGap,
        //         panel_mother_dy + motherGap, panel_mother_dy + motherGap,
        //         panel_mother_dz + motherGap);
        // panelVolume.rotate("xyz", Math.toRadians(-90), 0.0, Math.toRadians(-30.0 - sector * 60.0));

        // double panel_pos_x =  (panel_width/2 + align_deltaX[sector-1][layer-1]) + (panel_mother_dy+align_deltaZ[sector-1][layer-1]);
        // double panel_pos_y = align_deltaY[sector-1][layer-1];
        // double panel_pos_z = (panel_width/2 + align_deltaX[sector-1][layer-1])  + (panel_mother_dy+align_deltaZ[sector-1][layer-1]);
        // Vector3d pos_vec = new Vector3d(panel_pos_x, panel_pos_y, panel_pos_z);
        // pos_vec.rotateZ(Math.toRadians((sector-1)*60));
        // panelVolume.translate(pos_vec);

        // for (int ipaddle = 0; ipaddle < paddles.size(); ipaddle++) {
        //     paddles.get(ipaddle).setName("panel" + gemcLayerNames[layer - 1] + "_sector" + sector + "_paddle_" + (ipaddle + 1));
        //     paddles.get(ipaddle).setMother(panelVolume);
        // }

        // if (layer != 2) {
        //     G4Trd pbShield = new G4Trd("mucal_shield_p" + gemcLayerNames[layer-1] + "_sector" + sector,
        //             panel_mother_dx1, panel_mother_dx2, pbthickness / 2.0, pbthickness / 2.0, panel_mother_dz);
        //     pbShield.translate(0.0, - panel_mother_dy - microgap - pbthickness / 2.0, 0.0);
        //     pbShield.setMother(panelVolume);
        // }

        return paddles;
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
        double Cwidth = (CwidthU+CwidthD)/2 + microgap;
        int nCrystal = 2*((int)(CrmaxU / Cwidth));
        double paddlewidth = 10*Length.cm;
        double paddlethickness = 1.0*Length.cm;
        double gap = 0.5*Length.cm;
        double pairgap = 1.0*Length.cm;

        double centerX, centerY, x12, x22, y12, y22, rad1, rad2, rad3, rad4, rxy;
        double centerZ, thetaX, thetaY, radius, posX, posY, posZ, dx1, dx2, dz;
        String vname;
        List<G4Trd> paddleVolumes = new ArrayList<>();

        for(int iX = 0; iX < nCrystal; iX++)
	    {
		    for(int iY = 0; iY < nCrystal; iY++)
		    {
                centerX = - nCrystal/2.*Cwidth + iX*Cwidth + 0.5*Cwidth;
                centerY = - nCrystal/2.*Cwidth + iY*Cwidth + 0.5*Cwidth;

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
                paddleVolumes.add(crvolume);
            }

            }
        }


        // for (int ipaddle = 0; ipaddle < nCrystal; ipaddle++) {
        //     // double paddlelength = cp.getDouble(paddleLengthStr, ipaddle);//1
        //     double paddlelength = 10*Length.cm;
        //     String vname = String.format("sci_S%d_L%d_C%d", 0, layer, ipaddle + 1);
        //     G4Box volume = new G4Box(vname, paddlelength / 2. * Length.cm, paddlethickness / 2. * Length.cm, paddlewidth / 2.0 * Length.cm);
        //     volume.makeSensitive();

        //     int ipair = (int) ipaddle/2;
        //     double zoffset = (ipaddle - nCrystal / 2. + 0.5) * (paddlewidth + gap);
        //     if(layer==2) zoffset = (ipair - nCrystal/4-0.5) * (2*paddlewidth + gap + pairgap) + ((ipaddle%2)+0.5) * (paddlewidth + gap);

        //     volume.translate(0.0, 0.0, zoffset * Length.cm);

        //     paddleVolumes.add(volume);
        // }
        return paddleVolumes;
    }

    public G4Box getComponent(int sector, int layer, int paddle) {
        int ivolume = (sector - 1) * 3 + layer - 1;

        if (sector >= 1 && sector <= 6
                && layer >= 1 && layer <= 3) {

            List<Geant4Basic> panel = motherVolume.getChildren().get(ivolume).getChildren();
            int npaddles = panel.size();

            if (paddle >= 1 && paddle <= npaddles) {
                return (G4Box) panel.get(paddle - 1);
            }
        }

        System.err.println("ERROR!!!");
        System.err.println("Component: sector: " + sector + ", layer: " + layer + ", paddle: " + paddle + " doesn't exist");
        throw new IndexOutOfBoundsException();
    }

    public double getThickness(int sector, int layer, int paddle) {
        int ivolume = (sector - 1) * 3 + layer - 1;

        Geant4Basic panel = motherVolume.getChildren().get(ivolume);
        G4Box pad = (G4Box) panel.getChildren().get(paddle-1);
        return pad.getYHalfLength()*2.;      
    }
    
    public Plane3D getFrontalFace(int sector, int layer) {
        if (sector < 1 || sector > 6
                || layer < 1 || layer > 3) {
            System.err.println("ERROR!!!");
            System.err.println("Component: sector: " + sector + ", layer: " + layer + " doesn't exist");
            throw new IndexOutOfBoundsException();
        }

        int ivolume = (sector - 1) * 3 + layer - 1;

        Geant4Basic panel = motherVolume.getChildren().get(ivolume);
        G4Box padl = (G4Box) panel.getChildren().get(1);
        Vector3d point = new Vector3d(padl.getVertex(0)); //first corner of the paddle on the upstream face
        Vector3d normal = new Vector3d(panel.getLineY().diff().normalized());

        return new Plane3D(point.x, point.y, point.z, normal.x, normal.y, normal.z);
    }

    public Plane3D getMidPlane(int sector, int layer) {
        if (sector < 1 || sector > 6
                || layer < 1 || layer > 3) {
            System.err.println("ERROR!!!");
            System.err.println("Component: sector: " + sector + ", layer: " + layer + " doesn't exist");
            throw new IndexOutOfBoundsException();
        }

        int ivolume = (sector - 1) * 3 + layer - 1;

        Geant4Basic panel = motherVolume.getChildren().get(ivolume);
        G4Box padl = (G4Box) panel.getChildren().get(1);
        double x=(padl.getLineY().origin().x+padl.getLineY().end().x)/2;
        double y=(padl.getLineY().origin().y+padl.getLineY().end().y)/2;
        double z=(padl.getLineY().origin().z+padl.getLineY().end().z)/2;
        Vector3d normal = new Vector3d(panel.getLineY().diff().normalized());

        return new Plane3D(x, y, z, normal.x, normal.y, normal.z);
    }

    public Trap3D getTrajectorySurface(int sector, int layer) {
        if (sector < 1 || sector > 6
                || layer < 1 || layer > 3) {
            System.err.println("ERROR!!!");
            System.err.println("Component: sector: " + sector + ", layer: " + layer + " doesn't exist");
            throw new IndexOutOfBoundsException();
        }

        int ivolume = (sector - 1) * 3 + layer - 1;

        List<Geant4Basic> paddles = motherVolume.getChildren().get(ivolume).getChildren();
        G4Box padl0 = (G4Box) paddles.get(0);
        G4Box padln = (G4Box) paddles.get(nPaddles[layer-1]-1);

        Vector3d dy = padl0.getLineZ().diff().dividedBy(2);

        Vector3d p0 = padl0.getLineX().end().clone().sub(dy);
        Vector3d p1 = padl0.getLineX().origin().clone().sub(dy);
        Vector3d p2 = padln.getLineX().origin().clone().add(dy);
        Vector3d p3 = padln.getLineX().end().clone().add(dy);
        
        Trap3D trapezoid = new Trap3D(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z);
        
        return trapezoid; 
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
