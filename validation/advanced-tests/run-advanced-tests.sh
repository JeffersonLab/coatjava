#!/bin/bash -f

# coatjava and clara must already be built at ../../coatjava/
# and input data files at ./data

# set up environment
CLARA_HOME=$PWD/../../clara/ ; export CLARA_HOME
COAT=$CLARA_HOME/plugins/clas12/

# source coatjava environment
source $COAT/libexec/env.sh
classPath="${COATJAVA_CLASSPATH}:../lib/*:src/"

# run decoder
rm -f twoTrackEvents_809.hipo
$COAT/bin/decoder -t -0.5 -s 0.0 -i ./data/twoTrackEvents_809_raw.evio -o ./twoTrackEvents_809.hipo -c 2
[ $? -ne 0 ] && echo "decoder failure" && exit 3

# take a peek
$COAT/bin/hipo-utils -stats ./twoTrackEvents_809.hipo

# run clara
rm -f rec_twoTrackEvents_809.hipo
$COAT/bin/run-clara -y $COAT/etc/services/kpp.yaml ./twoTrackEvents_809.hipo
[ $? -ne 0 ] && echo "reconstruction with clara failure" && exit 4

# compile test codes
javac -cp $classPath src/kpptracking/KppTrackingTest.java 
[ $? -ne 0 ] && echo "KppTrackingTest compilation failure" && exit 5

# run KppTracking junit tests
java -DCLAS12DIR="$COAT" -Xmx1536m -Xms1024m -cp $classPath org.junit.runner.JUnitCore kpptracking.KppTrackingTest
[ $? -ne 0 ] && echo "KppTracking unit test failure" && exit 6

echo "KppTracking passed unit tests"
