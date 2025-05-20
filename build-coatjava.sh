#!/bin/bash

set -e
set -u
set -o pipefail

usage='''build-coatjava.sh [OPTIONS]... [MAVEN_OPTIONS]...

  OPTIONS

   --clean           clean up built objects and exit (does not compile)

   --nomaps          do not download field maps

   --docs            also build the API documentation webpages
   --spotbugs        also run spotbugs plugin
   --unittests       also run unit tests

   --quiet           run more quietly
   --no-progress     no download progress printouts

   --help            show this message

  MAVEN_OPTIONS

   all other arguments will be passed to `mvn`, e.g., -T4 will build with 4 parallel threads
'''

cleanBuild="no"
runSpotBugs="no"
downloadMaps="yes"
runUnitTests="no"
buildDocs="no"
mvnArgs=()
wgetArgs=()
for xx in $@
do
  case $xx in
    --spotbugs)  runSpotBugs="yes"  ;;
    -n)          runSpotBugs="no"   ;;
    --nomaps)    downloadMaps="no"  ;;
    --unittests) runUnitTests="yes" ;;
    --docs)      buildDocs="yes"    ;;
    --clean)     cleanBuild="yes"   ;;
    --quiet)
      mvnArgs+=(--quiet --batch-mode)
      wgetArgs+=(--quiet)
      ;;
    --no-progress)
      mvnArgs+=(--no-transfer-progress)
      wgetArgs+=(--no-verbose)
      ;;
    -h|--help)
      echo "$usage"
      exit 2
      ;;
    *) mvnArgs+=($xx) ;;
  esac
done

src_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
prefix_dir=$src_dir/coatjava

# working directory should be the source code directory
cd $src_dir

# set arguments for `mvn` and `wget`
wgetArgs+=(--timestamping --no-check-certificate) # `--timestamping` only redownloads if timestamp/filesize is newer/different
mvn="mvn ${mvnArgs[@]:-}"
wget="wget ${wgetArgs[@]:-}"

command_exists () {
    type "$1" &> /dev/null
}
download () {
    ret=0
    if command_exists wget ; then
        $wget $1
        ret=$?
    elif command_exists curl ; then
        if ! [ -e ${1##*/} ]; then
          curl $1 -o ${1##*/}
          ret=$?
        fi
    else
        ret=1
        echo ERROR:::::::::::  Could not find wget nor curl.
    fi
    return $ret
}


# download the default field maps, as defined in libexec/env.sh:
# (and duplicated in etc/services/reconstruction.yaml):
source libexec/env.sh
if [ $downloadMaps == "yes" ]; then
  echo 'Retrieving field maps ...'
  webDir=https://clasweb.jlab.org/clas12offline/magfield
  locDir=etc/data/magfield
  mkdir -p $locDir
  cd $locDir
  for map in $COAT_MAGFIELD_SOLENOIDMAP $COAT_MAGFIELD_TORUSMAP $COAT_MAGFIELD_TORUSSECONDARYMAP
  do
    download $webDir/$map
    if [ $? -ne 0 ]; then
        echo ERROR:::::::::::  Could not download field map:
        echo $webDir/$map
        echo One option is to download manually into etc/data/magfield and then run this build script with --nomaps
        exit 1
    fi
  done
  cd -
fi

# always clean the installation prefix
rm -rf $prefix_dir

# clean up any cache copies
if [ $cleanBuild == "yes" ]; then
  $mvn clean
  echo '''DONE CLEANING.
  Now re-run without `--clean` to build.'''
  exit
fi

# start new installation tree
mkdir -p $prefix_dir
cp -r bin $prefix_dir/
cp -r etc $prefix_dir/
cp -r libexec $prefix_dir/

# create schema directories for partial reconstruction outputs
which python3 >& /dev/null && python=python3 || python=python
$python etc/bankdefs/util/bankSplit.py $prefix_dir/etc/bankdefs/hipo4 || exit 1
mkdir -p $prefix_dir/lib/clas
mkdir -p $prefix_dir/lib/utils
mkdir -p $prefix_dir/lib/services

# FIXME:  this is still needed by one of the tests
cp external-dependencies/jclara-4.3-SNAPSHOT.jar $prefix_dir/lib/utils

# spotbugs, unit tests
unset CLAS12DIR
if [ $runUnitTests == "yes" ]; then
  $mvn install # also runs unit tests
  if [ $? != 0 ] ; then echo "mvn install failure" ; exit 1 ; fi
else
  $mvn -Dmaven.test.skip=true install
  if [ $? != 0 ] ; then echo "mvn install failure" ; exit 1 ; fi
fi

if [ $runSpotBugs == "yes" ]; then
  # mvn com.github.spotbugs:spotbugs-maven-plugin:spotbugs # spotbugs goal produces a report target/spotbugsXml.xml for each module
  $mvn com.github.spotbugs:spotbugs-maven-plugin:check # check goal produces a report and produces build failed if bugs
  # the spotbugsXml.xml file is easiest read in a web browser
  # see http://spotbugs.readthedocs.io/en/latest/maven.html and https://spotbugs.github.io/spotbugs-maven-plugin/index.html for more info
  if [ $? != 0 ] ; then echo "spotbugs failure" ; exit 1 ; fi
fi

# documentation
if [ $buildDocs == "yes" ]; then
  $mvn javadoc:javadoc javadoc:aggregate -Ddoclint=none
fi

# installation
cp common-tools/coat-lib/target/coat-libs-*-SNAPSHOT.jar $prefix_dir/lib/clas/
cp reconstruction/*/target/clas12detector-*-SNAPSHOT*.jar $prefix_dir/lib/services/
echo "installed coatjava to: $prefix_dir"
if [ $buildDocs == "yes" ]; then
  doc_dir=$prefix_dir/share/doc/coatjava/html
  mkdir -p $doc_dir
  cp -r target/reports/apidocs/* $doc_dir/
  echo "installed documentation to: $doc_dir"
fi

# install clara
#rm -rf clara-home && ./install-clara -c ./coatjava ./clara-home

echo "COATJAVA SUCCESSFULLY BUILT !"
