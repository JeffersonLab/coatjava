#!/usr/bin/env bash
# build coatjava

set -e
set -u
set -o pipefail

usage='''build-coatjava.sh [OPTIONS]... [MAVEN_OPTIONS]...

  OPTIONS
   --clara           install clara too
   --clean           clean up built objects and exit (does not compile)
   --quiet           run more quietly
   --no-progress     no download progress printouts
   --help            show this message

  OPTIONS FOR MAGNETIC FIELD MAPS, NEURAL NETWORK MODELS, etc.
   --lfs             use Git Large File Storage (requires `git-lfs`)
   --cvmfs           use CernVM-FS (requires `/cvfms`)
   --xrootd          use XRootD (requires `xrootd`)
   --nomaps          do not download field maps

  OPTIONS FOR TESTING
   --spotbugs        also run spotbugs plugin
   --unittests       also run unit tests
   --depana          run dependency analysis (only)
   --data            download test data (requires lfs)

  MAVEN_OPTIONS
   all other arguments will be passed to `mvn`; for example,
   -T4 will build with 4 parallel threads
'''

################################################################################
# parse arguments
################################################################################

cleanBuild="no"
anaDepends="no"
runSpotBugs="no"
downloadMaps="yes"
runUnitTests="no"
useXrootd=false
useCvmfs=false
useLfs=false
installClara=false
downloadData=false
mvnArgs=()
wgetArgs=()
for xx in $@
do
  case $xx in
    --spotbugs)  runSpotBugs="yes"  ;;
    -n)          runSpotBugs="no"   ;;
    --nomaps)    downloadMaps="no"  ;;
    --unittests) runUnitTests="yes" ;;
    --clean)     cleanBuild="yes"   ;;
    --depana)    anaDepends="yes"   ;;
    --quiet)
      mvnArgs+=(--quiet --batch-mode)
      wgetArgs+=(--quiet)
      ;;
    --no-progress)
      mvnArgs+=(--no-transfer-progress)
      wgetArgs+=(--no-verbose)
      ;;
    --xrootd) useXrootd=true ;;
    --cvmfs)  useCvmfs=true ;;
    --lfs)    useLfs=true ;;
    --clara)  installClara=true ;;
    --data)   downloadData=true ;;
    -h|--help)
      echo "$usage"
      exit 2
      ;;
    *) mvnArgs+=($xx) ;;
  esac
done

if $downloadData && ! $useLfs; then
    echo "$usage"
    echo "ERROR:::::::::::  --data requires --lfs" >&2
    exit 2
fi

# Currently only git-lfs works from offsite:
if ! [[ $(hostname) == *.jlab.org ]]; then
    echo "INFO:  using --lfs for offsite usage"
    useLfs=true
fi

################################################################################
# setup
################################################################################

src_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
prefix_dir=$src_dir/coatjava
clara_home=$src_dir/clara

# working directory should be the source code directory
cd $src_dir

# set arguments for `mvn` and `wget`
wgetArgs+=(--timestamping --no-check-certificate) # `--timestamping` only redownloads if timestamp/filesize is newer/different
mvn="mvn ${mvnArgs[@]:-}"
wget="wget ${wgetArgs[@]:-}"

################################################################################
# download field maps, NN models, etc.
################################################################################

command_exists () {
    type "$1" &> /dev/null
}

download_lfs() {
  if command_exists git-lfs ; then
    cd $src_dir > /dev/null
    git lfs install
    git submodule update --init $1
    cd - > /dev/null
  else
    echo 'ERROR: `git-lfs` not found; please install it, or use a different option other than `--lfs`' >&2
    exit 1
  fi
}

