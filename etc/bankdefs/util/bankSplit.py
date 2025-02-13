#!/usr/bin/env python

import json
import sys
import os

# print usage
if len(sys.argv)<2:
   print("usage: bankSplit.py coatjavahipobankfolder (e.g. coatjava/etc/bankdefs/hipo4/)")
   sys.exit()

# hipo schema directory
hipodirectory = sys.argv[1] +  "/"
os.chdir(hipodirectory)

# single jsons directory
workdirectory   = "singles/"
singledirectory = "full/"
os.mkdir(workdirectory)
os.mkdir(workdirectory + singledirectory)

# create schema directory
def create(dirname, banklist):
    os.mkdir(workdirectory + dirname)
    os.chdir(workdirectory + dirname)
    for bank in banklist:
        src = "../" + singledirectory + bank + ".json"
        if not os.path.exists(src):
            print('ERROR:  Bank not found:  '+bank)
            sys.exit(1)
        os.symlink(src, bank + ".json")
    os.chdir("../../")
    print("Json file links created in " + workdirectory + dirname)

# for each json file in hipo schema folder
for filename in os.listdir("./"):
    if filename.endswith(".json") and not filename.startswith("clas6"):
        #Read JSON data into the datastore variable
        f = open(filename)
        try:
            datastore = json.load(f)
        except:
            print('Invalid JSON:  '+filename)
            sys.exit(1)
        # loop over banks in the json file
        for bank in datastore:
            bankname = bank['name']
            file = open(workdirectory + singledirectory + bankname + ".json", 'w')
            file.write(json.dumps([bank], sort_keys=True, indent=4))
            file.close
print("Single json files saved in " + workdirectory + singledirectory)

# these should *always* be kept:
mc = ["MC::Event", "MC::GenMatch", "MC::Header", "MC::Lund", "MC::Particle", "MC::RecMatch", "MC::True"]
tag1 = ["RUN::config", "RAW::epics", "RAW::scaler", "RUN::scaler", "COAT::config", "HEL::flip", "HEL::online", "HEL::decoder"]

# these are the output of the event builder:
rectb   = ["REC::Event","REC::Particle","REC::Calorimeter","REC::CaloExtras","REC::Cherenkov","REC::CovMat","REC::ForwardTagger","REC::Scintillator","REC::ScintExtras","REC::Track","REC::UTrack","REC::Traj","RECFT::Event","RECFT::Particle"]
rechb   = ["RECHB::Event","RECHB::Particle","RECHB::Calorimeter","RECHB::CaloExtras","RECHB::Cherenkov","RECHB::ForwardTagger","RECHB::Scintillator","RECHB::ScintExtras","RECHB::Track"]
rectbai = ["RECAI::Event","RECAI::Particle","RECAI::Calorimeter","RECAI::CaloExtras","RECAI::Cherenkov","RECAI::CovMat","RECAI::ForwardTagger","RECAI::Scintillator","RECAI::ScintExtras","RECAI::Track","RECAI::Traj","RECAIFT::Event","RECAIFT::Particle"]
rechbai = ["RECHBAI::Event","RECHBAI::Particle","RECHBAI::Calorimeter","RECHBAI::CaloExtras","RECHBAI::Cherenkov","RECHBAI::ForwardTagger","RECHBAI::Scintillator","RECHBAI::ScintExtras","RECHBAI::Track"]

# special, detector-specific raw banks that are kept in DSTs (for now):
band   = ["BAND::laser","BAND::adc","BAND::tdc","BAND::hits","BAND::rawhits"]
raster = ["RASTER::position"]
rich   = ["RICH::tdc","RICH::Ring","RICH::Particle"]
rtpc   = ["RTPC::hits","RTPC::tracks","RTPC::KFtracks"]
alert  = ["AHDC::Track", "AHDC::MC", "AHDC::Hits", "AHDC::PreClusters", "AHDC::Clusters", "AHDC::KFTrack", "AHDC_AI::Prediction"]
dets   = band + raster + rich + rtpc + alert

# additions for the calibration schema:
calib = ["BAND::adc","BAND::laser","BAND::tdc","BAND::hits","BAND::rawhits","CND::adc","CND::hits","CND::tdc","CTOF::adc","CTOF::hits","CTOF::tdc","CVTRec::Tracks","CVTRec::UTracks","ECAL::adc","ECAL::calib","ECAL::clusters","ECAL::peaks","ECAL::tdc","FMT::Hits","FMT::Clusters","FMT::Tracks","FMT::Trajectory","FT::particles","FTCAL::adc","FTCAL::clusters","FTCAL::hits","FTHODO::adc","FTHODO::clusters","FTHODO::hits","FTTRK::clusters","FTTRK::hits","FTTRK::crosses","FTOF::adc","FTOF::hits","FTOF::tdc","HTCC::adc","HTCC::rec","LTCC::adc","LTCC::clusters","RASTER::adc","RF::adc","RF::tdc","RICH::tdc","RICH::Hit","RICH::Particle","RICH::Hadron","RICH::Photon","RICH::Ring","RTPC::adc","RTPC::hits","RTPC::tracks","RUN::rf","RUN::trigger","TimeBasedTrkg::TBHits","TimeBasedTrkg::TBTracks"]

