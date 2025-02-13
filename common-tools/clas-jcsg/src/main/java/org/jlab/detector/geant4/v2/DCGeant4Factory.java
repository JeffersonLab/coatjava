package org.jlab.detector.geant4.v2;

import eu.mihosoft.vrl.v3d.Vector3d;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.units.SystemOfUnits.Length;
import org.jlab.detector.volume.G4Trap;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Trap3D;
import org.jlab.logging.DefaultLogger;

/**
 *
 * @author kenjo
 */
final class DCdatabase {

    private final int nSectors = 6;
    private final int nRegions = 3;
    private final int nSupers = 6;
    private final int nShifts = 6;

    private final double dist2tgt[] = new double[nRegions];
    private final double xdist[] = new double[nRegions];
    private final double frontgap[] = new double[nRegions];
    private final double midgap[] = new double[nRegions];
    private final double backgap[] = new double[nRegions];
    private final double thopen[] = new double[nRegions];
    private final double thtilt[] = new double[nRegions];

    private final double thmin[] = new double[nSupers];
    private final double thster[] = new double[nSupers];
    private final double wpdist[] = new double[nSupers];
    private final double cellthickness[] = new double[nSupers];
    private final int nsenselayers[] = new int[nSupers];
    private final int nguardlayers[] = new int[nSupers];
    private final int nfieldlayers[] = new int[nSupers];
    private final double superwidth[] = new double[nSupers];

    private final double align_dx[][] = new double[nSectors][nRegions];
    private final double align_dy[][] = new double[nSectors][nRegions];
    private final double align_dz[][] = new double[nSectors][nRegions];

    private final double align_dthetax[][] = new double[nSectors][nRegions];
    private final double align_dthetay[][] = new double[nSectors][nRegions];
    private final double align_dthetaz[][] = new double[nSectors][nRegions];
    
    private final double endplatesbow[][][] = new double[nSectors][nRegions][2];

    private int nsensewires;
    private int nguardwires;

    private DCGeant4Factory.MinistaggerStatus ministaggerStatus = DCGeant4Factory.MinistaggerStatus.ON;
    private double ministagger ;

    private boolean endplatesStatus = false;
    
    private final String dcdbpath = "/geometry/dc/";
    private static DCdatabase instance = null;

    private final double[][] feedThroughExt = new double[nSectors][nSupers];    // extension from the endplate, inside the chamber volume
    private final double[][] feedThroughLength = new double[nSectors][nSupers]; // length of the curved, trumpet-like, part of the feedthrough
    private final double[][] feedThroughRmin = new double[nSectors][nSupers];   // inner radius at the beginning of the curved part
    private final double[][] feedThroughRmax = new double[nSectors][nSupers];   // inner radius at the end of the curved part, i.e. on the surface
    private final double[][] feedThroughRcurv = new double[nSectors][nSupers];  // curvature radius of the trumpet-like part
    private DCGeant4Factory.FeedthroughsStatus feedthroughsStatus = DCGeant4Factory.FeedthroughsStatus.SHIFT;
    
        
    private DCdatabase() {}

    public static DCdatabase getInstance() {
        if (instance == null) {
            instance = new DCdatabase();
        }
        return instance;
    }

