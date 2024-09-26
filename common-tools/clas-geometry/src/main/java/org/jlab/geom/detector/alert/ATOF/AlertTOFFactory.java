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
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Transformation3D;

import java.util.ArrayList;
import java.util.List;

/**
 * @author viktoriya, whit
 *
 * Swapped the meaning of "paddle" and "layer" The "paddle" is 10 wedges to a row. The row is the "layer" (of which there are 4).
 *
 * ATOF geometry:
 * _________________
 * \               /
 *  \   wedge     /     2cm thick
 *   \           /
 *    \_________/
 *     \__row__/        3mm thick
 *
 *         ^
 *         |
 *         |
 *      paticle
 *
 */
public class AlertTOFFactory implements Factory<AlertTOFDetector, AlertTOFSector, AlertTOFSuperlayer, AlertTOFLayer> {

	private final int nsectors = 15; // 15 modules 
	private final int nsuperl  = 2;  // 2 layers
	private final int npaddles = 10;  // 10 wedges per paddle
	private final int nlayers1 = 4; // 4 rows  per layer

	private final double openAng_pad_deg    = 6.0; 
	private final double openAng_pad_rad    = Math.toRadians(openAng_pad_deg);
	private final double openAng_sector_rad = 4 * openAng_pad_rad;

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
                // Lets not confuse things there are 2 "super layers"
		sector.addSuperlayer(createSuperlayer(cp, sectorId, 0)); // is it ok to start at zero here? 
		sector.addSuperlayer(createSuperlayer(cp, sectorId, 1));
		//for (int superlayerId = 0; superlayerId < nsuperl; superlayerId++)
		//    sector.addSuperlayer(createSuperlayer(cp, sectorId, superlayerId));
		return sector;
	}

	@Override
	public AlertTOFSuperlayer createSuperlayer(ConstantProvider cp, int sectorId, int superlayerId) {
		if (!(0 <= sectorId && sectorId < nsectors)) throw new IllegalArgumentException("Error: invalid sector=" + sectorId);
		if (!(0 <= superlayerId && superlayerId < nsuperl)) throw new IllegalArgumentException("Error: invalid superlayer=" + superlayerId);
		AlertTOFSuperlayer superlayer = new AlertTOFSuperlayer(sectorId, superlayerId);

                if (superlayerId == 0) {  // should this start at 1?
                  for (int layerId = 0; layerId < nlayers1; layerId++)
                    superlayer.addLayer(createLayer(cp, sectorId, superlayerId, layerId));
                } else {
                  for (int layerId = 0; layerId < nlayers1; layerId++)
                    superlayer.addLayer(createLayer(cp, sectorId, superlayerId, layerId));
                }
                return superlayer;
        }

	@Override
	public AlertTOFLayer createLayer(ConstantProvider cp, int sectorId, int superlayerId, int layerId) {
		if (!(0 <= sectorId && sectorId < nsectors)) throw new IllegalArgumentException("Error: invalid sector=" + sectorId);
		if (!(0 <= superlayerId && superlayerId < nsuperl)) throw new IllegalArgumentException("Error: invalid superlayer=" + superlayerId);
		if (!(0 <= layerId && layerId < nlayers1)) throw new IllegalArgumentException("Error: invalid layer=" + layerId);

		double R0  = 77.0;
		double R1  = 80.0;
		double dR0 = 3.0;    // row scintillator thickness
		double dR1 = 20.0;   // wedge scintillator thickness

                //double pad_b1 = 2*((R1)*tan(openAng_pad_deg/2) - 25.4*0.004*); 
                //double pad_b2 = 2*((R1+dR1)*tan(openAng_pad_deg/2) - 25.4*0.004*); 
                // where the wrap thickness is 4 mil.

                // trapezoide dimensions for a bigger paddle (external)
                double pad_b1 = 8.17369; // mm inner width of wedge 
		double pad_b2 = 10.27; // mm  outer width of wedge

                // 4 mil wrapping thickness = 25.4*0.004*2 = 0.2032 mm
		double gap_pad_z = 0.2032; // mm, gap between paddles in z
		double pad_z = 280.0; // mm  wedge length
		if (superlayerId == 1) pad_z = 28.0 - gap_pad_z; // mm inner bar length

                //double small_pad_b1 = 2*((R0)*tan(openAng_pad_deg/2) - 25.4*0.004*); 
                //double small_pad_b2 = 2*((R0+dR0)*tan(openAng_pad_deg/2) - 25.4*0.004*); 
                // where the wrap thickness is 4 mil.
                
		// trapezoide dimensions for a smaller paddle (internal)
		double small_pad_b1 = 7.85924; // mm
		double small_pad_b2 = 8.17369; // mm


                // "layer" is a paddle
		AlertTOFLayer layer = new AlertTOFLayer(sectorId, superlayerId, layerId);

		List<Plane3D> planes = new ArrayList<>();

		double Rl      = R0;
		double dR      = dR0;
		double widthTl = small_pad_b2;
		double widthBl = small_pad_b1;
                int scint_per_row = 1; 

		if (superlayerId == 1) {
                  // Outer wedges
                  Rl            = R1;
                  dR            = dR1;
                  widthTl       = pad_b2;
                  widthBl       = pad_b1;
                  scint_per_row = npaddles;  //10 wedges
                }

                int comp_index = 0;

                // 10 pads per layer in the outer wedges
                for (int padId = 0; padId < scint_per_row; padId++) {

                  // Not sure what this is relative to but it seems to start at z=0. -whit
                  double len_b   = padId * pad_z + padId * gap_pad_z; // back paddle plan
                  double len_f   = len_b + pad_z; // front paddle plan
                                                  
                  if (superlayerId == 1) {
                    // first 4 index values are for the 4 inner bars
                    comp_index = 3 + layerId*10 +padId;
                  } else {
                    comp_index  = layerId+padId; // padId should be 0 here so it is just layerId.
                  }

			Point3D p0 = new Point3D(-dR / 2, -widthBl / 2, len_f);
			Point3D p1 = new Point3D(dR / 2, -widthTl / 2, len_f);
			Point3D p2 = new Point3D(dR / 2, widthTl / 2, len_f);
			Point3D p3 = new Point3D(-dR / 2, widthBl / 2, len_f);

			Point3D            p4     = new Point3D(-dR / 2, -widthBl / 2, len_b);
			Point3D            p5     = new Point3D(dR / 2, -widthTl / 2, len_b);
			Point3D            p6     = new Point3D(dR / 2, widthTl / 2, len_b);
			Point3D            p7     = new Point3D(-dR / 2, widthBl / 2, len_b);

                        // What is the role of the component ID here: -whit
                        // 
                        // Each scintillator paddle has a unique ID. 
			ScintillatorPaddle Paddle = new ScintillatorPaddle(sectorId *44 + comp_index, p0, p1, p2, p3, p4, p5, p6, p7);

			double openAng_sector_deg = npaddles * openAng_pad_deg;
			Paddle.rotateZ(Math.toRadians(layerId * openAng_pad_deg + sectorId * openAng_sector_deg));

			double xoffset;
			double yoffset;

			xoffset = (Rl + dR / 2) * Math.cos(layerId * openAng_pad_rad + sectorId * openAng_sector_rad);
			yoffset = (Rl + dR / 2) * Math.sin(layerId * openAng_pad_rad + sectorId * openAng_sector_rad);

			Paddle.translateXYZ(xoffset, yoffset, 0);

			// Add the paddles to the list
			layer.addComponent(Paddle);
		}


		if (superlayerId == 0) {
                // Not sure what this is for? -whit
                // adding plane for each of the 60 inner bars. Maybe the outer wedge rows need a plane too?...
		Plane3D plane = new Plane3D(0, Rl, 0, 0, 1, 0);
		plane.rotateZ((sectorId) * openAng_sector_rad + layerId*openAng_pad_rad    - Math.toRadians(90));
		planes.add(plane);
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
