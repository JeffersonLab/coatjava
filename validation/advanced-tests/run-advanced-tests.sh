#!/bin/bash -f

# coatjava and clara must already be built at ../../coatjava/
# and input data files at ./data

# set up environment
source $PWD/../../coatjava/libexec/env.sh
classPath="${COATJAVA_CLASSPATH}:../lib/*:src/"

# run reconstruction
recon-mutil -f -0.5,0 -t 4 -y $CLAS12DIR/etc/services/kpp.yaml -o rec_twoTrackEvents_809.hipo data/twoTrackEvents_809_raw.evio
[ $? -ne 0 ] && echo "recon-mutil failure" && exit 1

# take a peek
hipo-utils -stats ./rec_twoTrackEvents_809.hipo
[ $? -ne 0 ] && echo "recon-utils failure" && exit 2

# compile test codes
javac -cp $classPath src/kpptracking/KppTrackingTest.java 
[ $? -ne 0 ] && echo "KppTrackingTest compilation failure" && exit 3

# run KppTracking junit tests
java -Xmx1536m -Xms1024m -cp $classPath org.junit.runner.JUnitCore kpptracking.KppTrackingTest
[ $? -ne 0 ] && echo "KppTracking unit test failure" && exit 4

echo "KppTracking passed unit tests"
