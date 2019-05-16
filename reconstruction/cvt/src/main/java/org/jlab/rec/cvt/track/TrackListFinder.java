package org.jlab.rec.cvt.track;

import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.swimtools.Swim;

import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.hit.FittedHit;
import org.jlab.rec.cvt.trajectory.Trajectory;
import org.jlab.rec.cvt.trajectory.TrajectoryFinder;

public class TrackListFinder {
    
    public TrackListFinder() {
        // TODO Auto-generated constructor stub
    }

    /**
     *
     * @param cands the list of track candidates
     * @param svt_geo the svt geometry
     * @return the list of selected tracks
     */
    public List<Track> getTracks(List<Track> cands, 
            org.jlab.rec.cvt.svt.Geometry svt_geo, org.jlab.rec.cvt.bmt.Geometry bmt_geo,
            Swim bstSwim) {
        List<Track> tracks = new ArrayList<Track>();
        if (cands.size() == 0) {
            System.err.print("Error no tracks found");
            return cands;
        }

        // loop over candidates and set the trajectories
        
        for (Track trk : cands) {
            if(trk.get_helix()!=null) {
                this.assignTrkPID(trk);
                //KalFit kf = new KalFit(trk, svt_geo);
                //kf.runKalFit(trk, svt_geo);
                //EnergyLossCorr elc = new EnergyLossCorr(trk);
                //System.out.println("******* before EL "+trk.get_P());
                //elc.doCorrection(trk, svt_geo);
                //System.out.println("*******  after EL "+trk.get_P());

                int charge = trk.get_Q();
                double maxPathLength = 5.0;//very loose cut 
                bstSwim.SetSwimParameters(trk.get_helix().xdca() / 10, trk.get_helix().ydca() / 10, trk.get_helix().get_Z0() / 10, 
                        Math.toDegrees(trk.get_helix().get_phi_at_dca()), Math.toDegrees(Math.acos(trk.get_helix().costheta())),
                        trk.get_P(), charge, 
                        maxPathLength) ;

                double[] pointAtCylRad = bstSwim.SwimToCylinder(Constants.CTOFINNERRADIUS/10);
                trk.set_TrackPointAtCTOFRadius(new Point3D(pointAtCylRad[0]*10, pointAtCylRad[1]*10, pointAtCylRad[2]*10));
                trk.set_TrackDirAtCTOFRadius(new Vector3D(pointAtCylRad[3]*10, pointAtCylRad[4]*10, pointAtCylRad[5]*10));

                trk.set_pathLength(pointAtCylRad[6]*10);

                TrajectoryFinder trjFind = new TrajectoryFinder();

                Trajectory traj = trjFind.findTrajectory(trk.get_Id(), trk.get_helix(), trk, svt_geo, bmt_geo, "final");

                trk.set_Trajectory(traj.get_Trajectory());

                //if(trk.passCand == true)
                tracks.add(trk);
            }

        }
        return tracks;
    }

    private void assignTrkPID(Track trk) {

        int NbHits = 0;
        double TotE = 0;
        for (int i = 0; i < trk.size(); i++) {
            if (trk.get(i).get_Detector() != "SVT") {
                continue;
            }
            for (int j = 0; j < trk.get(i).get_Cluster1().size(); j++) {
                TotE += trk.get(i).get_Cluster1().get(j).get_Strip().get_Edep();
                NbHits++;
            }
            for (int j = 0; j < trk.get(i).get_Cluster2().size(); j++) {
                TotE += trk.get(i).get_Cluster2().get(j).get_Strip().get_Edep();
                NbHits++;
            }
        }
        TotE /= (double) NbHits;

        if (TotE <= org.jlab.rec.cvt.Constants.PIDCUTOFF) {
            trk.set_PID("pion");
        }
        if (TotE > org.jlab.rec.cvt.Constants.PIDCUTOFF) {
            trk.set_PID("proton");
        }
    }
    
    public void removeBadTracks(List<Track> trkcands) {
    	if(trkcands==null)
            return;
        
    	int initial_size=trkcands.size();
    	
        for(int i =1; i<initial_size+1; i++) {
        	if (Double.isNaN(trkcands.get(initial_size-i).getChi2())) {
        		trkcands.remove(initial_size-i);
        		continue;
        	}
        	if (trkcands.get(initial_size-i).getChi2()>Constants.Nsigma_per_point*Constants.Nsigma_per_point*(trkcands.get(initial_size-i).getNDF()+5)) {
        		trkcands.remove(initial_size-i);
        		continue;
        	}
        	if (trkcands.get(initial_size-i).getNDF()<Constants.NDF_Min) {
        		trkcands.remove(initial_size-i);
        		continue;
        	}
        	if (trkcands.get(initial_size-i).get_Pt()<Constants.Pt_Min) {
        		trkcands.remove(initial_size-i);
        		continue;
        	}
        }
    }
     