    public void connect(ConstantProvider cp, double[][] shifts) {

        if(shifts==null || shifts.length!=nRegions || shifts[0].length!=nShifts) {
            shifts = new double[nRegions][nShifts];
        }
        
        nguardwires = cp.getInteger(dcdbpath + "layer/nguardwires", 0);
        nsensewires = cp.getInteger(dcdbpath + "layer/nsensewires", 0);
        ministagger = cp.getDouble(dcdbpath + "ministagger/ministagger", 0);

        for (int ireg = 0; ireg < nRegions; ireg++) {
            dist2tgt[ireg] = cp.getDouble(dcdbpath + "region/dist2tgt", ireg)*Length.cm;
            xdist[ireg] = cp.getDouble(dcdbpath + "region/xdist", ireg)*Length.cm;
            frontgap[ireg] = cp.getDouble(dcdbpath + "region/frontgap", ireg)*Length.cm;
            midgap[ireg] = cp.getDouble(dcdbpath + "region/midgap", ireg)*Length.cm;
            backgap[ireg] = cp.getDouble(dcdbpath + "region/backgap", ireg)*Length.cm;
            thopen[ireg] = Math.toRadians(cp.getDouble(dcdbpath + "region/thopen", ireg));
            thtilt[ireg] = Math.toRadians(cp.getDouble(dcdbpath + "region/thtilt", ireg));
        }
        for (int isuper = 0; isuper < nSupers; isuper++) {
            thmin[isuper] = Math.toRadians(cp.getDouble(dcdbpath + "superlayer/thmin", isuper));
            thster[isuper] = Math.toRadians(cp.getDouble(dcdbpath + "superlayer/thster", isuper));
            wpdist[isuper] = cp.getDouble(dcdbpath + "superlayer/wpdist", isuper)*Length.cm;
            cellthickness[isuper] = cp.getDouble(dcdbpath + "superlayer/cellthickness", isuper);
            nsenselayers[isuper] = cp.getInteger(dcdbpath + "superlayer/nsenselayers", isuper);
            nguardlayers[isuper] = cp.getInteger(dcdbpath + "superlayer/nguardlayers", isuper);
            nfieldlayers[isuper] = cp.getInteger(dcdbpath + "superlayer/nfieldlayers", isuper);

            superwidth[isuper] = wpdist[isuper] * (nsenselayers[isuper] + nguardlayers[isuper] - 1) * cellthickness[isuper];
        }
        int feedthroughrows = cp.length(dcdbpath+"feedthroughs/sector");
        for(int irow = 0; irow< feedthroughrows; irow++) {
               int isec = cp.getInteger(dcdbpath + "feedthroughs/sector",irow)-1;
               int isl  = cp.getInteger(dcdbpath + "feedthroughs/superlayer",irow)-1;
               feedThroughExt[isec][isl]    = cp.getDouble(dcdbpath + "feedthroughs/extension", irow);
               feedThroughLength[isec][isl] = cp.getDouble(dcdbpath + "feedthroughs/length", irow);
               feedThroughRmin[isec][isl]   = cp.getDouble(dcdbpath + "feedthroughs/rmin", irow);
               feedThroughRmax[isec][isl]   = cp.getDouble(dcdbpath + "feedthroughs/rmax", irow);
               feedThroughRcurv[isec][isl]  = (Math.pow(feedThroughRmax[isec][isl]-feedThroughRmin[isec][isl], 2)+Math.pow(feedThroughLength[isec][isl], 2))
                                            /(feedThroughRmax[isec][isl]-feedThroughRmin[isec][isl])/2;
        }
        double scaleTest=1;
        int alignrows = cp.length(dcdbpath+"alignment/dx");
        for(int irow = 0; irow< alignrows; irow++) {
               int isec = cp.getInteger(dcdbpath + "alignment/sector",irow)-1;
               int ireg = cp.getInteger(dcdbpath + "alignment/region",irow)-1;

            Vector3d align_delta    = new Vector3d(shifts[ireg][0], shifts[ireg][1], shifts[ireg][2]);
            Vector3d align_position = new Vector3d(scaleTest*cp.getDouble(dcdbpath + "alignment/dx",irow),
                                                   scaleTest*cp.getDouble(dcdbpath + "alignment/dy",irow),
                                                   scaleTest*cp.getDouble(dcdbpath + "alignment/dz",irow));
            align_position = align_position.rotateZ(-isec*Math.toRadians(60));
            align_position = align_position.rotateY(-thtilt[ireg]);
            align_position = align_position.add(align_delta);
            align_position = align_position.rotateY(thtilt[ireg]);
            align_position = align_position.rotateZ(isec*Math.toRadians(60));
            align_dx[isec][ireg]=align_position.x;
            align_dy[isec][ireg]=align_position.y;
            align_dz[isec][ireg]=align_position.z;

            align_dthetax[isec][ireg]=shifts[ireg][3]+scaleTest*cp.getDouble(dcdbpath + "alignment/dtheta_x",irow);
            align_dthetay[isec][ireg]=shifts[ireg][4]+scaleTest*cp.getDouble(dcdbpath + "alignment/dtheta_y",irow);
            align_dthetaz[isec][ireg]=shifts[ireg][5]+scaleTest*cp.getDouble(dcdbpath + "alignment/dtheta_z",irow);
        }
        
        int endplatesrows = cp.length(dcdbpath+"endplatesbow/coefficient");
        for(int irow = 0; irow< endplatesrows; irow++) {
               int isec  = cp.getInteger(dcdbpath + "endplatesbow/sector",irow)-1;
               int ireg  = cp.getInteger(dcdbpath + "endplatesbow/region",irow)-1;
               int order = cp.getInteger(dcdbpath + "endplatesbow/order",irow);
               endplatesbow[isec][ireg][order] = cp.getDouble(dcdbpath+"endplatesbow/coefficient", irow)*Length.cm;
               //System.out.println("READ ENDPLATES COEFF [isec"+isec+"]["+ireg+"]="+endplatesbow[isec][ireg][order] );
        }
    }
    
    public double dist2tgt(int ireg) {
        return dist2tgt[ireg];
    }

    public double xdist(int ireg) {
        return xdist[ireg];
    }

    public double frontgap(int ireg) {
        return frontgap[ireg];
    }

    public double midgap(int ireg) {
        return midgap[ireg];
    }

    public double backgap(int ireg) {
        return backgap[ireg];
    }

    public double thopen(int ireg) {
        return thopen[ireg];
    }

    public double thtilt(int ireg) {
        return thtilt[ireg];
    }

    public double thmin(int isuper) {
        return thmin[isuper];
    }

    public double thster(int isuper) {
        return thster[isuper];
    }

    public double wpdist(int isuper) {
        return wpdist[isuper];
    }

    public double cellthickness(int isuper) {
        return cellthickness[isuper];
    }

    public int nsenselayers(int isuper) {
        return nsenselayers[isuper];
    }

    public int nguardlayers(int isuper) {
        return nguardlayers[isuper];
    }

    public int nfieldlayers(int isuper) {
        return nfieldlayers[isuper];
    }

    public double superwidth(int isuper) {
        return superwidth[isuper];
    }

    public int nsensewires() {
        return nsensewires;
    }

    public int nguardwires() {
        return nguardwires;
    }

    public int nsuperlayers() {
        return nSupers;
    }

    public int nregions() {
        return nRegions;
    }

    public int nsectors() {
        return nSectors;
    }
    
    public double ministagger() {
        return ministagger;
    }
    
    public void setMinistaggerType(DCGeant4Factory.MinistaggerStatus ministaggerStatus) {
        this.ministaggerStatus = ministaggerStatus;
    }

    public DCGeant4Factory.MinistaggerStatus getMinistaggerStatus(){
        return ministaggerStatus;
    }
    
    public double endplatesbow(int isec, int ireg, int order) {
        return endplatesbow[isec][ireg][order];
    }
    
    public void setEndPlatesStatus(boolean endplatesStatus) {
        this.endplatesStatus = endplatesStatus;
    }

    public boolean getEndPlatesStatus(){
        return endplatesStatus;
    }

    public DCGeant4Factory.FeedthroughsStatus feedthroughsStatus() {
        return feedthroughsStatus;
    }

    public void setFeedthroughsStatus(DCGeant4Factory.FeedthroughsStatus feedthroughsStatus) {
        this.feedthroughsStatus = feedthroughsStatus;
    }

    public double feedThroughExt(int isec, int isl) {
        return feedThroughExt[isec][isl];
    }

    public double feedThroughLength(int isec, int isl) {
        return feedThroughLength[isec][isl];
    }

    public double feedThroughRmin(int isec, int isl) {
        return feedThroughRmin[isec][isl];
    }

    public double feedThroughRmax(int isec, int isl) {
        return feedThroughRmax[isec][isl];
    }

    public double feedThroughRcurv(int isec, int isl) {
        return feedThroughRcurv[isec][isl];
    }
    
