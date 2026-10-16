/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.geom.detector.alert.AHDC;

import org.jlab.geom.DetectorId;
import org.jlab.geom.abs.AbstractDetector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

/**
 * @author sergeyeva
 */
public class AlertDCDetector extends AbstractDetector<AlertDCSector> {
	protected AlertDCDetector() {
		super(DetectorId.AHDC);
	}

	/**
	 * Returns "ALERT DC Detector".
	 *
	 * @return "ALERT DC Detector"
	 */
	@Override
	public String getType() {
		return "ALERT DC Detector";
	}

	public void print() {
		System.out.println("/////////////////////////"); 
		System.out.println("AHDC geometry"); 
		System.out.println(""); 
		System.out.println("s  : sector"); 
		System.out.println("sl : super layer"); 
		System.out.println("l  : layer"); 
		System.out.println("c  : component"); 
		System.out.println("/////////////////////////"); 
		System.out.println("------------------------------------------------------------------------------"); 
		System.out.println("                |            origin            |             end"); 
		System.out.println("------------------------------------------------------------------------------"); 
		System.out.println("s   sl  l   c   |     x         y        z     |     x        y        z"); 
		System.out.println("------------------------------------------------------------------------------");
		for (int s = 1; s <= getNumSectors(); s++) {
			for (int sl = 1; sl <= getSector(s).getNumSuperlayers(); sl++) {
				for (int l = 1; l <= getSector(s).getSuperlayer(sl).getNumLayers(); l++) {
					for (int c = 1; c <= getSector(s).getSuperlayer(sl).getLayer(l).getNumComponents(); c++) {
						Line3D line = getSector(s).getSuperlayer(sl).getLayer(l).getComponent(c).getLine();
						Point3D end = line.end();
						Point3D origin = line.origin();
						System.out.printf("%2d  %2d  %2d  %2d  |  %7.3f  %7.3f  %7.3f  |  %7.3f  %7.3f  %7.3f\n", s, sl, l, c, origin.x(), origin.y(), origin.z(), end.x(), end.y(), end.z());
					}
				}
			}
		}
	}

}