    public void removeOverlappingTracks(List<Track> trkcands) {
            if(trkcands==null)
                return;
            
            List<Track> selectedTracks =new ArrayList<Track>();
            List<Track> list = new  ArrayList<Track>();
            List<Track> rejected = new  ArrayList<Track>();
            for(int i =0; i<trkcands.size(); i++) { 
                
                list.clear();
                if(trkcands.get(i)==null) {
                    continue;
                }
                if( rejected.contains( trkcands.get(i) ) ) continue;
                
                this.getOverlapLists(trkcands.get(i), trkcands, list);
                
                Track selectedTrk = this.FindBestTrack(list);
                if(selectedTrk==null)
                    continue;
                if(selectedTracks.contains(selectedTrk)==false)
                        selectedTracks.add(selectedTrk);
                
                list.remove(selectedTrk);

                for( Track t : list ) {
                	if( ! rejected.contains(t) ) rejected.add(t);
                }

            }
            if( rejected != null )
            	selectedTracks.removeAll(rejected);
            if(trkcands!=null)
                trkcands.removeAll(trkcands);
            if(selectedTracks!=null)
                trkcands.addAll(selectedTracks);
            
            //Let's assign the id of tracks since now they are supposed to be the best.
            for (int c = 0; c < trkcands.size(); c++) {
                trkcands.get(c).set_Id(c + 1);
            }
    }

    private boolean ListContainsTrack(List<Track> selectedTracks, Track selectedTrk) { 
            // not used. Now Track extends Comparables
            boolean isInList = false;
            for(Track trk : selectedTracks) {
                    if(trk.get_Id()==selectedTrk.get_Id())
                            isInList=true;
            }
            return isInList;
    }


    private void getOverlapLists(Track track, List<Track> trkcands, List<Track> list) {
    // --------------------------------------------------------------------
    //  two tracks are considered the same if they share at least 2 crosses
    // --------------------------------------------------------------------
			
    	for( Track t : trkcands ) {
    		int N = 0;
    		for( Cross c : t ) {
    			// do not check on BMTC
    	        //if( c.get_DetectorType().equalsIgnoreCase("C") == true ) continue;
    			if( track.contains(c) ) { N++;  }
    		}
    		if( N >= 2 ) list.add( t );
    	}
    	
    }

    private Track FindBestTrack(List<Track> trkList) {
    // --------------------------------------------------------------------
    //  Select the candidate with the highest number of NDF
    //    if two have the same ndf, get the one with the better chi2/ndf
    // --------------------------------------------------------------------
            double bestChi2 = 9999999;
            int ndf = 12;
            Track bestTrk = null;
            //The best and smallest track
            for (int i =0; i<trkList.size(); i++) {
            	if (trkList.get(i).getNDF()>ndf||(trkList.get(i).getNDF()==ndf&&trkList.get(i).getChi2()<bestChi2)) {
            		ndf=trkList.get(i).getNDF();
            		bestTrk=trkList.get(i);
            		bestChi2=trkList.get(i).getChi2();
            	}
            }
            
            //Once we have the best and smallest track, we look if each point added to this track are not too far off (for the moment we set the limit at N_sigma_per_point)
            for(int i =0; i<trkList.size(); i++) {
            	if (trkList.get(i).getNDF()<ndf&&trkList.get(i).getChi2()<bestChi2+(trkList.get(i).getNDF()-ndf)* Constants.Nsigma_per_point* Constants.Nsigma_per_point){
            		ndf=trkList.get(i).getNDF();
            		bestTrk=trkList.get(i);
            		bestChi2=trkList.get(i).getChi2();
            	}
                   
            }
            return bestTrk;
    }