    public double getAlignmentThetaX(int isec, int ireg) {
        return align_dthetax[isec][ireg];
    }

    public double getAlignmentThetaY(int isec, int ireg) {
        return align_dthetay[isec][ireg];
    }

    public double getAlignmentThetaZ(int isec, int ireg) {
        return align_dthetaz[isec][ireg];
    }

    public Vector3d getAlignmentShift(int isec, int ireg) {
        return new Vector3d(align_dx[isec][ireg], align_dy[isec][ireg], align_dz[isec][ireg]);
    }
}

final class Wire {

    private final int sector;
    private final int ireg;
    private final int isuper;
    private final int layer;
    private final int wire;
    private final DCdatabase dbref = DCdatabase.getInstance();

    private Vector3d midpoint;
    private Vector3d center;
    private Vector3d direction;
    private Vector3d leftend;
    private Vector3d rightend;

    public Wire translate(Vector3d vshift) {
        leftend.add(vshift);
        rightend.add(vshift);
        setCenter();
        setMiddle();
        return this;
    }

    public Wire rotateX(double rotX) {
        direction.rotateX(rotX);
        leftend.rotateX(rotX);
        rightend.rotateX(rotX);
        setCenter();
        setMiddle();
        return this;
    }

    public Wire rotateY(double rotY) {
        direction.rotateY(rotY);
        leftend.rotateY(rotY);
        rightend.rotateY(rotY);
        setCenter();
        setMiddle();
        return this;
    }

    public Wire rotateZ(double rotZ) {
        direction.rotateZ(rotZ);
        leftend.rotateZ(rotZ);
        rightend.rotateZ(rotZ);
        setCenter();
        setMiddle();
        return this;
    }

    private void findEnds() {

        double copen = Math.cos(dbref.thopen(ireg) / 2.0);
        double sopen = Math.sin(dbref.thopen(ireg) / 2.0);

        // define unit vector normal to the endplates of the chamber and pointing inside, projected onto the z=0 plane in the sector frame
        Vector3d rnorm = new Vector3d(copen, sopen, 0);
        Vector3d lnorm = new Vector3d(-copen, sopen, 0);
        // define unit vector parallel to the sides of the chamber and pointing to the chamber tip, projected onto the z=0 plane in the sector frame
        Vector3d rpar  = new Vector3d(sopen, -copen, 0);
        Vector3d lpar  = new Vector3d(-sopen, -copen, 0);
        // define unit vector perpendicular to the layer plane, pointing upstream in the sector frame
        Vector3d vperp = rnorm.cross(rpar).rotateX(-dbref.thtilt(ireg));
        // define unit vectors perpendicular to the end plates in the sector frame
        Vector3d rperp = rnorm.clone().rotateX(-dbref.thtilt(ireg));
        Vector3d lperp = lnorm.clone().rotateX(-dbref.thtilt(ireg));

        // define vector from wire midpoint to chamber tip in the sector frame, projected onto the z=0 plane
        Vector3d vnum = new Vector3d(0, dbref.xdist(ireg), 0);
        if(dbref.feedthroughsStatus()!=DCGeant4Factory.FeedthroughsStatus.OFF)
            vnum.add(0, dbref.feedThroughExt(sector-1,isuper)/copen, 0);
        vnum.sub(midpoint);

        // calculate the end points, exploiting the identity between the component perpendicular to the end plates, projected onto the z=0 plane, 
        // of the vector connecting the chamber tip to the midpoint and the vector connecting the midpoint and the endpoint 
        double wlenl = vnum.dot(lnorm) / direction.dot(lnorm);
        leftend = direction.times(wlenl).add(midpoint);
        double wlenr = vnum.dot(rnorm) / direction.dot(rnorm);
        rightend = direction.times(wlenr).add(midpoint);

        if(dbref.feedthroughsStatus()==DCGeant4Factory.FeedthroughsStatus.OFF) return;
        
        // define the center of the circles that describe the trumpet-like part of the feedthrough in the sector frame
        Vector3d rcirc = rnorm.times(-dbref.feedThroughLength(sector-1, isuper)).add(rpar.times(dbref.feedThroughRmin(sector-1, isuper)+dbref.feedThroughRcurv(sector-1, isuper))).rotateX(-dbref.thtilt(ireg)).add(rightend);
        Vector3d lcirc = lnorm.times(-dbref.feedThroughLength(sector-1, isuper)).add(lpar.times(dbref.feedThroughRmin(sector-1, isuper)+dbref.feedThroughRcurv(sector-1, isuper))).rotateX(-dbref.thtilt(ireg)).add(leftend);

        // recalculate the wire direction assuming the wire is tangent to the left and right circles in the sector frame
        Vector3d newDirection = lcirc.minus(rcirc).normalized();

        // update the wire direction only if the flag is >1
        if(dbref.feedthroughsStatus()==DCGeant4Factory.FeedthroughsStatus.SHIFTANDDIR)
            direction = newDirection;
        
        // recalculate the wire end point in the sector frame
        // first define a vector parallel to the one connecting the circle center and the point at the beginning of the trumpet-like part
        Vector3d rtang = rpar.times(-dbref.feedThroughRcurv(sector-1, isuper)).rotateX(-dbref.thtilt(ireg)); 
        // rotate rtang to be parallel to the vector connecting the circle center to the point where the wire is tangent to the circle
        double rangle = newDirection.angle(rperp); 
        double langle = newDirection.negated().angle(lperp); //not used but kept for reference
        vperp.rotate(rtang,rangle);
        // shift the origin to coincide with the circle center so that the end point is where the wire is tangent to the circle
        rtang = rtang.add(rcirc);
        // recalculate the wire midpoint as the point on the wire line defined by rtang and newDirection with x=0
        midpoint = rtang.plus(newDirection.times(-rtang.x/newDirection.x));
        
        // recalculate the wire endpoints
        vnum = new Vector3d(0, dbref.xdist(ireg)+dbref.feedThroughExt(sector-1,isuper)/copen, 0);
        vnum.sub(midpoint);
        wlenl = vnum.dot(lnorm) / direction.dot(lnorm);
        leftend = direction.times(wlenl).add(midpoint);
        wlenr = vnum.dot(rnorm) / direction.dot(rnorm);
        rightend = direction.times(wlenr).add(midpoint);
    }

