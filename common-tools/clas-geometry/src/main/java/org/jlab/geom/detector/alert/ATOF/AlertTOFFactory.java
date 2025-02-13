/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//package clas12vis;

package org.jlab.geom.detector.alert.ATOF;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.geom.base.DetectorTransformation;
import org.jlab.geom.base.Factory;
import org.jlab.geom.component.ScintillatorPaddle;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Transformation3D;


/**
 * @author viktoriya, pilleux
 * Original geometry July 02, 2020
 * Updated December 2024 to match actual atof conventions
 * ATOF geometry class to be used in reco. and in GEMC simulations! 
 */
public class AlertTOFFactory implements Factory<AlertTOFDetector, AlertTOFSector, AlertTOFSuperlayer, AlertTOFLayer> {

        //Convention definitions: https://clasweb.jlab.org/wiki/index.php/File:Atof_def.png
        //The atof has 15 phi sectors.
	private final int nsectors    = 15;
        //Top superlayer (index 1) = wedges.
        //Bottom one (0) = bar.
	private final int nsuperl     = 2;
        //Layers = quarters of sectors.
	private final int nlayers     = 4;
        //Components = slices in z. 10 for the wedges, 1 for the bar.
	private final int ncomponents = 10;

        //Each pad = quarter of module
	private final double openAng_pad_deg    = 6.0;
        //4 pads = 4 layers = 1 sector
	private final double openAng_sector_deg = nlayers * openAng_pad_deg;

	@Override
	public AlertTOFDetector createDetectorCLAS(ConstantProvider cp) {
		return createDetectorSector(cp);
	}

	@Override
	public AlertTOFDetector createDetectorSector(ConstantProvider cp) {
		return createDetectorTilted(cp);
	}

	@Override
	public AlertTOFDetector createDetectorTilted(ConstantProvider cp) {
		return createDetectorLocal(cp);
	}

	@Override
	public AlertTOFDetector createDetectorLocal(ConstantProvider cp) {
		AlertTOFDetector detector = new AlertTOFDetector();
		for (int sectorId = 0; sectorId < nsectors; sectorId++)
		     detector.addSector(createSector(cp, sectorId));
		return detector;
	}

	@Override
	public AlertTOFSector createSector(ConstantProvider cp, int sectorId) {
		if (!(0 <= sectorId && sectorId < nsectors)) throw new IllegalArgumentException("Error: invalid sector=" + sectorId);
		AlertTOFSector sector = new AlertTOFSector(sectorId);
		for (int superlayerId = 0; superlayerId < nsuperl; superlayerId++)
		     sector.addSuperlayer(createSuperlayer(cp, sectorId, superlayerId));
		return sector;
	}

	@Override
	public AlertTOFSuperlayer createSuperlayer(ConstantProvider cp, int sectorId, int superlayerId) {
		if (!(0 <= sectorId && sectorId < nsectors)) throw new IllegalArgumentException("Error: invalid sector=" + sectorId);
		if (!(0 <= superlayerId && superlayerId < nsuperl)) throw new IllegalArgumentException("Error: invalid superlayer=" + superlayerId);
		AlertTOFSuperlayer superlayer = new AlertTOFSuperlayer(sectorId, superlayerId);
                for (int layerId = 0; layerId < nlayers; layerId++) superlayer.addLayer(createLayer(cp, sectorId, superlayerId, layerId));
		return superlayer;
	}
        
