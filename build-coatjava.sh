#!/usr/bin/env bash
# build coatjava

set -e
set -u
set -o pipefail

################################################################################
# default options
################################################################################

cleanBuild=false
anaDepends=false
runSpotBugs=false
downloadMaps=true
downloadNets=true
runUnitTests=false
dataRetrieval=lfs
installClara=false
downloadData=false

################################################################################
# usage
################################################################################

usage='''build-coatjava.sh [OPTIONS]...

GENERAL OPTIONS
    --clara           install clara too
    --clean           clean up built objects and exit (does not compile)
    --quiet           run more quietly
    --no-progress     no download progress printouts
    --help            show this message

DATA RETRIEVAL OPTIONS
  How to retrieve magnetic field maps, neural network models, etc.
  Choose only one; default is `--'$dataRetrieval'`
    --lfs             use Git Large File Storage (requires `git-lfs`)
    --cvmfs           use CernVM-FS (requires `/cvfms`)
    --clasweb         use clasweb (only works for field maps)
  Options to disable data retrieval:
    --nomaps          do not download/overwrite field maps
    --nonets          do not download/overwrite neural networks

TESTING OPTIONS
    --spotbugs        also run spotbugs plugin
    --unittests       also run unit tests
    --depana          run dependency analysis (only)
    --data            download test data (requires option `--lfs`)

MAVEN OPTIONS
  all other arguments will be passed to `mvn`; for example,
  -T4 will build with 4 parallel threads
'''


################################################################################
# parse arguments
################################################################################

mvnArgs=()
wgetArgs=()
for xx in $@
do
  case $xx in
    --spotbugs)  runSpotBugs=true   ;;
    -n)          runSpotBugs=false  ;;
    --nomaps)    downloadMaps=false ;;
    --nonets)    downloadNets=false ;;
    --unittests) runUnitTests=true  ;;
    --clean)     cleanBuild=true    ;;
    --depana)    anaDepends=true    ;;
    --quiet)
      mvnArgs+=(--quiet --batch-mode)
      wgetArgs+=(--quiet)
      ;;
    --no-progress)
      mvnArgs+=(--no-transfer-progress)
      wgetArgs+=(--no-verbose)
      ;;
    --cvmfs)   dataRetrieval=cvmfs   ;;
    --lfs)     dataRetrieval=lfs     ;;
    --clasweb) dataRetrieval=clasweb ;;
    --clara)   installClara=true     ;;
    --data)    downloadData=true     ;;
    -h|--help)
      echo "$usage"
      exit 2
      ;;
    *) mvnArgs+=($xx) ;;
  esac
done


################################################################################
# setup
################################################################################

# directories
src_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
prefix_dir=$src_dir/coatjava
clara_home=$src_dir/clara
magfield_dir=$src_dir/etc/data/magfield

# working directory should be the source code directory
cd $src_dir

# set arguments for `mvn` and `wget`
wgetArgs+=(--timestamping --no-check-certificate) # `--timestamping` only redownloads if timestamp/filesize is newer/different
mvn="mvn ${mvnArgs[@]:-}"
wget="wget ${wgetArgs[@]:-}"

# environment
source libexec/env.sh --no-classpath

################################################################################
# cleaning, dependency analysis, etc.
################################################################################

# function to clean installation prefixes
clean_prefixes() {
  rm -rf $prefix_dir $clara_home
}

# clean up any cache copies
if $cleanBuild; then
  clean_prefixes
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

# run dependency analysis and exit
if $anaDepends; then
  libexec/dependency-analysis.sh
  libexec/dependency-tree.sh
  exit 0
fi


################################################################################
# download field maps, NN models, etc.
################################################################################

# check if a command exists
command_exists () {
  type "$1" &> /dev/null
}

# print retrieval notice
notify_retrieval() {
  echo "Retrieving $1 from $2 ..."
}

# update an LFS submodule
download_lfs() {
  cd $src_dir > /dev/null
  git submodule update --init $1
  cd - > /dev/null
}