    /**
     * Correct for endplates bowing in tilted coordinate system. (ziegler)
     */
    public void correctEnds() {
        double iwirn = (double) wire/112.0;
        //deflection function has to be 1 at extremum (3.8465409 scales it so it is 1 at first derivative)
        double defFunc = 3.8465409*(iwirn - 3 * iwirn*iwirn*iwirn +2 * iwirn*iwirn*iwirn*iwirn);
        //max deflection for L and R sides of wire
        double deflMaxL = dbref.endplatesbow(sector-1, ireg, 0);
        double deflMaxR = dbref.endplatesbow(sector-1, ireg, 1); 
        //deflection of the L and R sides
        double deflL = 0.5 * deflMaxL * defFunc;
        double deflR = 0.5 * deflMaxR * defFunc;
        
        double xL = leftend.x + deflL;
        double xR = rightend.x + deflR;
        double yL = leftend.y;
        double yR = rightend.y;
        
        // the uncorrected wirelength.  We assume the wire length is not changing
        double wlenl = leftend.sub(midpoint).magnitude();
        double wlenr = rightend.sub(midpoint).magnitude();
        //get the modified wire direction
        double n = Math.sqrt((xR - xL)*(xR - xL)+(yR - yL)*(yR - yL));
        direction.set((xR - xL)/n, (yR - yL)/n, 0);
        // midpoint corresponds to y = 0
        midpoint.set(xR -yR*((xR-xL)/(yR-yL)), 0, midpoint.z);
        //get left and right ends assuming the wire length is not changing
        leftend = direction.times(-wlenl).add(midpoint);
        rightend = direction.times(wlenr).add(midpoint);
        
//        if(sector == 4)
//            System.out.println((this.isuper+1)+" "+layer+" "+wire+" "+(float)(xL-deflL)+" "+(float)yL+" "+(float)leftend.z+" "+ 
//                    (float)(xL)+" "+(float)yL+" "+(float)leftend.z+" "+
//                    (float)leftend.x+" "+(float)leftend.y+" "+(float)leftend.z
//            +" "+(float)(xR-deflR)+" "+(float)yR+" "+(float)rightend.z+" "+ 
//                    (float)(xR)+" "+(float)yR+" "+(float)rightend.z+" "+
//                    (float)rightend.x+" "+(float)rightend.y+" "+(float)rightend.z);
    }
    /**
     * 
     * @param sector sector 1...6
     * @param super  superlayer index 0...5
     * @param layer  layer 1...6
     * @param wire   wire 1...112
     */
    public Wire(int sector, int isuperl, int layer, int wire) {
        this.sector  = sector;
        this.isuper  = isuperl;
        this.layer   = layer;
        this.wire    = wire;
        this.ireg    = isuper / 2;

        // calculate first-wire distance from target
        double w2tgt = dbref.dist2tgt(ireg);
        if (isuper % 2 > 0) {
            w2tgt += dbref.superwidth(isuper - 1) + dbref.midgap(ireg);
        }
        w2tgt /= Math.cos(dbref.thtilt(ireg) - dbref.thmin(isuper));

        // y0 and z0 in the lab for the first wire of the layer
        double y0mid = w2tgt * Math.sin(dbref.thmin(isuper));
        double z0mid = w2tgt * Math.cos(dbref.thmin(isuper));

        double cster = Math.cos(dbref.thster(isuper));
        double ctilt = Math.cos(dbref.thtilt(ireg));
        double stilt = Math.sin(dbref.thtilt(ireg));

        double dw = 4 * Math.cos(Math.toRadians(30)) * dbref.wpdist(isuper);
        double dw2 = dw / cster;

        // hh: wire distance in the wire plane
        double hh = (wire-1 + ((double)(layer % 2)) / 2.0) * dw2;
        if(ireg==2 && isSensitiveWire(isuper, layer, wire)) {   // apply the ministagger only to sense wires because guard wires are actually used to define the geant4 volumes
            if(dbref.getMinistaggerStatus()==DCGeant4Factory.MinistaggerStatus.ON) 
                hh += ((layer%2)*2)*dbref.ministagger();
            else if(dbref.getMinistaggerStatus()==DCGeant4Factory.MinistaggerStatus.NOTONREFWIRE)
                hh += ((layer%2)*2-1)*dbref.ministagger();
        }
                

        // ll: layer distance
        double tt = dbref.cellthickness(isuper) * dbref.wpdist(isuper);
        double ll = layer * tt;

        // wire x=0 coordinates in the lab
        double ym = y0mid + ll * stilt + hh * ctilt;
        double zm = z0mid + ll * ctilt - hh * stilt;

        // wire midpoint in the lab
        midpoint = new Vector3d(0, ym, zm);
        direction = new Vector3d(1, 0, 0);
        direction.rotateZ(dbref.thster(isuper));
        direction.rotateX(-dbref.thtilt(ireg));
        findEnds();
        center = leftend.plus(rightend).dividedBy(2.0);
    }

    private boolean isSensitiveWire(int isuper, int ilayer, int iwire) {
        return iwire>0 && iwire<=dbref.nsensewires() &&
                ilayer>0 && ilayer<=dbref.nsenselayers(isuper);
    }

    private void setCenter() {  
        center.set(leftend.plus(rightend).dividedBy(2.0));
    }
    
    private void setMiddle() {
        double t = -leftend.y/direction.y;
        midpoint.set(leftend.plus(direction.times(t)));
    }
        
    public Vector3d mid() {
        return new Vector3d(midpoint);
    }

    public Vector3d left() {
        return new Vector3d(leftend);
    }

    public Vector3d right() {
        return new Vector3d(rightend);
    }

    public Vector3d dir() {
        return new Vector3d(direction);
    }

    public Vector3d top() {
        if (leftend.y < rightend.y) {
            return new Vector3d(rightend);
        }
        return new Vector3d(leftend);
    }

