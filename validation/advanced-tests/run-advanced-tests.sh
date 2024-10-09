#!/bin/sh -f

# coatjava must already be built at ../../coatjava/

# set up environment
JAVA_OPTS="-Djava.util.logging.config.file=$PWD/../../etc/logging/debug.properties"
CLARA_HOME=$PWD/clara_installation/ ; export CLARA_HOME
COAT=$CLARA_HOME/plugins/clas12/
classPath="$COAT/lib/services/*:$COAT/lib/clas/*:$COAT/lib/utils/*:../lib/*:src/"

# install clara
../../install-clara -c ../../coatjava $CLARA_HOME
[ $? -ne 0 ] && echo "clara installation error" && exit 1

# download test files
wget --no-check-certificate http://clasweb.jlab.org/clas12offline/distribution/coatjava/validation_files/twoTrackEvents_809_raw.evio.tar.gz
[ $? -ne 0 ] && echo "wget validation files failure" && exit 2
tar -zxvf twoTrackEvents_809_raw.evio.tar.gz

# run decoder
$COAT/bin/decoder -t -0.5 -s 0.0 -i ./twoTrackEvents_809_raw.evio -o ./twoTrackEvents_809.hipo -c 2
[ $? -ne 0 ] && echo "decoder failure" && exit 3

# run clara
$COAT/bin/run-clara -y $COAT/etc/services/kpp.yaml *.hipo
[ $? -ne 0 ] && echo "reconstruction with clara failure" && exit 4

# compile test codes
javac -cp $classPath src/kpptracking/KppTrackingTest.java 
[ $? -ne 0 ] && echo "KppTrackingTest compilation failure" && exit 5

# run KppTracking junit tests
java -DCLAS12DIR="$COAT" -Xmx1536m -Xms1024m -cp $classPath org.junit.runner.JUnitCore kpptracking.KppTrackingTest
[ $? -ne 0 ] && echo "KppTracking unit test failure" && exit 6

echo "KppTracking passed unit tests"
