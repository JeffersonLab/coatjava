#!/bin/sh -f

# coatjava must already be built at ../../coatjava/

# set up environment
CLARA_HOME=$PWD/clara_installation/ ; export CLARA_HOME
COAT=$CLARA_HOME/plugins/clas12/
classPath="$COAT/lib/services/*:$COAT/lib/clas/*:$COAT/lib/utils/*:../lib/*:src/"

# install clara
../../install-clara -c ../../coatjava $CLARA_HOME
if [ $? != 0 ] ; then echo "clara installation error" ; exit 1 ; fi

# download test files
wget --no-check-certificate http://clasweb.jlab.org/clas12offline/distribution/coatjava/validation_files/twoTrackEvents_809_raw.evio.tar.gz

if [ $? != 0 ] ; then echo "wget validation files failure" ; exit 1 ; fi
tar -zxvf twoTrackEvents_809_raw.evio.tar.gz

export JAVA_OPTS="-Djava.util.logging.config.file=$PWD/../../etc/logging/debug.properties"

# run decoder
$COAT/bin/decoder -t -0.5 -s 0.0 -i ./twoTrackEvents_809_raw.evio -o ./twoTrackEvents_809.hipo -c 2

# run clara
$COAT/bin/run-clara $COAT/etc/services/kpp.yaml  || echo "reconstruction with clara failure" && exit 1

# compile test codes
javac -cp $classPath src/kpptracking/KppTrackingTest.java 
if [ $? != 0 ] ; then echo "KppTrackingTest compilation failure" ; exit 1 ; fi

# run KppTracking junit tests
java -DCLAS12DIR="$COAT" -Xmx1536m -Xms1024m -cp $classPath org.junit.runner.JUnitCore kpptracking.KppTrackingTest
if [ $? != 0 ] ; then echo "KppTracking unit test failure" ; exit 1 ; else echo "KppTracking passed unit tests" ; fi