    public Vector3d bottom() {
        if (leftend.y < rightend.y) {
            return new Vector3d(leftend);
        }
        return new Vector3d(rightend);
    }

    public double length() {
        return leftend.minus(rightend).magnitude();
    }

    public Vector3d center() {
        return new Vector3d(center);
    }
}

///////////////////////////////////////////////////
public final class DCGeant4Factory extends Geant4Factory {

    private static final Logger LOGGER = Logger.getLogger("DCGeant4Factory");
    DCdatabase dbref = null;
    
    private final HashMap<String, String> properties = new HashMap<>();
    private int nsgwires;

    private final double y_enlargement = 3.65;
    private final double z_enlargement = -2.46;
    private final double microgap = 0.01;

    private final Wire[][][][] wires;
    private final Vector3d[][][] layerMids;
    private final Vector3d[][] regionMids;
    
    public static enum MinistaggerStatus {
        OFF          ( 0, "OFF"),          // no ministagger
        NOTONREFWIRE ( 1, "NOTONREFWIRE"), // ministagger is applied assuming the reference wire position, defined by dist2tgt and thmin, has no ministagger
        ON           ( 2, "ON");           // ministagger is applied assuming the reference wire position, defined by dist2tgt and thmin, HAS ministagger (default)
        
        private final int id;
        private final String name;
        private final static MinistaggerStatus DEFAULT = ON;
        
        private MinistaggerStatus(int id, String name) { 
            this.id = id; 
            this.name = name;
        }
        
        public int getId() { return id; }
        public String getName() { return name; }
        
        public static MinistaggerStatus getStatus(String name) {
            if(name!=null) {
                name = name.trim();
                for(MinistaggerStatus status: MinistaggerStatus.values())
                    if (status.getName().equalsIgnoreCase(name)) 
                        return status;
            }
            LOGGER.log(Level.WARNING, "Invalid MinistaggerStatus value, setting default status " + DEFAULT.getName());
            return DEFAULT;
        }   
    
        // this method is to support the old API that was accepting booleans as inputs
        public static MinistaggerStatus getStatus(boolean status) {
            return status ? ON : OFF;
        }    
    }

    public static enum FeedthroughsStatus {
        OFF         ( 0, "OFF"),         // do not account for the feedthroughs
        SHIFT       ( 1, "SHIFT"),       // account for wire midpoint shift only (default)
        SHIFTANDDIR ( 2, "SHIFTANDDIR"); // account for wire shift and tilt
        
        private final int id;
        private final String name;
        private final static FeedthroughsStatus DEFAULT = SHIFT;
        
        private FeedthroughsStatus(int id, String name) { 
            this.id = id; 
            this.name = name;
        }
        
        public int getId() { return id; }
        public String getName() { return name; }
        
        public static FeedthroughsStatus getStatus(String name) {
            if(name!=null) {
                name = name.trim();
                for(FeedthroughsStatus status: FeedthroughsStatus.values())
                    if (status.getName().equalsIgnoreCase(name)) 
                        return status;
            }
            LOGGER.log(Level.WARNING, "Invalid FeedthroughsStatus value, setting default status " + DEFAULT.getName());
            return DEFAULT;
        }    
    }

    public static boolean ENDPLATESBOWON=true;
    public static boolean ENDPLATESBOWOFF=false;

    ///////////////////////////////////////////////////
    public DCGeant4Factory(ConstantProvider provider) {
        this(provider, MinistaggerStatus.OFF, FeedthroughsStatus.SHIFT, ENDPLATESBOWOFF, null);
    }

    ///////////////////////////////////////////////////
    public DCGeant4Factory(ConstantProvider provider, 
                        boolean ministaggerStatus,
                        boolean endplatesStatus) {
        this(provider, MinistaggerStatus.getStatus(ministaggerStatus), FeedthroughsStatus.SHIFT, endplatesStatus, null);
    }
        
    ///////////////////////////////////////////////////
    public DCGeant4Factory(ConstantProvider provider, 
                        boolean ministaggerStatus,
                        boolean endplatesStatus, 
                        double[][] shifts) { 
        this(provider, MinistaggerStatus.getStatus(ministaggerStatus), FeedthroughsStatus.SHIFT, endplatesStatus, shifts);
    }
    
