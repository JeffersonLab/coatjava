## Trigger Roads Package

### Dictionaries
Roads are representations of a particle trajectory in the detector, consisting of the sequence of detector element IDs that are crossed by the particle. In the forward detector, they can include elements of HTCC, DC, FTOF, and ECAL. Charged-particle roads are used in the CLAS12 level-1 trigger to select events in which the detected hits are consistent with one or more tracks with hits in FTOF and PCAL, in a selected number of sectors. For this purpose, dictionaries of roads, i.e. list of roads covering the kinematics of interest, are used (see B. Raydo et al., "The CLAS12 Trigger System", Nucl. Inst. and Meth. A 960, 163529 (2020)).

This package contains the tools to generate, validate and manage the so-called roads for L1 trigger in CLAS12. Roads are stored in text files, aka ''dictionaries'', where each line orresponds to a road defined but the following list of quantities:
* particle charge, momentum, theta, and phi,
* list of 36 DC wires,
* FTOF panel 1B paddle,
* particle z vertex,
* FTOF panel 2 paddle,
* PCCAL U, V, and W strips,
* HTCC mask encoding sector, side, and ring of the PMTs,
* CLAS12 sector,
* ECAL cluster energies,


with the convention that detector components are set to 0 if the particle doesn't hit that layer.

### Tools
The package tools are:

- ```dict-generator```: generates roads in selected kinematics and for a chosen charge and torus solenoid field using a fastMC approach. For each road, the initial particle momentum and vertex are randomly generated and the particle is transported in the magnetic fields using the swimming package to determine the trajectory and the intersections with the relevant detector surfaces. These intersections, obtained using the geometry packages, are used to determine the corresponding detector element. The list of generated roads is saved to a text file, removing duplicates. 

```
     Usage : dict-generator -charge [particle charge] -n [number of roads] -solenoid [solenoid scale] -torus [torus scale]
     
   Options :
-duplicates : remove duplicates (1=on, 0=off) (default = 0)
   -phimax : maximum azimuthal angle in degrees (default = 30.0)
   -phimin : minimum azimuthal angle in degrees (default = -30.0)
     -pmax : maximum momentum in GeV (default = 11.0)
     -pmin : minimum momentum in GeV (default = 0.3)
     -seed : random seed (default = 10)
    -thmax : maximum polar angle in degrees (default = 40.0)
    -thmin : minimum polar angle in degrees (default = 5.0)
-variation : geometry database variation (default = default)
       -vr : raster radius in cm (default = 0.0)
    -vzmax : maximum vertex z coordinate in cm (default = 5.0)
    -vzmin : minimum vertex z coordinate in cm (default = -5.0)
```
Roads are stored in the output file with the format outlined above. the HTCC mask and ECAL energies are set to 0 since these cannot be predicted in the fast-MC approach.

- ```dict-maker```: generates roads from MC or real data reconstructed tracks. For each track satisfying the selection criteria set from the command line option, the list of detector elements hit by the track or road is extracted. The list of roads is saved to a text file, with the option of removing duplicates.
```
     Usage : dict-maker -i [event file] -o [dictionary file name]  [input1] [input2] ....

   Options :
   -charge : select particle charge for new dictionary, 0: no selection (default = 0)
    -dupli : remove duplicates in dictionary creation, 0=false, 1=true (default = 1)
        -n : maximum number of events to process for validation (default = -1)
      -pid : select particle PID for new dictionary, 0: no selection, (default = 0)
-threshold : select roads momentum threshold in GeV (default = 1)
    -vzmax : maximum vz (cm) (default = 10)
    -vzmin : minimum vz (cm) (default = -10)
```    
- ```dict-validator```: tests an existing road dictionary by evaluating the fraction of tracks in a reconstructed event file (either from GEMC or from real data) that have a match with one road in the dictionary. For each reconstructed particle matching the charge, pid, momentum threshold, and vertex range set via the command-line options the corresponding road is extracted and the presence of a matching road in the dictionary is verified. The matching criteria can be modified based on the selected mode and DC and PCAL binning and smearing options. Binning of N means that the DC wires or PCAL strips of the roads that are being compared are binned in groups of N before being numerically compared. Smearing of N means that the DC wires or PCAL strips of the particle road are smeared by +/- N before being compared with the dictionary roads. Typically, validation should be run using events from a data file of MC file representative of the reactions of interest. The data or MC file should have the banks listed [here](https://github.com/raffaelladevita/clas12-offline-software/blob/development/common-tools/clas-analysis/src/main/java/org/jlab/analysis/roads/Road.java#L90-L121).
```
     Usage : dict-validator -dict [dictionary file name] -i [event file for dictionary test]  [input1] [input2] ....

   Options :
   -charge : select particle charge for new dictionary, 0: no selection (default = 0)
     -mode : select test mode, available options are 0-DC 1-DCPCALU 2-DCFTOFPCALU  (default = 0)
        -n : maximum number of events to process for validation (default = -1)
      -pid : select particle PID for new dictionary, 0: no selection, (default = 0)
   -sector : sector dependent roads, 0=false, 1=true) (default = 0)
    -smear : smearing in wire/paddle/strip matching (default = 1)
    -strip : pcal strip bin size in road finding (default = 2)
-threshold : select roads momentum threshold in GeV (default = 1)
    -vzmax : maximum vz (cm) (default = 10)
    -vzmin : minimum vz (cm) (default = -10)
     -wire : dc wire bin size in road finding (default = 1)
```

### Generating and validating roads
Typically, real data would be used to generate roads if possible. Otherwise, the fastMC option is the preferred one since it is much faster than using GEANT4 simulations and the performance is similar. 
In the following, a summary of the procedure to generate with fastMC and test roads for electron trigger is summarized:
* run dict-generator for negative charge and the magnetic field and kinematics of interest for the experiment (momentum, angles, and vz selection). Based on past experience, generating 50 M roads is sufficient to obtain a good dictionary. This can quickly be achieved by launching farm jobs, each generating 1 M roads making sure to change the random generator seed from job to job. The output files can be merged into one dictionary using dict-merger or simply concatenating the jobs output files into one (in the first case duplicated roads will be removed in the merge, while in the second case, they won't).
* test the dictionary on an existing data or MC file by running dict-validator. The data or MC file should contain the event of interest. dict-validator should be run selecting electrons (-pid 11) and setting mode to 2 to test the roads based on DC, FTOF-1b and PCAL-U (this is more stringent than what is currently used in L1 trigger but is a conservative approach in case FTOF and PCAL will be used later on to improve the geometrical selection in the trigger).
* check the efficiency reported for negative tracks based on the integrated value reported on the prompt and in kinematic-dependent plots shown in the efficiency tab of the GUI: this should be greater than 99%. Lower values in specific kinematics usually indicate the kinematics selected in the dictionary generation is incomplete. An average low value (kinematic independent) may indicate the dictionary is incomplete and more statistics is needed.
  