	@Override
	public AlertTOFLayer createLayer(ConstantProvider cp, int sectorId, int superlayerId, int layerId) {
		if (!(0 <= sectorId && sectorId < nsectors)) throw new IllegalArgumentException("Error: invalid sector=" + sectorId);
		if (!(0 <= superlayerId && superlayerId < nsuperl)) throw new IllegalArgumentException("Error: invalid superlayer=" + superlayerId);
		if (!(0 <= layerId && layerId < nlayers)) throw new IllegalArgumentException("Error: invalid layer=" + layerId);

		double R0  = 77.0d;
		double R1  = 80.0d;
		double dR0 = 3.0d;
		double dR1 = 20.0d;

		// trapezoide dimensions for a bigger paddle (external)
		double pad_b1 = 8.17369; // mm
		double pad_b2 = 10.27; // mm

		double pad_z = 279.7; // mm
		if (superlayerId == 1) pad_z = 27.7; // mm

		// trapezoide dimensions for a smaller paddle (internal)
		double small_pad_b1 = 7.85924; // mm
		double small_pad_b2 = 8.17369; // mm

		double gap_pad_z = 0.3d; // mm, gap between paddles in z

		AlertTOFLayer layer = new AlertTOFLayer(sectorId, superlayerId, layerId);
		
                //Dimensions for the bar 
		double Rl      = R0;
		double dR      = dR0;
		double widthTl = small_pad_b2;
		double widthBl = small_pad_b1;
                //Dimensions for the wedge
		if (superlayerId == 1) {
			Rl      = R1;
			dR      = dR1;
			widthTl = pad_b2;
			widthBl = pad_b1;
		}
                
                //Layer = quarter of a sector
                double current_angle_deg = layerId * openAng_pad_deg + sectorId * openAng_sector_deg;
                //Aligning the y axis with the separation between modules 0 and 14
                current_angle_deg = current_angle_deg + 90 + 3; 

                //Component = z slice. 
                //There are 10 for the wedge/top/sl=1
                int current_ncomponents = ncomponents;
                //There is only one for the bar/bottom/sl=0
                if(superlayerId==0) current_ncomponents =  1;
                
                //Starting loop on components 
		for (int padId = 0; padId < current_ncomponents; padId++) {
                    
                    //Component index increases with increasing z
                    double len_b   = padId * pad_z + padId * gap_pad_z; // back paddle plan
                    double len_f   = len_b + pad_z; // front paddle plan

                    Point3D p0 = new Point3D(-dR / 2, -widthBl / 2, len_f);
                    Point3D p1 = new Point3D(dR / 2, -widthTl / 2, len_f);
                    Point3D p2 = new Point3D(dR / 2, widthTl / 2, len_f);
                    Point3D p3 = new Point3D(-dR / 2, widthBl / 2, len_f);

                    Point3D            p4     = new Point3D(-dR / 2, -widthBl / 2, len_b);
                    Point3D            p5     = new Point3D(dR / 2, -widthTl / 2, len_b);
                    Point3D            p6     = new Point3D(dR / 2, widthTl / 2, len_b);
                    Point3D            p7     = new Point3D(-dR / 2, widthBl / 2, len_b);
		
                    //Component index is the z slice for the top/wedge/sl=1
                    int component = padId;
                    //It is 10 for the bottom/bar/sl=0
                    if(superlayerId==0) component =  10;   
                    
                    ScintillatorPaddle Paddle = new ScintillatorPaddle(component, p0, p1, p2, p3, p4, p5, p6, p7);
                    
                    Paddle.rotateZ(Math.toRadians(current_angle_deg));

                    double xoffset;
                    double yoffset;

                    xoffset = (Rl + dR / 2) * Math.cos(Math.toRadians(current_angle_deg));
                    yoffset = (Rl + dR / 2) * Math.sin(Math.toRadians(current_angle_deg));

                    Paddle.translateXYZ(xoffset, yoffset, 0);

                    // Add the paddles to the list
                    layer.addComponent(Paddle);
		}
		return layer;
	}

	/**
	 * Returns "Alert TOF Factory".
	 *
	 * @return "Alert TOF Factory"
	 */
	@Override
	public String getType() {
		return "Alert TOF Factory";
	}

	@Override
	public void show() {
		System.out.println(this);
	}

	@Override
	public String toString() {
		return getType();
	}

	@Override
	public Transformation3D getTransformation(ConstantProvider cp, int sector, int superlayer, int layer) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public DetectorTransformation getDetectorTransform(ConstantProvider cp) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