    ///////////////////////////////////////////////////
    public DCGeant4Factory(ConstantProvider provider, 
                           MinistaggerStatus ministaggerStatus,
                           FeedthroughsStatus feedthroughsStatus,
                           boolean endplatesStatus, 
                           double[][] shifts) {
        DefaultLogger.debug();
        dbref = DCdatabase.getInstance();
        dbref.setMinistaggerType(ministaggerStatus);
        dbref.setFeedthroughsStatus(feedthroughsStatus);
        dbref.setEndPlatesStatus(endplatesStatus);
        LOGGER.log(Level.INFO, "DC Geometry Factory configured with:" + 
                         "\n\t ministagger: " + dbref.getMinistaggerStatus().getName() +
                         "\n\t feedthroughs: " + dbref.feedthroughsStatus().getName() +
                         "\n\t endplates bow: " + dbref.getEndPlatesStatus());
        
        motherVolume = new G4World("root");

        dbref.connect(provider, shifts);
        nsgwires = dbref.nsensewires() + dbref.nguardwires();

        for (int iregion = 0; iregion < 3; iregion++) {
            for (int isector = 0; isector < 6; isector++) {
                Geant4Basic regionVolume = createRegion(isector, iregion);
                regionVolume.setMother(motherVolume);
            }
        }

        properties.put("email", "mestayer@jlab.org");
        properties.put("author", "mestayer");
        properties.put("date", "05/08/16");

        // define wire and layer points in tilted coordinate frame (z axis is perpendicular to the chamber, y is along the wire)
        wires = new Wire[dbref.nsectors()][dbref.nsuperlayers()][][];
        layerMids = new Vector3d[dbref.nsectors()][dbref.nsuperlayers()][];
        regionMids = new Vector3d[dbref.nsectors()][dbref.nregions()];

        for(int isec = 0; isec < dbref.nsectors(); isec++) {
            for(int iregion=0; iregion<dbref.nregions(); iregion++) {
                regionMids[isec][iregion] = getRegion(isec, iregion).getGlobalPosition()
				.add(dbref.getAlignmentShift(isec, iregion))
				.rotateZ(Math.toRadians(-isec * 60))
				.rotateY(-dbref.thtilt(iregion));
/*
                //define layerMid using wires (produce slight shift compared to GEMC volumes)
                regionMids[isec][iregion] = new Vector3d(layerMids[isec][iregion*2][dbref.nsenselayers(iregion*2)-1]);
                regionMids[isec][iregion] = regionMids[isec][iregion].plus(layerMids[isec][iregion*2+1][0]).dividedBy(2.0);
*/
            }

            for(int isuper=0; isuper<dbref.nsuperlayers(); isuper++) {
                layerMids[isec][isuper]  = new Vector3d[dbref.nsenselayers(isuper)];

                for(int ilayer=0; ilayer<dbref.nsenselayers(isuper); ilayer++) {
/*
                    //define layerMid using wires (produce slight shift compared to GEMC volumes)
                    Wire firstWire = new Wire(isuper, layer+1, 1);
                    Wire lastWire = new Wire(isuper, layer+1, dbref.nsensewires());
                    Vector3d firstMid = firstWire.mid().rotateZ(Math.toRadians(-90.0)).rotateY(-dbref.thtilt(isuper/2));
                    Vector3d lastMid = lastWire.mid().rotateZ(Math.toRadians(-90.0)).rotateY(-dbref.thtilt(isuper/2));
                    layerMids[isec][isuper][layer] = firstMid.plus(lastMid).dividedBy(2.0);
*/
                    layerMids[isec][isuper][ilayer] = getLayer(isec, isuper, ilayer).getGlobalPosition()
					.add(dbref.getAlignmentShift(isec, isuper/2))
					.rotateZ(Math.toRadians(- isec * 60))
					.rotateY(-dbref.thtilt(isuper/2));
                }
            }

            for(int isuper=0; isuper<dbref.nsuperlayers(); isuper++) {
                wires[isec][isuper]   = new Wire[dbref.nsenselayers(isuper)][dbref.nsensewires()];
                for(int ilayer=0; ilayer<dbref.nsenselayers(isuper); ilayer++) {
                    layerMids[isec][isuper][ilayer].add(regionMids[isec][isuper/2].times(-1.0));
                    layerMids[isec][isuper][ilayer].rotateZ(Math.toRadians(dbref.getAlignmentThetaZ(isec, isuper/2)));
                    layerMids[isec][isuper][ilayer].rotateX(Math.toRadians(dbref.getAlignmentThetaX(isec, isuper/2)));
                    layerMids[isec][isuper][ilayer].rotateY(Math.toRadians(dbref.getAlignmentThetaY(isec, isuper/2)));
                    layerMids[isec][isuper][ilayer].add(regionMids[isec][isuper/2]);

                    for(int iwire=0; iwire<dbref.nsensewires(); iwire++) {
                        wires[isec][isuper][ilayer][iwire] = new Wire(isec+1, isuper, ilayer+1, iwire+1);
                        //rotate in tilted sector coordinate system
                        wires[isec][isuper][ilayer][iwire].rotateZ(Math.toRadians(-90.0 + isec * 60))
						.translate(dbref.getAlignmentShift(isec, isuper/2))
						.rotateZ(Math.toRadians(-isec * 60))
						.rotateY(-dbref.thtilt(isuper/2));
                        
                        //implement end-plates bow in the tilted sector coordinate system (ziegler)
                        if(dbref.getEndPlatesStatus())
                            wires[isec][isuper][ilayer][iwire].correctEnds();
                        //dc alignment implementation
                        wires[isec][isuper][ilayer][iwire].translate(regionMids[isec][isuper/2].times(-1.0));
                        wires[isec][isuper][ilayer][iwire].rotateZ(Math.toRadians(dbref.getAlignmentThetaZ(isec, isuper/2)));
                        wires[isec][isuper][ilayer][iwire].rotateX(Math.toRadians(dbref.getAlignmentThetaX(isec, isuper/2)));
                        wires[isec][isuper][ilayer][iwire].rotateY(Math.toRadians(dbref.getAlignmentThetaY(isec, isuper/2)));
                        wires[isec][isuper][ilayer][iwire].translate(regionMids[isec][isuper/2]);
                    }
                }

            }
        }
    }

    public Vector3d getWireMidpoint(int isec, int isuper, int ilayer, int iwire) {
        return wires[isec][isuper][ilayer][iwire].mid();
    }

    public Vector3d getWireLeftend(int isec, int isuper, int ilayer, int iwire) {
        return wires[isec][isuper][ilayer][iwire].left();
    }

    public Vector3d getWireRightend(int isec, int isuper, int ilayer, int iwire) {
        return wires[isec][isuper][ilayer][iwire].right();
    }

    public Vector3d getRegionMidpoint(int isec, int iregion) {
        return regionMids[isec][iregion].clone();
    }

    public Vector3d getLayerMidpoint(int isec, int isuper, int ilayer) {
        return layerMids[isec][isuper][ilayer].clone();
    }

    public Vector3d getWireMidpoint(int isuper, int ilayer, int iwire) {
        return wires[0][isuper][ilayer][iwire].mid();
    }

    public Vector3d getWireLeftend(int isuper, int ilayer, int iwire) {
        return wires[0][isuper][ilayer][iwire].left();
    }

    public Vector3d getWireRightend(int isuper, int ilayer, int iwire) {
        return wires[0][isuper][ilayer][iwire].right();
    }

    public Vector3d getRegionMidpoint(int iregion) {
        return regionMids[0][iregion].clone();
    }

    public Vector3d getLayerMidpoint(int isuper, int ilayer) {
        return layerMids[0][isuper][ilayer].clone();
    }

    public Vector3d getWireDirection(int isuper, int ilayer, int iwire) {
        return wires[0][isuper][ilayer][iwire].dir();
    }

