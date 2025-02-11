#!/bin/bash

webDir=http://clasweb.jlab.org/clas12offline/distribution/coatjava/validation_files/eb
webVersion=5.11-fid-tm-r11
webDir=$webDir/$webVersion

# coatjava must already be built at ../../coatjava/

# whether to use CLARA (0=no)
useClara=0

# if non-zero, don't redownload dependencies, don't run reconstruction:
runTestOnly=0

# gemc default solenoid (changed in 4a.2.4):
gemcSolenoidDefault=-1.0
if [[ $webVersion = *"4a.2.2"* ]] || [[ $webVersion = *"4a.2.3"* ]]
then
    gemcSolenoidDefault=1.0
fi

# geometry variation for DC
geoDbVariation="default"
if [[ $webVersion = *"4a.2.2"* ]] || [[ $webVersion = *"4a.2.3"* ]] || [[ $webVersion = *"4a.2.4"* ]]
then
    geoDbVariation="dc_geo_gemc424"
fi

nEvents=-1

for arg in $@
do
    if [ "$arg" == "-t" ]
    then
        runTestOnly=1
    elif [[ $arg == "-100" ]]
    then
        webDir=${webDir}-100
    fi
done

# last argument is input file stub:
stub="${@: -1}"

# sanity check on filestub name,
# just to error with reasonable message before proceeding:
case $stub in
    # electron in forward, hadron in forward:
    electronproton)
        ;;
    electronkaon)
        ;;
    electronpion)
        ;;
    electrongamma)
        ;;
    electronneutron)
        ;;
    electronFTproton)
        ;;
    electronFTkaon)
        ;;
    electronFTpion)
        ;;
    electronFTgamma)
        ;;
    electrongammaFT)
        ;;
    electronprotonC)
        ;;
    electronkaonC)
        ;;
    electronpionC)
        ;;
    electrongammaC)
        ;;
    electronneutronC)
        ;;
    electrondeuteronC)
        ;;
    *)
      echo Invalid input evio file:  $stub
      exit 1
esac

# set up environment
if [ $useClara -eq 0 ]
then
    COAT=../../coatjava
    source $COAT/libexec/env.sh
else
    CLARA_HOME=$PWD/clara_installation/
    COAT=$CLARA_HOME/plugins/clas12/
    export CLARA_HOME
fi

classPath="$COAT/lib/services/*:$COAT/lib/clas/*:$COAT/lib/utils/*:../lib/*:src/"

classPath2="../../coatjava/lib/services/*:../../coatjava/lib/clas/*:../../coatjava/lib/utils/*:../lib/*:src/"

# make sure test code compiles before anything else:
javac -cp $classPath2 src/eb/EBTwoTrackTest.java
if [ $? != 0 ] ; then echo "EBTwoTrackTest compilation failure" ; exit 1 ; fi

# download and setup dependencies, run reconstruction:
if [ $runTestOnly -eq 0 ]
then

    if ! [ $useClara -eq 0 ]
    then
        # tar the local coatjava build so it can be installed with clara
        cd ../..
        tar -zcvf coatjava-local.tar.gz coatjava
        mv coatjava-local.tar.gz validation/advanced-tests/
        cd -

        # install clara
        if ! [ -d clara_installation ]
        then
            ../../install-clara clara_installation
        fi
    fi

    # download test files, if necessary:
    wget -N --no-check-certificate $webDir/${stub}.hipo
    if [ $? != 0 ] ; then echo "wget validation files failure" ; exit 1 ; fi

    # update the schema dictionary:  (no longer necessary now that recon-util does it)
    #rm -f up_${stub}.hipo
    #../../coatjava/bin/hipo-utils -update -d ../../coatjava/etc/bankdefs/hipo4/ -o up_${stub}.hipo ${stub}.hipo

    # run reconstruction:
    rm -f out_${stub}.hipo
    if [ $useClara -eq 0 ]
    then
        GEOMDBVAR=$geoDbVariation
        export GEOMDBVAR
        ../../coatjava/bin/recon-util -i ${stub}.hipo -o out_${stub}.hipo -c 2
    else
        echo "set inputDir $PWD/" > cook.clara
        echo "set outputDir $PWD/" >> cook.clara
        echo "set threads 7" >> cook.clara
        echo "set javaMemory 2" >> cook.clara
        echo "set session s_cook" >> cook.clara
        echo "set description d_cook" >> cook.clara
        ls ${stub}.hipo > files.list
        echo "set fileList $PWD/files.list" >> cook.clara
        echo "run local" >> cook.clara
        echo "exit" >> cook.clara
        $CLARA_HOME/bin/clara-shell cook.clara
    fi
fi

# run Event Builder tests:
java -DCLAS12DIR="$COAT" -Xmx1536m -Xms1024m -cp $classPath2 -DINPUTFILE=out_${stub}.hipo org.junit.runner.JUnitCore eb.EBTwoTrackTest
if [ $? != 0 ] ; then echo "EBTwoTrackTest unit test failure" ; exit 1 ; else echo "EBTwoTrackTest passed unit tests" ; fi

$COAT/bin/trutheff ./out_${stub}.hipo

exit 0

