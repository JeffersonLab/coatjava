package org.jlab.rec.dc.cross;

import Jama.Matrix;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.timetodistance.TimeToDistanceEstimator;
import org.jlab.utils.groups.IndexedTable;
import trackfitter.fitter.LineFitter;
import org.jlab.rec.urwell.reader.URWellCross;

/**
 * A class to build 4-crosses or 3-crosses combos
 *
 * @author Tongtong Cao
 */

public class URWellDCCrossesListFinder {

    public URWellDCCrossesListFinder() {
    }

    public URWellDCCrossesList candURWellDCCrossLists(List<URWellCross> urCrosses, CrossList crosslist) {
        URWellDCCrossesList urDCCrossesList = new URWellDCCrossesList();

        for (int i = 0; i < urCrosses.size(); i++) {
            for (int j = 0; j < crosslist.size(); j++) {
                URWellCross urCross = urCrosses.get(i);
                List<Cross> dcCrosses = crosslist.get(j);

                if (urCross.sector() == dcCrosses.get(0).get_Sector() && urCross.sector() == dcCrosses.get(1).get_Sector() && urCross.sector() == dcCrosses.get(2).get_Sector()) {
                    urDCCrossesList.add_URWellDCCrosses(urCross, dcCrosses);
                }
            }
        }

        return urDCCrossesList;
    }

    /**
     *
     * @param dccrosslist the list of crosses in the event
     * @return the list of crosses determined to be consistent with belonging to
     * a track in the DC
     */
    ClusterFitter cf = new ClusterFitter();