    private Geant4Basic getRegion(int isec, int ireg) {
        return motherVolume.getChildren().get(ireg*6+isec);
    }

    private Geant4Basic getLayer(int isec, int isuper, int ilayer) {
        return getRegion(isec, isuper/2).getChildren().get((isuper%2)*6 + ilayer);
    }
    ///////////////////////////////////////////////////
    public Geant4Basic createRegion(int isector, int iregion) {
        Wire regw0 = new Wire(isector+1, iregion * 2, 0, 0);
        Wire regw1 = new Wire(isector+1, iregion * 2 + 1, 7, nsgwires - 1);

        double dx_shift = y_enlargement * Math.tan(Math.toRadians(29.5));

        double reg_dz = (dbref.frontgap(iregion) + dbref.backgap(iregion) + dbref.midgap(iregion) + dbref.superwidth(iregion * 2) + dbref.superwidth(iregion * 2 + 1)) / 2.0 + z_enlargement;
        double reg_dx0 = Math.abs(regw0.bottom().x) - dx_shift + 1.0;
        double reg_dx1 = Math.abs(regw1.top().x) + dx_shift + 1.0;
        double reg_dy = regw1.top().minus(regw0.bottom()).y / Math.cos(dbref.thtilt(iregion)) / 2.0 + y_enlargement + 1.0;
        double reg_skew = 0.0;
        double reg_thtilt = dbref.thtilt(iregion);

        Vector3d vcenter = regw1.top().plus(regw0.bottom()).dividedBy(2.0);
        vcenter.x = 0;
        Vector3d reg_position0 = new Vector3d(vcenter.x, vcenter.y, vcenter.z);
        vcenter.rotateZ(-Math.toRadians(90 - isector * 60));

        Geant4Basic regionVolume = new G4Trap("region" + (iregion + 1) + "_s" + (isector + 1),
                reg_dz, -reg_thtilt, Math.toRadians(90.0),
                reg_dy, reg_dx0, reg_dx1, 0.0,
                reg_dy, reg_dx0, reg_dx1, 0.0);
        regionVolume.rotate("yxz", 0.0, reg_thtilt, Math.toRadians(90.0 - isector * 60.0));
        regionVolume.translate(vcenter.x, vcenter.y, vcenter.z);
        regionVolume.setId(isector + 1, iregion + 1, 0, 0);

        for (int isup = 0; isup < 2; isup++) {
            int isuper = iregion * 2 + isup;
            Geant4Basic superlayerVolume = this.createSuperlayer(isuper);
            superlayerVolume.setName("sl" + (isuper + 1) + "_s" + (isector + 1));

            Vector3d slcenter = superlayerVolume.getLocalPosition();
            Vector3d slshift = slcenter.minus(reg_position0);
            slshift.rotateX(reg_thtilt);

            superlayerVolume.rotate("zxy", -dbref.thster(isuper), 0.0, 0.0);

            superlayerVolume.setPosition(slshift.x, slshift.y, slshift.z);
            superlayerVolume.setMother(regionVolume);
            superlayerVolume.setId(isector + 1, iregion + 1, isuper + 1);
            
            int nsglayers = dbref.nsenselayers(isuper) + dbref.nguardlayers(isuper);
            for (int ilayer = 1; ilayer < nsglayers - 1; ilayer++) {
                Geant4Basic layerVolume = this.createLayer(isuper, ilayer);
                layerVolume.setName("sl" + (isuper + 1) + "_layer" + ilayer + "_s" + (isector + 1));

                Vector3d lcenter = layerVolume.getLocalPosition();
                Vector3d lshift = lcenter.minus(reg_position0);
                lshift.rotateX(reg_thtilt);

                layerVolume.rotate("zxy", -dbref.thster(isuper), 0.0, 0.0);

                layerVolume.setPosition(lshift.x, lshift.y, lshift.z);
                layerVolume.setMother(regionVolume);
                layerVolume.setId(isector + 1, iregion + 1, isuper + 1, ilayer);
            }
        }

        return regionVolume;
    }


    /**
     * Create GEANT4 superlayer volume:
     * - from first to last guard wire in layer=0 to define y 
     * - from first to last guard wire plane in z 
     * @param isuper
     * @return
     */
    public Geant4Basic createSuperlayer(int isuper) {
        int nsglayers = dbref.nsenselayers(isuper) + dbref.nguardlayers(isuper);
        Wire lw0 = new Wire(1, isuper, 0, 0);
        Wire lw1 = new Wire(1, isuper, 0, nsgwires - 1);
        Wire lw2 = new Wire(1, isuper, 2, 0);

        Vector3d yline = lw1.mid().minus(lw0.mid());
        double lay_dy = Math.sqrt(Math.pow(yline.magnitude(), 2.0) - Math.pow(yline.dot(lw0.dir()), 2.0)) / 2.0;
        double lay_dx0 = lw0.length() / 2.0;
        double lay_dx1 = lw1.length() / 2.0;
        double lay_dz = (dbref.cellthickness(isuper)*dbref.nsenselayers(isuper)+1) * dbref.wpdist(isuper)/Math.cos(dbref.thtilt(isuper/2))/ 2.0;
        double lay_skew = lw0.center().minus(lw1.center()).angle(lw1.dir()) - Math.toRadians(90.0);

        Vector3d zline = lw2.mid().minus(lw0.mid()).normalized();
        Vector3d lcent = lw0.center().plus(lw1.center()).dividedBy(2.0).plus(zline.times(lay_dz));
        
        G4Trap superlayerVolume = new G4Trap("sl" + (isuper + 1),
                lay_dz, -dbref.thtilt(isuper / 2), Math.toRadians(90.0),
                lay_dy, lay_dx0, lay_dx1, lay_skew,
                lay_dy, lay_dx0, lay_dx1, lay_skew);

        superlayerVolume.setPosition(lcent.x, lcent.y, lcent.z);

        return superlayerVolume;
    }