	public void updateCrosses(List<Track> trks, List<ArrayList<Cross>> crosses) {
		ArrayList<Cross> temp=new ArrayList<Cross>();
		
		//Loop over the track
		for (int t = 0; t < trks.size(); t++) {
			//Loop ovver the measurements/state vectors
			for (int tp = 0; tp < trks.get(t).getTrajectory().size(); tp++) {

               if (trks.get(t).getTrajectory().get(tp).layer<7) {//It is SVT
                	for (int jj=0 ; jj < crosses.get(0).size(); jj++) {//So we loop over svt crosses
                		//If we have the cluster 1
                		if (trks.get(t).getTrajectory().get(tp).clusID==crosses.get(0).get(jj).get_Cluster1().get_Id()) {
                			crosses.get(0).get(jj).set_Point(new Point3D(trks.get(t).getTrajectory().get(tp).xdet,trks.get(t).getTrajectory().get(tp).ydet,trks.get(t).getTrajectory().get(tp).zdet));
            	   			crosses.get(0).get(jj).set_Dir(new Vector3D(trks.get(t).getTrajectory().get(tp).dirx,trks.get(t).getTrajectory().get(tp).diry,trks.get(t).getTrajectory().get(tp).dirz));
            	   			crosses.get(0).get(jj).get_Cluster1().set_CentroidResidual(trks.get(t).getTrajectory().get(tp).residual);
            	   			crosses.get(0).get(jj).get_Cluster1().set_AssociatedTrackID(trks.get(t).get_Id());
            	   			crosses.get(0).get(jj).set_AssociatedTrackID(trks.get(t).get_Id());
            	   			for (FittedHit h : crosses.get(0).get(jj).get_Cluster1()) {
                                h.set_AssociatedTrackID(trks.get(t).get_Id());
                            }
        					temp.add(crosses.get(0).get(jj).Duplicate());
        					temp.get(temp.size()-1).set_Id(-crosses.get(0).get(jj).get_Id());
                		}
                		
                		//If we have the cluster 2
                		if (trks.get(t).getTrajectory().get(tp).clusID==crosses.get(0).get(jj).get_Cluster2().get_Id()) {
                			crosses.get(0).get(jj).set_Point(new Point3D(trks.get(t).getTrajectory().get(tp).xdet,trks.get(t).getTrajectory().get(tp).ydet,trks.get(t).getTrajectory().get(tp).zdet));
            	   			crosses.get(0).get(jj).set_Dir(new Vector3D(trks.get(t).getTrajectory().get(tp).dirx,trks.get(t).getTrajectory().get(tp).diry,trks.get(t).getTrajectory().get(tp).dirz));
            	   			crosses.get(0).get(jj).get_Cluster2().set_CentroidResidual(trks.get(t).getTrajectory().get(tp).residual);
            	   			crosses.get(0).get(jj).get_Cluster2().set_AssociatedTrackID(trks.get(t).get_Id());
            	   			crosses.get(0).get(jj).set_AssociatedTrackID(trks.get(t).get_Id());
            	   			for (FittedHit h : crosses.get(0).get(jj).get_Cluster2()) {
                                h.set_AssociatedTrackID(trks.get(t).get_Id());
                            }
        					
                		}
                	}
               }
               else {
            	   	for (int jj=0 ; jj < crosses.get(1).size(); jj++) {//So we loop over bmt crosses
            	   		if (trks.get(t).getTrajectory().get(tp).clusID==crosses.get(1).get(jj).get_Cluster1().get_Id()) {
            	   			crosses.get(1).get(jj).set_Point(new Point3D(trks.get(t).getTrajectory().get(tp).xdet,trks.get(t).getTrajectory().get(tp).ydet,trks.get(t).getTrajectory().get(tp).zdet));
            	   			crosses.get(1).get(jj).set_Dir(new Vector3D(trks.get(t).getTrajectory().get(tp).dirx,trks.get(t).getTrajectory().get(tp).diry,trks.get(t).getTrajectory().get(tp).dirz));
            	   			crosses.get(1).get(jj).get_Cluster1().set_CentroidResidual(trks.get(t).getTrajectory().get(tp).residual);
            	   			crosses.get(1).get(jj).get_Cluster1().set_AssociatedTrackID(trks.get(t).get_Id());
            	   			crosses.get(1).get(jj).set_AssociatedTrackID(trks.get(t).get_Id());
            	   			for (FittedHit h : crosses.get(1).get(jj).get_Cluster1()) {
                                h.set_AssociatedTrackID(trks.get(t).get_Id());
                            }
            	   		}
            	   	}
               }
               
			}
		}
		crosses.get(0).addAll(temp);
	}

}