    public URWellDCCrossesList candURWellDCCrossesLists(DataEvent event,
            List<Cross> dccrosslist, List<URWellCross> urCrosses, boolean TimeBased,
            IndexedTable tab, DCGeant4Factory DcDetector, TimeToDistanceEstimator tde,
            Swim swimmer, boolean FOOS, int nReqCrosses) {

        URWellDCCrossesList urDCCrossesList = new URWellDCCrossesList();

        if (dccrosslist.size() > 0) {
            List<Cross> dccrosslistRg1 = new ArrayList<>();
            List<Cross> dccrosslistRg2 = new ArrayList<>();
            List<Cross> dccrosslistRg3 = new ArrayList<>();

            for (Cross dc : dccrosslist) {
                if (dc.get_Region() == 1) {
                    dccrosslistRg1.add(dc);
                }
                if (dc.get_Region() == 2) {
                    dccrosslistRg2.add(dc);
                }
                if (dc.get_Region() == 3) {
                    dccrosslistRg3.add(dc);
                }
            }
            if (nReqCrosses == 4) { // need 4 crosses

                TrajectoryParametriz qf1 = new TrajectoryParametriz(nReqCrosses);
                double[] X = new double[4];
                double[] Y = new double[4];
                double[] Z = new double[4];
                double[] errX = new double[4];
                double[] errY = new double[4];
                Vector3D traj1 = new Vector3D(0, 0, 0);
                Vector3D traj2 = new Vector3D(0, 0, 0);
                Vector3D traj3 = new Vector3D(0, 0, 0);

                if (!dccrosslistRg1.isEmpty() && !dccrosslistRg2.isEmpty() && !dccrosslistRg3.isEmpty() && !urCrosses.isEmpty()) {
                    for (Cross c1 : dccrosslistRg1) {
                        for (Cross c2 : dccrosslistRg2) {
                            for (Cross c3 : dccrosslistRg3) {
                                for (URWellCross urCross : urCrosses) {
                                    if (urCross.sector() != c1.get_Sector() || urCross.sector() != c2.get_Sector() || urCross.sector() != c3.get_Sector()) {
                                        this.clear(X, Y, Z, errX, errY);
                                        continue;
                                    }
                                    if (FOOS == true) {
                                        if (c1.get_Id() != -1 && c2.get_Id() != -1 && c3.get_Id() != -1) {
                                            this.clear(X, Y, Z, errX, errY);
                                            continue;
                                        }
                                    }

                                    Z[0] = c1.get_Point().z();
                                    Y[0] = c1.get_Point().y();
                                    X[0] = c1.get_Point().x();
                                    errX[0] = c1.get_PointErr().x();
                                    errY[0] = c1.get_PointErr().y();
                                    Z[1] = c2.get_Point().z();
                                    Y[1] = c2.get_Point().y();
                                    X[1] = c2.get_Point().x();
                                    errX[1] = c2.get_PointErr().x();
                                    errY[1] = c2.get_PointErr().y();
                                    Z[2] = c3.get_Point().z();
                                    Y[2] = c3.get_Point().y();
                                    X[2] = c3.get_Point().x();
                                    errX[2] = c3.get_PointErr().x();
                                    errY[2] = c3.get_PointErr().y();
                                    Z[3] = urCross.local().z();
                                    Y[3] = urCross.local().y();
                                    X[3] = urCross.local().x();
                                    errX[3] = Constants.URWELLXRESOLUTION;
                                    errY[3] = Constants.URWELLYRESOLUTION;
                                    // ignore point errors and assume the track vertex is close to the origin
                                    for (int j = 0; j < 6; j++) {
                                        this.clear(qf1.fitResult[j]);
                                    }
                                    qf1.evaluate(Z, X, errX, Y, errY);

                                    traj1.setXYZ(qf1.fitResult[3][0], qf1.fitResult[4][0], qf1.fitResult[5][0]);
                                    traj2.setXYZ(qf1.fitResult[3][1], qf1.fitResult[4][1], qf1.fitResult[5][1]);
                                    traj3.setXYZ(qf1.fitResult[3][2], qf1.fitResult[4][2], qf1.fitResult[5][2]);

                                    double cosTh1 = traj1.dot(c1.get_Dir().toVector3D());
                                    double cosTh2 = traj2.dot(c2.get_Dir().toVector3D());
                                    double cosTh3 = traj3.dot(c3.get_Dir().toVector3D());
                                    
                                    // require that the cross direction estimate be in the direction of the trajectory
                                    if (cosTh1 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh2 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh3 < Constants.TRACKDIRTOCROSSDIRCOSANGLE) {
                                        continue;
                                    }

                                    double fitchsq = 0;

                                    if (c1.isPseudoCross == false) {
                                        fitchsq += ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y()) * ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y());
                                    }
                                    if (c2.isPseudoCross == false) {
                                        fitchsq += ((qf1.fitResult[1][1] - c2.get_Point().y()) / c2.get_PointErr().y()) * ((qf1.fitResult[1][1] - c2.get_Point().y()) / c2.get_PointErr().y());
                                    }
                                    if (c3.isPseudoCross == false) {
                                        fitchsq += ((qf1.fitResult[1][2] - c3.get_Point().y()) / c3.get_PointErr().y()) * ((qf1.fitResult[1][2] - c3.get_Point().y()) / c3.get_PointErr().y());
                                    }
                                    fitchsq += ((qf1.fitResult[1][3] - urCross.local().y()) / Constants.URWELLYRESOLUTION) * ((qf1.fitResult[1][3] - urCross.local().y()) / Constants.URWELLYRESOLUTION);

                                    // fit the  projection with a line -- the track is ~ constant in phi
                                    LineFitter linefit = new LineFitter();
                                    boolean linefitstatusOK = linefit.fitStatus(X, Y, errX, errY, Z.length);
                                    if (!linefitstatusOK) {
                                        continue; // fit failed
                                    }

                                    this.updateBFittedHits(event, c1, tab, DcDetector, tde, swimmer);
                                    this.updateBFittedHits(event, c2, tab, DcDetector, tde, swimmer);
                                    this.updateBFittedHits(event, c3, tab, DcDetector, tde, swimmer);

                                    if (fitchsq < Constants.CROSSLISTSELECTQFMINCHSQ) {
                                        List<Cross> dcCrosses = new ArrayList<Cross>();
                                        dcCrosses.add(c1);
                                        dcCrosses.add(c2);
                                        dcCrosses.add(c3);
                                        urDCCrossesList.add_URWellDCCrosses(urCross, dcCrosses);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (nReqCrosses == 3) { // need 3 crosses

                TrajectoryParametriz qf1 = new TrajectoryParametriz(nReqCrosses);
                double[] X = new double[3];
                double[] Y = new double[3];
                double[] Z = new double[3];
                double[] errX = new double[3];
                double[] errY = new double[3];
                Vector3D traj1 = new Vector3D(0, 0, 0);
                Vector3D traj2 = new Vector3D(0, 0, 0);
                Vector3D traj3 = new Vector3D(0, 0, 0);

                if (!dccrosslistRg1.isEmpty() && !dccrosslistRg2.isEmpty() && !dccrosslistRg3.isEmpty()) { // Cases for R1R2R3
                    for (Cross c1 : dccrosslistRg1) {
                        for (Cross c2 : dccrosslistRg2) {
                            for (Cross c3 : dccrosslistRg3) {
                                if (c1.get_Sector() != c2.get_Sector() || c1.get_Sector() != c3.get_Sector()) {
                                    this.clear(X, Y, Z, errX, errY);
                                    continue;
                                }
                                if (FOOS == true) {
                                    if (c1.get_Id() != -1 && c2.get_Id() != -1 && c3.get_Id() != -1) {
                                        this.clear(X, Y, Z, errX, errY);
                                        continue;
                                    }
                                }

                                Z[0] = c1.get_Point().z();
                                Y[0] = c1.get_Point().y();
                                X[0] = c1.get_Point().x();
                                errX[0] = c1.get_PointErr().x();
                                errY[0] = c1.get_PointErr().y();
                                Z[1] = c2.get_Point().z();
                                Y[1] = c2.get_Point().y();
                                X[1] = c2.get_Point().x();
                                errX[1] = c2.get_PointErr().x();
                                errY[1] = c2.get_PointErr().y();
                                Z[2] = c3.get_Point().z();
                                Y[2] = c3.get_Point().y();
                                X[2] = c3.get_Point().x();
                                errX[2] = c3.get_PointErr().x();
                                errY[2] = c3.get_PointErr().y();

                                // ignore point errors and assume the track vertex is close to the origin
                                for (int j = 0; j < 6; j++) {
                                    this.clear(qf1.fitResult[j]);
                                }
                                qf1.evaluate(Z, X, errX, Y, errY);

                                traj1.setXYZ(qf1.fitResult[3][0], qf1.fitResult[4][0], qf1.fitResult[5][0]);
                                traj2.setXYZ(qf1.fitResult[3][1], qf1.fitResult[4][1], qf1.fitResult[5][1]);
                                traj3.setXYZ(qf1.fitResult[3][2], qf1.fitResult[4][2], qf1.fitResult[5][2]);

                                double cosTh1 = traj1.dot(c1.get_Dir().toVector3D());
                                double cosTh2 = traj2.dot(c2.get_Dir().toVector3D());
                                double cosTh3 = traj3.dot(c3.get_Dir().toVector3D());

                                // require that the cross direction estimate be in the direction of the trajectory
                                if (cosTh1 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh2 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh3 < Constants.TRACKDIRTOCROSSDIRCOSANGLE) {
                                    continue;
                                }

                                double fitchsq = 0;

                                if (c1.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y()) * ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y());
                                }
                                if (c2.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][1] - c2.get_Point().y()) / c2.get_PointErr().y()) * ((qf1.fitResult[1][1] - c2.get_Point().y()) / c2.get_PointErr().y());
                                }
                                if (c3.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][2] - c3.get_Point().y()) / c3.get_PointErr().y()) * ((qf1.fitResult[1][2] - c3.get_Point().y()) / c3.get_PointErr().y());
                                }

                                // fit the  projection with a line -- the track is ~ constant in phi
                                LineFitter linefit = new LineFitter();
                                boolean linefitstatusOK = linefit.fitStatus(X, Y, errX, errY, Z.length);
                                if (!linefitstatusOK) {
                                    continue; // fit failed
                                }

                                this.updateBFittedHits(event, c1, tab, DcDetector, tde, swimmer);
                                this.updateBFittedHits(event, c2, tab, DcDetector, tde, swimmer);
                                this.updateBFittedHits(event, c3, tab, DcDetector, tde, swimmer);

                                if (fitchsq < Constants.CROSSLISTSELECTQFMINCHSQ) {
                                    List<Cross> dcCrosses = new ArrayList<Cross>();
                                    dcCrosses.add(c1);
                                    dcCrosses.add(c2);
                                    dcCrosses.add(c3);
                                    urDCCrossesList.add_URWellDCCrosses(null, dcCrosses);
                                }
                            }
                        }
                    }
                }
                else if(!dccrosslistRg2.isEmpty() && !dccrosslistRg3.isEmpty() && !urCrosses.isEmpty()){ // Cases for R2R3R0
                    for (Cross c2 : dccrosslistRg2) {
                        for (Cross c3 : dccrosslistRg3) {
                            for (URWellCross urCross : urCrosses) {
                                if (urCross.sector() != c2.get_Sector() || urCross.sector() != c3.get_Sector()) {
                                    this.clear(X, Y, Z, errX, errY);
                                    continue;
                                }
                                if (FOOS == true) {
                                    if (c2.get_Id() != -1 && c3.get_Id() != -1) {
                                        this.clear(X, Y, Z, errX, errY);
                                        continue;
                                    }
                                }

                                Z[0] = c2.get_Point().z();
                                Y[0] = c2.get_Point().y();
                                X[0] = c2.get_Point().x();
                                errX[0] = c2.get_PointErr().x();
                                errY[0] = c2.get_PointErr().y();
                                Z[1] = c3.get_Point().z();
                                Y[1] = c3.get_Point().y();
                                X[1] = c3.get_Point().x();
                                errX[1] = c3.get_PointErr().x();
                                errY[1] = c3.get_PointErr().y();
                                Z[2] = urCross.local().z();
                                Y[2] = urCross.local().y();
                                X[2] = urCross.local().x();
                                errX[2] = Constants.URWELLXRESOLUTION;
                                errY[2] = Constants.URWELLYRESOLUTION;

                                // ignore point errors and assume the track vertex is close to the origin
                                for (int j = 0; j < 6; j++) {
                                    this.clear(qf1.fitResult[j]);
                                }
                                qf1.evaluate(Z, X, errX, Y, errY);

                                traj2.setXYZ(qf1.fitResult[3][0], qf1.fitResult[4][0], qf1.fitResult[5][0]);
                                traj3.setXYZ(qf1.fitResult[3][1], qf1.fitResult[4][1], qf1.fitResult[5][1]);

                                double cosTh2 = traj2.dot(c2.get_Dir().toVector3D());
                                double cosTh3 = traj3.dot(c3.get_Dir().toVector3D());

                                // require that the cross direction estimate be in the direction of the trajectory
                                if (cosTh2 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh3 < Constants.TRACKDIRTOCROSSDIRCOSANGLE) {
                                    continue;
                                }

                                double fitchsq = 0;

                                if (c2.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][0] - c2.get_Point().y()) / c2.get_PointErr().y()) * ((qf1.fitResult[1][0] - c2.get_Point().y()) / c2.get_PointErr().y());
                                }
                                if (c3.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][1] - c3.get_Point().y()) / c3.get_PointErr().y()) * ((qf1.fitResult[1][1] - c3.get_Point().y()) / c3.get_PointErr().y());
                                }
                                fitchsq += ((qf1.fitResult[1][2] - urCross.local().y()) / Constants.URWELLYRESOLUTION) * ((qf1.fitResult[1][2] - urCross.local().y()) / Constants.URWELLYRESOLUTION);

                                // fit the  projection with a line -- the track is ~ constant in phi
                                LineFitter linefit = new LineFitter();
                                boolean linefitstatusOK = linefit.fitStatus(X, Y, errX, errY, Z.length);
                                if (!linefitstatusOK) {
                                    continue; // fit failed
                                }

                                this.updateBFittedHits(event, c2, tab, DcDetector, tde, swimmer);
                                this.updateBFittedHits(event, c3, tab, DcDetector, tde, swimmer);

                                if (fitchsq < Constants.CROSSLISTSELECTQFMINCHSQ) {
                                    List<Cross> dcCrosses = new ArrayList<Cross>();
                                    dcCrosses.add(c2);
                                    dcCrosses.add(c3);
                                    urDCCrossesList.add_URWellDCCrosses(urCross, dcCrosses);
                                }
                            }
                        }
                    }
                }
                else if (!dccrosslistRg1.isEmpty() && !dccrosslistRg3.isEmpty() && !urCrosses.isEmpty()) { // Cases for R1R3R0
                    for (Cross c1 : dccrosslistRg1) {
                        for (Cross c3 : dccrosslistRg3) {
                            for (URWellCross urCross : urCrosses) {
                                if (urCross.sector() != c1.get_Sector() || urCross.sector() != c3.get_Sector()) {
                                    this.clear(X, Y, Z, errX, errY);
                                    continue;
                                }
                                if (FOOS == true) {
                                    if (c1.get_Id() != -1 && c3.get_Id() != -1) {
                                        this.clear(X, Y, Z, errX, errY);
                                        continue;
                                    }
                                }

                                Z[0] = c1.get_Point().z();
                                Y[0] = c1.get_Point().y();
                                X[0] = c1.get_Point().x();
                                errX[0] = c1.get_PointErr().x();
                                errY[0] = c1.get_PointErr().y();
                                Z[1] = c3.get_Point().z();
                                Y[1] = c3.get_Point().y();
                                X[1] = c3.get_Point().x();
                                errX[1] = c3.get_PointErr().x();
                                errY[1] = c3.get_PointErr().y();
                                Z[2] = urCross.local().z();
                                Y[2] = urCross.local().y();
                                X[2] = urCross.local().x();
                                errX[2] = Constants.URWELLXRESOLUTION;
                                errY[2] = Constants.URWELLYRESOLUTION;

                                // ignore point errors and assume the track vertex is close to the origin
                                for (int j = 0; j < 6; j++) {
                                    this.clear(qf1.fitResult[j]);
                                }
                                qf1.evaluate(Z, X, errX, Y, errY);

                                traj1.setXYZ(qf1.fitResult[3][0], qf1.fitResult[4][0], qf1.fitResult[5][0]);
                                traj3.setXYZ(qf1.fitResult[3][1], qf1.fitResult[4][1], qf1.fitResult[5][1]);

                                double cosTh1 = traj1.dot(c1.get_Dir().toVector3D());
                                double cosTh3 = traj3.dot(c3.get_Dir().toVector3D());
                                
                                // require that the cross direction estimate be in the direction of the trajectory
                                if (cosTh1 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh3 < Constants.TRACKDIRTOCROSSDIRCOSANGLE) {
                                    continue;
                                }
                                
                                double fitchsq = 0;

                                if (c1.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y()) * ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y());
                                }
                                if (c3.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][1] - c3.get_Point().y()) / c3.get_PointErr().y()) * ((qf1.fitResult[1][1] - c3.get_Point().y()) / c3.get_PointErr().y());
                                }
                                fitchsq += ((qf1.fitResult[1][2] - urCross.local().y()) / Constants.URWELLYRESOLUTION) * ((qf1.fitResult[1][2] - urCross.local().y()) / Constants.URWELLYRESOLUTION);
                                
                                // fit the  projection with a line -- the track is ~ constant in phi
                                LineFitter linefit = new LineFitter();
                                boolean linefitstatusOK = linefit.fitStatus(X, Y, errX, errY, Z.length);
                                if (!linefitstatusOK) {
                                    continue; // fit failed
                                }

                                this.updateBFittedHits(event, c1, tab, DcDetector, tde, swimmer);
                                this.updateBFittedHits(event, c3, tab, DcDetector, tde, swimmer);

                                if (fitchsq < Constants.CROSSLISTSELECTQFMINCHSQ) {
                                    List<Cross> dcCrosses = new ArrayList<Cross>();
                                    dcCrosses.add(c1);
                                    dcCrosses.add(c3);
                                    urDCCrossesList.add_URWellDCCrosses(urCross, dcCrosses);
                                }
                            }
                        }
                    }
                }
                else if (!dccrosslistRg1.isEmpty() && !dccrosslistRg2.isEmpty() && !urCrosses.isEmpty()) { // Cases for R1R2R0
                    for (Cross c1 : dccrosslistRg1) {
                        for (Cross c2 : dccrosslistRg2) {
                            for (URWellCross urCross : urCrosses) {
                                if (urCross.sector() != c1.get_Sector() || urCross.sector() != c2.get_Sector()) {
                                    this.clear(X, Y, Z, errX, errY);
                                    continue;
                                }
                                if (FOOS == true) {
                                    if (c1.get_Id() != -1 && c2.get_Id() != -1) {
                                        this.clear(X, Y, Z, errX, errY);
                                        continue;
                                    }
                                }

                                Z[0] = c1.get_Point().z();
                                Y[0] = c1.get_Point().y();
                                X[0] = c1.get_Point().x();
                                errX[0] = c1.get_PointErr().x();
                                errY[0] = c1.get_PointErr().y();
                                Z[1] = c2.get_Point().z();
                                Y[1] = c2.get_Point().y();
                                X[1] = c2.get_Point().x();
                                errX[1] = c2.get_PointErr().x();
                                errY[1] = c2.get_PointErr().y();
                                Z[2] = urCross.local().z();
                                Y[2] = urCross.local().y();
                                X[2] = urCross.local().x();
                                errX[2] = Constants.URWELLXRESOLUTION;
                                errY[2] = Constants.URWELLYRESOLUTION;

                                // ignore point errors and assume the track vertex is close to the origin
                                for (int j = 0; j < 6; j++) {
                                    this.clear(qf1.fitResult[j]);
                                }
                                qf1.evaluate(Z, X, errX, Y, errY);

                                traj1.setXYZ(qf1.fitResult[3][0], qf1.fitResult[4][0], qf1.fitResult[5][0]);
                                traj2.setXYZ(qf1.fitResult[3][1], qf1.fitResult[4][1], qf1.fitResult[5][1]);

                                double cosTh1 = traj1.dot(c1.get_Dir().toVector3D());
                                double cosTh2 = traj2.dot(c2.get_Dir().toVector3D());
                                
                                // require that the cross direction estimate be in the direction of the trajectory
                                if (cosTh1 < Constants.TRACKDIRTOCROSSDIRCOSANGLE || cosTh2 < Constants.TRACKDIRTOCROSSDIRCOSANGLE) {
                                    continue;
                                }

                                double fitchsq = 0;

                                if (c1.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y()) * ((qf1.fitResult[1][0] - c1.get_Point().y()) / c1.get_PointErr().y());
                                }
                                if (c2.isPseudoCross == false) {
                                    fitchsq += ((qf1.fitResult[1][1] - c2.get_Point().y()) / c2.get_PointErr().y()) * ((qf1.fitResult[1][1] - c2.get_Point().y()) / c2.get_PointErr().y());
                                }
                                fitchsq += ((qf1.fitResult[1][2] - urCross.local().y()) / Constants.URWELLYRESOLUTION) * ((qf1.fitResult[1][2] - urCross.local().y()) / Constants.URWELLYRESOLUTION);
                                
                                // fit the  projection with a line -- the track is ~ constant in phi
                                LineFitter linefit = new LineFitter();
                                boolean linefitstatusOK = linefit.fitStatus(X, Y, errX, errY, Z.length);
                                if (!linefitstatusOK) {
                                    continue; // fit failed
                                }

                                this.updateBFittedHits(event, c1, tab, DcDetector, tde, swimmer);
                                this.updateBFittedHits(event, c2, tab, DcDetector, tde, swimmer);

                                if (fitchsq < Constants.CROSSLISTSELECTQFMINCHSQ) {
                                    List<Cross> dcCrosses = new ArrayList<Cross>();
                                    dcCrosses.add(c1);
                                    dcCrosses.add(c2);
                                    urDCCrossesList.add_URWellDCCrosses(urCross, dcCrosses);
                                }
                            }
                        }
                    }
                }
                
            }
        }

        return urDCCrossesList;
    }

    @SuppressWarnings("unused")
    private void RecalculateCrossDir(Cross c1, double slope) {
        double val_sl2 = c1.get_Segment2().get_fittedCluster().get_clusterLineFitSlope();
        double tanThX = val_sl2;
        double tanThY = slope;
        double uz = 1. / Math.sqrt(1 + tanThX * tanThX + tanThY * tanThY);
        double ux = uz * tanThX;
        double uy = uz * tanThY;
        // set the dir	
        c1.set_Dir(new Point3D(ux, uy, uz));
    }

    @SuppressWarnings("unused")
    private void RecalculateCrossDirErr(Cross c1, double slope, double slopeErr) {
        // Error calculation
        double val_sl2 = c1.get_Segment2().get_fittedCluster().get_clusterLineFitSlope();
        double tanThX = val_sl2;
        double tanThY = slope;
        double del_tanThX = c1.get_Segment2().get_fittedCluster().get_clusterLineFitSlopeErr();
        double del_tanThY = slopeErr;
        double uz = 1. / Math.sqrt(1 + tanThX * tanThX + tanThY * tanThY);
        double del_uz = uz * uz * uz * Math.sqrt(tanThX * tanThX * del_tanThX * del_tanThX + tanThY * tanThY * del_tanThY * del_tanThY);
        double del_ux = Math.sqrt(tanThX * tanThX * del_uz * del_uz + uz * uz * del_tanThX * del_tanThX);
        double del_uy = Math.sqrt(tanThY * tanThY * del_uz * del_uz + uz * uz * del_tanThY * del_tanThY);

        double err_xDir = del_ux;
        double err_yDir = del_uy;
        double err_zDir = del_uz;

        Point3D estimDirErr = new Point3D(err_xDir, err_yDir, err_zDir);

        c1.set_DirErr(estimDirErr);
    }

    private void recalcParsSegment(DataEvent event, Segment _Segment1, IndexedTable tab, DCGeant4Factory DcDetector, TimeToDistanceEstimator tde) {
        //refit
        double trkAngle = _Segment1.get_fittedCluster().get_clusterLineFitSlope();
        // update the hits
        for (FittedHit fhit : _Segment1.get_fittedCluster()) {
            fhit.updateHitPositionWithTime(event, trkAngle, fhit.getB(), tab, DcDetector, tde);
        }

        cf.SetFitArray(_Segment1.get_fittedCluster(), "TSC");
        cf.Fit(_Segment1.get_fittedCluster(), true);
        trkAngle = _Segment1.get_fittedCluster().get_clusterLineFitSlope();

        for (FittedHit fhit : _Segment1.get_fittedCluster()) {
            fhit.updateHitPositionWithTime(event, trkAngle, fhit.getB(), tab, DcDetector, tde);
        }
        cf.SetFitArray(_Segment1.get_fittedCluster(), "TSC");
        cf.Fit(_Segment1.get_fittedCluster(), true);
        cf.SetResidualDerivedParams(_Segment1.get_fittedCluster(), true, false, DcDetector); //calcTimeResidual=false, resetLRAmbig=false 

        cf.SetFitArray(_Segment1.get_fittedCluster(), "TSC");
        cf.Fit(_Segment1.get_fittedCluster(), false);

        cf.SetSegmentLineParameters(_Segment1.get_fittedCluster().get(0).get_Z(), _Segment1.get_fittedCluster());

    }

    public List<List<Cross>> get_CrossesInSectors(List<Cross> crosses) {
        List<List<Cross>> CrossesBySectors = new ArrayList<>();

        for (int s = 0; s < 6; s++) {
            CrossesBySectors.add(s, new ArrayList<>());
        }
        for (Cross cross : crosses) {
            //if(cross.isPseudoCross==false)
            CrossesBySectors.get(cross.get_Sector() - 1).add(cross);
        }
        return CrossesBySectors;
    }

    /**
     *
     * @param event
     * @param c cross
     * @param tab table of constants
     * @param DcDetector detector geometry
     * @param tde time-to-distance utility Updates the B-field information of
     * the hits in the cross segments
     * @param swimmer
     */
    public void updateBFittedHits(DataEvent event, Cross c, IndexedTable tab, DCGeant4Factory DcDetector, TimeToDistanceEstimator tde, Swim swimmer) {
        for (int i = 0; i < c.get_Segment1().size(); i++) {
            Point3D ref = c.get_Segment1().get(i).getCrossDirIntersWire();
            float[] result = new float[3];
            swimmer.Bfield(c.get_Sector(), ref.x(), ref.y(), ref.z(), result);
            c.get_Segment1().get(i).setB(Math.sqrt(result[0] * result[0] + result[1] * result[1] + result[2] * result[2]));

        }
        for (int i = 0; i < c.get_Segment2().size(); i++) {
            Point3D ref = c.get_Segment2().get(i).getCrossDirIntersWire();
            float[] result = new float[3];
            swimmer.Bfield(c.get_Sector(), ref.x(), ref.y(), ref.z(), result);
            c.get_Segment2().get(i).setB(Math.sqrt(result[0] * result[0] + result[1] * result[1] + result[2] * result[2]));
        }
        if (tde != null) {
            this.recalcParsSegment(event, c.get_Segment1(), tab, DcDetector, tde);
            this.recalcParsSegment(event, c.get_Segment2(), tab, DcDetector, tde);
        }
        //remake cross
        c.set_CrossParams(DcDetector);
    }

    private void clear(double[]... X) {
        for (double[] x : X) {
            for (int i = 0; i < x.length; i++) {
                x[i] = 0;
            }
        }
    }

    private class TrajectoryParametriz {

        private int nReqCrosses = 3;

        private double[][] fitResult = {{0., 0., 0., 0.},
        {0., 0., 0., 0.},
        {0., 0., 0., 0.},
        {0., 0., 0., 0.},
        {0., 0., 0., 0.},
        {0., 0., 0., 0.}};

        public TrajectoryParametriz(int nReqCrosses) {
            this.nReqCrosses = nReqCrosses;
        }

        public double[] evaluate(double[] x, double[] y, double[] err, double[] y2, double[] err2) {
            LineFitter linefit = new LineFitter();
            linefit.fitStatus(x, y2, err, err, x.length);
            double[] ret = {0., 0., 0.};
            Matrix A = new Matrix(3, 3);
            Matrix V = new Matrix(3, 1);
            double sum1 = 0.0;
            double sum2 = 0.0;
            double sum3 = 0.0;
            double sum4 = 0.0;
            double sum5 = 0.0;
            double sum6 = 0.0;
            double sum7 = 0.0;
            double sum8 = 0.0;
            for (int i = 0; i < x.length; ++i) {
                double y1 = y[i];
                double x1 = x[i];
                double x2 = x1 * x1;
                double x3 = x2 * x1;
                double x4 = x2 * x2;
                double e2 = err[i] * err[i];
                sum1 += x4 / e2;
                sum2 += x3 / e2;
                sum3 += x2 / e2;
                sum4 += x1 / e2;
                sum5 += 1.0 / e2;
                sum6 += y1 * x2 / e2;
                sum7 += y1 * x1 / e2;
                sum8 += y1 / e2;
            }
            A.set(0, 0, sum1);
            A.set(0, 1, sum2);
            A.set(0, 2, sum3);
            A.set(1, 0, sum2);
            A.set(1, 1, sum3);
            A.set(1, 2, sum4);
            A.set(2, 0, sum3);
            A.set(2, 1, sum4);
            A.set(2, 2, sum5);
            V.set(0, 0, sum6);
            V.set(1, 0, sum7);
            V.set(2, 0, sum8);
            Matrix Ainv = A.inverse();
            Matrix X;
            try {
                X = Ainv.times(V);
                for (int i = 0; i < 3; ++i) {
                    ret[i] = X.get(i, 0);
                }
                for (int i = 0; i < x.length; i++) {

                    double tiltSysXterm = ret[0] * x[i] * x[i] + ret[1] * x[i] + ret[2];
                    double tiltSysYterm = linefit.getFit().slope() * x[i] + linefit.getFit().intercept();
                    double tiltSysZterm = x[i];

                    double dl = 0.01;
                    double dQ = 2. * ret[0] * x[i] * dl + ret[1] * dl;
                    double dL = linefit.getFit().slope() * dl;
                    double Len = Math.sqrt(dl * dl + dQ * dQ + dL * dL);

                    double tiltSysdirXterm = dQ / Len;
                    double tiltSysdirYterm = dL / Len;
                    double tiltSysdirZterm = dl / Len;

                    fitResult[0][i] = tiltSysXterm;
                    fitResult[1][i] = tiltSysYterm;
                    fitResult[2][i] = tiltSysZterm;
                    fitResult[3][i] = tiltSysdirXterm;
                    fitResult[4][i] = tiltSysdirYterm;
                    fitResult[5][i] = tiltSysdirZterm;

                    //double n = ret[0]*x[i]*x[i]+ret[1]*x[i]+ret[2] - y[i];
                }
            } catch (ArithmeticException e) {
                // TODO Auto-generated catch block
            }
            return (ret);
        }
    }

}