# download a magnetic field map
download_map () {
  ret=0
  case $dataRetrieval in
    cvmfs)
      notify_retrieval 'field map' 'cvmfs'
      cp $1 ./
      ret=$?
      ;;
    clasweb)
      if command_exists wget ; then
        notify_retrieval 'field map' 'clasweb via wget'
        $wget $1
        ret=$?
      elif command_exists curl ; then
        notify_retrieval 'field map' 'clasweb via curl'
        if ! [ -e ${1##*/} ]; then
          curl $1 -o ${1##*/}
          ret=$?
        fi
      else
        ret=1
        echo "ERROR:::::::::::  Could not find wget nor curl." >&2
      fi
      ;;
    *)
      ret=1
      echo "ERROR:::::::::::  called 'download_map' with bad 'dataRetrieval'." >&2
      ;;
  esac
  return $ret
}

# check data-retrieval options, and prepare accordingly
case $dataRetrieval in
  lfs)
    if ! command_exists git-lfs ; then
      echo 'ERROR: `git-lfs` not found; please install it, or use a different data-retrieval option other than `--lfs`' >&2
      exit 1
    fi
    git lfs install
    ;;
  cvmfs)
    path=/cvmfs/oasis.opensciencegrid.org/jlab
    if [ ! -d $path ]; then
      echo "ERROR: cannot find CVMFS path '$path'; data retrieval option \`--cvmfs\` failed" >&2
      exit 1
    fi
    ;;
  clasweb)
    ;;
  *)
    echo "ERROR: data retrieval option '$dataRetrieval' is not supported" >&2
    exit 1
    ;;
esac

# download the default field maps, as defined in libexec/env.sh:
# (and duplicated in etc/services/reconstruction.yaml):
if $downloadMaps; then
  case $dataRetrieval
    lfs)
      notify_retrieval 'field maps' 'lfs'
      download_lfs etc/data/magfield
      ;;
    cvmfs|clasweb)
      webDir=https://clasweb.jlab.org/clas12offline/magfield
      if [ "$dataRetrieval" = "cvmfs" ]; then
        webDir=/cvmfs/oasis.opensciencegrid.org/jlab/hallb/clas12/sw/noarch/data/magfield
      fi
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
      ;;
    *)
      echo "ERROR: data retrieval option '$dataRetrieval' not supported for field maps" >&2
      exit 1
      ;;
  esac
fi

# download neural networks
if $downloadNets; then
  case $dataRetrieval in
    lfs)
      notify_retrieval 'neural networks' 'lfs'
      download_lfs etc/data/nnet
      ;;
    cvmfs)
      notify_retrieval 'neural networks' 'cvmfs'
      cp -r /cvmfs/oasis.opensciencegrid.org/jlab/hallb/clas12/sw/noarch/data/networks/* etc/data/nnet/
      ;;
    *)
      echo 'WARNING: neural networks not downloaded; run with `--help` for guidance' >&2
      sleep 1
      ;;
  esac
fi

# download validation data
if $downloadData; then
  case $dataRetrieval in
    lfs)
      notify_retrieval 'validation data' 'lfs'
      download_lfs validation/advanced-tests/data
      ;;
    *)
      echo 'ERROR: option `--lfs` must be used when using `--data`' >&2
      exit 1
      ;;
  esac
fi


################################################################################
# build
################################################################################

# start new installation tree
clean_prefixes # always clean the installation prefix
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

# build (and test)
unset CLAS12DIR
if $runUnitTests; then
  $mvn install # also runs unit tests
else
  $mvn install -DskipTests
fi

# run spotbugs
if $runSpotBugs; then
  libexec/spotbugs.sh ${mvnArgs[@]:-} || (echo "ERROR: spotbugs failure" >&2 && exit 1)
  echo "spotbugs spotted no bugs!"
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

# install clara
if $installClara; then ./install-clara -c $prefix_dir $clara_home; fi

echo "COATJAVA SUCCESSFULLY BUILT !"
