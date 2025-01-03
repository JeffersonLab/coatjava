#!/bin/bash
# WARNING:  coatjava must already be built at ../../coatjava/

set -e

webDir=http://clasweb.jlab.org/clas12offline/distribution/coatjava/validation_files/eb
webVersion=dev-fid-r11
webDir=$webDir/$webVersion
runTestOnly=0

# check command-line options:
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

# check stub validity:
! grep "^$stub -pid" src/eb/scripts/list.txt && echo Invalid stub:  $stub && exit 1

# set up environment
coatjava=../../coatjava
source $coatjava/libexec/env.sh
classPath="$coatjava/lib/services/*:$coatjava/lib/clas/*:$coatjava/lib/utils/*:../lib/*:src/"

# make sure test code compiles first:
javac -cp $classPath src/eb/EBTwoTrackTest.java

# download and setup dependencies, run reconstruction:
if [ $runTestOnly -eq 0 ]
then
    wget -N --no-check-certificate $webDir/${stub}.hipo
    rm -f out_${stub}.hipo
    export GEOMDBVAR=default
    ../../coatjava/bin/recon-util -i ${stub}.hipo -o out_${stub}.hipo -c 2
fi

# run tests:
$coatjava/bin/trutheff ./out_${stub}.hipo
java -DCLAS12DIR="$coatjava" -Xmx1536m -Xms1024m -cp $classPath -DINPUTFILE=out_${stub}.hipo org.junit.runner.JUnitCore eb.EBTwoTrackTest

