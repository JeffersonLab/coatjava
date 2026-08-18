#!/bin/bash

# WARNING:  coatjava must already be built at ../../coatjava/
# and input test data files at ./data

set -e
source ../../coatjava/libexec/env.sh
input_dir=./data/6.0-r11
classPath="${COATJAVA_CLASSPATH}:../lib/*:src/"

# check arguments:
for arg in $@
do
    if [[ $arg == "-100" ]]
    then
        input_dir=${input_dir}-100
    fi
done

# last argument is input file stub:
stub="${@: -1}"

# check (file)stub name:
case $stub in
    electronproton) ;;
    electronkaon) ;;
    electronpion) ;;
    electrongamma) ;;
    electronneutron) ;;
    electronFTproton) ;;
    electronFTkaon) ;;
    electronFTpion) ;;
    electronFTgamma) ;;
    electrongammaFT) ;;
    electronprotonC) ;;
    electronkaonC) ;;
    electronpionC) ;;
    electrongammaC) ;;
    electronneutronC) ;;
    electrondeuteronC) ;;
    *)
      echo Invalid input evio file:  $stub
      exit 1
esac

# compile test code:
javac -cp $classPath src/eb/EBTwoTrackTest.java
if [ $? != 0 ] ; then echo "EBTwoTrackTest compilation failure" ; exit 1 ; fi

# run reconstruction:
rm -f out_${stub}.hipo
../../coatjava/bin/recon-mutil -t 4 -l FINE -i ${input_dir}/${stub}.hipo -o out_${stub}.hipo -c 2

# run EB tests:
java -Xmx1536m -Xms1024m -cp $classPath -DINPUTFILE=out_${stub}.hipo eb.EBTwoTrackTest
if [ $? != 0 ] ; then echo "EBTwoTrackTest unit test failure" ; exit 1 ; else echo "EBTwoTrackTest passed unit tests" ; fi

# run truth-efficiency calculator:
../../coatjava/bin/trutheff ./out_${stub}.hipo