download_map () {
    ret=0
    if $useXrootd; then
        xrdcp $1 ./
        ret=$?
    elif $useCvmfs; then
        cp -v $1 ./
        ret=$?
    elif command_exists wget ; then
        $wget $1
        ret=$?
    elif command_exists curl ; then
        if ! [ -e ${1##*/} ]; then
          curl $1 -o ${1##*/}
          ret=$?
        fi
    else
        ret=1
        echo "ERROR:::::::::::  Could not find wget nor curl." >&2
    fi
    return $ret
}

# download the default field maps, as defined in libexec/env.sh:
# (and duplicated in etc/services/reconstruction.yaml):
source libexec/env.sh --no-classpath
magfield_dir=$src_dir/etc/data/magfield
if [ $cleanBuild == "no" ] && [ $downloadMaps == "yes" ]; then
  echo 'Retrieving field maps ...'
  if $useLfs; then
    download_lfs etc/data/magfield
  else
    webDir=https://clasweb.jlab.org/clas12offline/magfield
    if $useXrootd; then webDir=xroot://sci-xrootd.jlab.org//osgpool/hallb/clas12/coatjava/magfield; fi
    if $useCvmfs; then webDir=/cvmfs/oasis.opensciencegrid.org/jlab/hallb/clas12/sw/noarch/data/magfield; fi
    mkdir -p $magfield_dir
    cd $magfield_dir
    for map in $COAT_MAGFIELD_SOLENOIDMAP $COAT_MAGFIELD_TORUSMAP $COAT_MAGFIELD_TORUSSECONDARYMAP
    do
      download_map $webDir/$map
      if [ $? -ne 0 ]; then
          echo "ERROR:::::::::::  Could not download field map:" >&2
          echo "$webDir/$map" >&2
          echo "One option is to download manually into etc/data/magfield and then run this build script with --nomaps" >&2
          exit 1
      fi
    done
    cd -
  fi
fi

# download neural networks
if $useLfs; then
  download_lfs etc/data/nnet
elif $useCvmfs; then
  cp -rv /cvmfs/oasis.opensciencegrid.org/jlab/hallb/clas12/sw/noarch/data/networks/* etc/data/nnet/
else
  echo "WARNING: neural networks not downloaded" >&2
fi

# download validation data
if $downloadData; then
  download_lfs validation/advanced-tests/data
fi

################################################################################
# cleaning
################################################################################

# always clean the installation prefix
rm -rf $prefix_dir $clara_home

# clean up any cache copies
if [ $cleanBuild == "yes" ]; then
  $mvn clean
  for target_dir in $(find $src_dir -type d -name target); do
    echo "WARNING: target directory '$target_dir' was not removed! JAR files within may be accidentally installed!" >&2
  done
  echo """DONE CLEANING.
  NOTE:
    - to remove local magnetic field maps:
        rm $magfield_dir/*.dat
    - to clear all LFS git submodules:
        git submodule deinit --all

  Now re-run without \`--clean\` to build."""
  exit
fi

################################################################################
# build
################################################################################

# run dependency analysis and exit
if [ $anaDepends == "yes" ]; then
  libexec/dependency-analysis.sh
  libexec/dependency-tree.sh
  exit 0
fi

# start new installation tree
mkdir -p $prefix_dir
cp -r bin $prefix_dir/
cp -r etc $prefix_dir/
cp -r libexec $prefix_dir/

# create schema directories for partial reconstruction outputs
which python3 >& /dev/null && python=python3 || python=python
$python etc/bankdefs/util/bankSplit.py $prefix_dir/etc/bankdefs/hipo4 || exit 1

# FIXME:  this is still needed by one of the tests
mkdir -p $prefix_dir/lib/utils
cp external-dependencies/jclara-4.3-SNAPSHOT.jar $prefix_dir/lib/utils

# spotbugs, unit tests
unset CLAS12DIR
if [ $runUnitTests == "yes" ]; then
  $mvn install # also runs unit tests
else
  $mvn install -DskipTests
fi

if [ $runSpotBugs == "yes" ]; then
  # mvn com.github.spotbugs:spotbugs-maven-plugin:spotbugs # spotbugs goal produces a report target/spotbugsXml.xml for each module
  $mvn com.github.spotbugs:spotbugs-maven-plugin:check # check goal produces a report and produces build failed if bugs
  # the spotbugsXml.xml file is easiest read in a web browser
  # see http://spotbugs.readthedocs.io/en/latest/maven.html and https://spotbugs.github.io/spotbugs-maven-plugin/index.html for more info
  if [ $? != 0 ] ; then echo "spotbugs failure" >&2 ; exit 1 ; fi
fi

################################################################################
# install
################################################################################

# NOTE: a maven plugin, such as `maven-assembly-plugin`, would be better, but it seems that they:
# - require significantly more repetition of the module names and/or generation of additional XML file(s)
# - seem to break thread safety of `mvn install`, i.e., we'd need to run `mvn package` first, then `mvn install`
# - we just want copy the produced JAR files to a final installation directory, so the following bash code gets the job done without drama
install_jars() {
  src=$(dirname $1)
  dest=$2
  [ $# -ge 3 ] && filter="$3" || filter='*.jar'
  if [ -d $src/target ]; then
    for f in $(find $src/target -name $filter); do
      mkdir -p $dest
      cp $f $dest/
    done
  fi
}
for pom in $(find reconstruction -name pom.xml); do
  install_jars $pom $prefix_dir/lib/services
done
for pom in $(find common-tools -name pom.xml); do
  if [[ "$pom" =~ coat-libs ]]; then
    install_jars $pom $prefix_dir/lib/clas 'coat-libs-*.jar'
  # else # FIXME, consumers may be need these after https://github.com/JeffersonLab/coatjava/pull/632 ; alternatively add needed deps to `coat-libs`
  #   install_jars $pom $prefix_dir/lib/services
  fi
done
echo "installed coatjava to: $prefix_dir"

if $installClara; then ./install-clara -c $prefix_dir $clara_home; fi

echo "COATJAVA SUCCESSFULLY BUILT !"
