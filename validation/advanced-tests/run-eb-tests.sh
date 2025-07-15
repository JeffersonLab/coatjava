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
source ../../coatjava/libexec/env.sh
classPath="${COATJAVA_CLASSPATH}:../lib/*:src/"

# make sure test code compiles before anything else:
javac -cp $classPath src/eb/EBTwoTrackTest.java
if [ $? != 0 ] ; then echo "EBTwoTrackTest compilation failure" ; exit 1 ; fi

# download and setup dependencies, run reconstruction:
if [ $runTestOnly -eq 0 ]
then
    wget -N --no-check-certificate $webDir/${stub}.hipo
    rm -f out_${stub}.hipo
    export GEOMDBVAR=default
    ../../coatjava/bin/recon-util -i ${stub}.hipo -o out_${stub}.hipo -c 2
fi

# run Event Builder tests:
java -Xmx1536m -Xms1024m -cp $classPath -DINPUTFILE=out_${stub}.hipo org.junit.runner.JUnitCore eb.EBTwoTrackTest
if [ $? != 0 ] ; then echo "EBTwoTrackTest unit test failure" ; exit 1 ; else echo "EBTwoTrackTest passed unit tests" ; fi

# show a pid effenciency matrix:
trutheff ./out_${stub}.hipo

exit 0