    ///////////////////////////////////////////////////
    public Geant4Basic createLayer(int isuper, int ilayer) {
        Wire lw0 = new Wire(1, isuper, ilayer, 0);
        Wire lw1 = new Wire(1, isuper, ilayer, nsgwires - 1);

        Vector3d midline = lw1.mid().minus(lw0.mid());
        double lay_dy = Math.sqrt(Math.pow(midline.magnitude(), 2.0) - Math.pow(midline.dot(lw0.dir()), 2.0)) / 2.0;
        double lay_dx0 = lw0.length() / 2.0;
        double lay_dx1 = lw1.length() / 2.0;
        double lay_dz = dbref.cellthickness(isuper) * dbref.wpdist(isuper) / 2.0 - microgap;
        double lay_skew = lw0.center().minus(lw1.center()).angle(lw1.dir()) - Math.toRadians(90.0);

        Vector3d lcent = lw0.center().plus(lw1.center()).dividedBy(2.0);
        G4Trap layerVolume = new G4Trap("sl" + (isuper + 1) + "_layer" + ilayer,
                lay_dz, -dbref.thtilt(isuper / 2), Math.toRadians(90.0),
                lay_dy, lay_dx0, lay_dx1, lay_skew,
                lay_dy, lay_dx0, lay_dx1, lay_skew);

        layerVolume.setPosition(lcent.x, lcent.y, lcent.z);

        return layerVolume;
    }

    public Trap3D getTrajectorySurface(int isector, int isuperlayer, int ilayer) {
        Wire lw0 = new Wire(isector+1, isuperlayer, ilayer+1, 0);
        Wire lw1 = new Wire(isector+1, isuperlayer, ilayer+1, nsgwires - 1);
        
        // move to CLAS12 frame
        Vector3d p0 = lw0.right().rotateZ(Math.toRadians(-90 + isector*60));
        Vector3d p1 = lw0.left().rotateZ(Math.toRadians(-90 + isector*60));
        Vector3d p2 = lw1.left().rotateZ(Math.toRadians(-90 + isector*60)); 
        Vector3d p3 = lw1.right().rotateZ(Math.toRadians(-90 + isector*60)); 
        
        // define left and right side lines and their intersection
        Line3D left  = new Line3D(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
        Line3D right = new Line3D(p0.x, p0.y, p0.z, p3.x, p3.y, p3.z);
        Point3D v = left.distance(right).origin();
        
        // shift the left or right origin, depending on distance from "triangle" vertex
        double dleft  = v.distance(left.origin());
        double dright = v.distance(right.origin());
        if(dleft<dright)
            left.setOrigin(left.lerpPoint((dright-dleft)/left.length()));
        else 
            right.setOrigin(right.lerpPoint((dleft-dright)/right.length()));
        
        // shift the left or right end, depending on lengths
        double lleft  = left.length();
        double lright = right.length();
        if(lleft<lright)
            right.setEnd(right.lerpPoint(lleft/lright));
        else 
            left.setEnd(left.lerpPoint(lright/lleft));
        
        Trap3D trapezoid = new Trap3D(right.origin(), left.origin(), left.end(), right.end());
        
        return trapezoid;
    } 

    
    public double getCellSize(int isuperlayer) {
        return dbref.cellthickness(isuperlayer);
    }
    
    /*
    public void printWires(){
        System.out.println("hello");
        for(int isup=0;isup<2;isup++)
        for(int il=0;il<8;il+=7)
            for(int wire=0;wire<nsgwires+1;wire+=nsgwires/30){
        Wire regw = new Wire(isup,il,wire);
        System.out.println("line("+regw.left()+", "+regw.right()+");");
            }
    }
    */
    
    public static void main(String[] args) {
        
        ConstantProvider provider = GeometryFactory.getConstants(DetectorType.DC, 11, "default");
        DCGeant4Factory dc0 = new DCGeant4Factory(provider, MinistaggerStatus.ON, FeedthroughsStatus.OFF, false, null);
        DCGeant4Factory dc1 = new DCGeant4Factory(provider, MinistaggerStatus.ON, FeedthroughsStatus.SHIFT, false, null);
        DCGeant4Factory dc2 = new DCGeant4Factory(provider, MinistaggerStatus.ON, FeedthroughsStatus.SHIFTANDDIR, false, null);
        
        for(int il=0; il<36; il++) {
            for(int iw=0; iw<112; iw=iw+111) {
                int sector = 1;
                int layer = il+1;
                int wire = iw+1;
                int isuper = il/6;
                int ilayer = il%6;


                System.out.println(sector + " " + layer + " " + wire + " " +
                                   Math.toDegrees(Math.acos(-dc0.getWireDirection(isuper, ilayer, iw).y))*Math.signum(dc0.getWireDirection(isuper, ilayer, iw).x) + " " + 
                                   dc0.getWireMidpoint(isuper, ilayer, iw) + " " +
                                   dc0.getWireDirection(isuper, ilayer, iw) + " " +
                                   dc0.getWireLeftend(isuper, ilayer, iw) + " " +
                                   dc0.getWireRightend(isuper, ilayer, iw) + " ");
                System.out.println(sector + " " + layer + " " + wire + " " +
                                   Math.toDegrees(Math.acos(-dc1.getWireDirection(isuper, ilayer, iw).y))*Math.signum(dc1.getWireDirection(isuper, ilayer, iw).x) + " " + 
                                   dc1.getWireMidpoint(isuper, ilayer, iw) + " " +
                                   dc1.getWireDirection(isuper, ilayer, iw) + " " +
                                   dc1.getWireLeftend(isuper, ilayer, iw) + " " +
                                   dc1.getWireRightend(isuper, ilayer, iw) + " ");
                System.out.println(sector + " " + layer + " " + wire + " " +
                                   Math.toDegrees(Math.acos(-dc2.getWireDirection(isuper, ilayer, iw).y))*Math.signum(dc2.getWireDirection(isuper, ilayer, iw).x) + " " + 
                                   dc2.getWireMidpoint(isuper, ilayer, iw) + " " +
                                   dc2.getWireDirection(isuper, ilayer, iw) + " " +
                                   dc2.getWireLeftend(isuper, ilayer, iw) + " " +
                                   dc2.getWireRightend(isuper, ilayer, iw) + " ");
            }
        }
    }
}