# additions for the monitoring schema:
mon = ["BMT::adc","BMTRec::Clusters","BMTRec::Crosses","BMTRec::Hits","BMTRec::LayerEffs","BST::adc","BSTRec::Clusters","BSTRec::Crosses","BSTRec::Hits","BSTRec::LayerEffs","CND::clusters","CVTRec::Trajectory","ECAL::hits","FMT::adc","FTTRK::adc","HEL::adc","HitBasedTrkg::HBTracks","RAW::vtp","TimeBasedTrkg::TBCrosses","TimeBasedTrkg::TBSegments","TimeBasedTrkg::TBSegmentTrajectory","TimeBasedTrkg::Trajectory"]

# trigger validation needs these:
trig = ["RAW::vtp","HTCC::rec","ECAL::adc","ECAL::calib","ECAL::clusters","ECAL::hits","ECAL::moments","ECAL::peaks","ECAL::tdc","ECAL::trigger"]

# accumulate all the DST banks:
dst = rectbai + rectb + mc + tag1 + dets
dsthb = dst + rechbai + rechb

# generate the calib and mon schema:
calib.extend(dst)
mon.extend(calib + rechbai + rechb)

trig.extend(dst)

# EB rerun schema is DSTs plus whatever is necessary to rerun EB:
ebrerun = list(dst)
ebrerun.extend(["FTOF::clusters","FTOF::hbclusters","TimeBasedTrkg::TBTracks","TimeBasedTrkg::Trajectory","TimeBasedTrkg::TBCovMat","HitBasedTrkg::HBTracks","HitBasedTrkg::Trajectory","ECAL::clusters","ECAL::calib","CTOF::clusters","CND::clusters","HTCC::rec","LTCC::clusters","ECAL::moments","CVTRec::Tracks","CVTRec::Trajectory","FT::particles","FTCAL::clusters","FTHODO::clusters","RUN::rf"])

# EC rerun schema adds ECAL raw banks to the EB rerun schema:
ecrerun = list(ebrerun)
ecrerun.extend(["ECAL::tdc","ECAL::adc"])

# DC alignment and AI-tracking validation schema:
dcalign = list(dst)
dcalign.extend(["ai::tracks", "aidn::tracks", "TimeBasedTrkg::AIClusters", "TimeBasedTrkg::AIHits", "TimeBasedTrkg::AISegments", "TimeBasedTrkg::AITracks", "TimeBasedTrkg::TBClusters", "TimeBasedTrkg::TBHits", "TimeBasedTrkg::TBSegments", "TimeBasedTrkg::TBSegmentTrajectory", "TimeBasedTrkg::TBTracks"])

# DC HV studies schema:
dchv = list(dsthb)
dchv.extend(["DC::tdc","DC::jitter", "HitBasedTrkg::HBClusters", "HitBasedTrkg::HBHitTrkId", "HitBasedTrkg::HBHits", "HitBasedTrkg::HBSegmentTrajectory", "HitBasedTrkg::HBSegments", "HitBasedTrkg::HBTracks", "HitBasedTrkg::Hits", "HitBasedTrkg::Trajectory", "TimeBasedTrkg::TBClusters", "TimeBasedTrkg::TBHits", "TimeBasedTrkg::TBSegments", "TimeBasedTrkg::TBSegmentTrajectory", "TimeBasedTrkg::TBTracks"])

# Level3 validation schema:
level3 = list(dst)
level3.extend(["DC::tdc", "ECAL::adc", "ECAL::clusters", "FTOF::tdc", "FTOF::adc", "HitBasedTrkg::HBClusters", "HitBasedTrkg::HBTracks", "HTCC::adc", "RF::adc", "RF::tdc", "RUN::rf", "TimeBasedTrkg::TBClusters", "TimeBasedTrkg::TBTracks"])

create("dst/", set(dst))
create("dsthb/", set(dsthb))
create("calib/", set(calib))
create("mon/",  set(mon))
create("ebrerun/", set(ebrerun))
create("ecrerun/", set(ecrerun))
create("dcalign/", set(dcalign))
create("dchv/", set(dchv))
create("level3/", set(level3))
create("trigger/", set(trig))

