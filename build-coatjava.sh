#!/usr/bin/env bash
# build coatjava

set -e
set -u
set -o pipefail

usage='''build-coatjava.sh [OPTIONS]...

GENERAL OPTIONS
    --clara           install clara too
    --clean           clean up built objects and exit (does not compile)
    --quiet           run more quietly
    --no-progress     no download progress printouts
    --help            show this message

DATA RETRIEVAL OPTIONS
  How to retrieve magnetic field maps, neural network models, etc.;
  choose only one, e.g., if the automated default choice fails:
    --lfs             use Git Large File Storage (requires `git-lfs`)
    --cvmfs           use CernVM-FS (requires `/cvfms`)
    --xrootd          use XRootD (requires `xrootd`)
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

cleanBuild=false
anaDepends=false
runSpotBugs=false
downloadMaps=true
downloadNets=true
runUnitTests=false
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
    --xrootd) useXrootd=true    ;;
    --cvmfs)  useCvmfs=true     ;;
    --lfs)    useLfs=true       ;;
    --clara)  installClara=true ;;
    --data)   downloadData=true ;;
    -h|--help)
      echo "$usage"
      exit 2
      ;;
    *) mvnArgs+=($xx) ;;
  esac
done

# check if a command exists
command_exists () {
  type "$1" &> /dev/null
}

# count how many data-retrieval options are set
count_download_opts() {
  local n=0
  for o in $useLfs $useCvmfs $useXrootd; do
    $o && ((n++))
  done
  echo $n
}

# if the user did not choose a data retrieval method, choose a reasonable one
if [[ $(count_download_opts) -eq 0 ]]; then
  echo 'INFO: no data-retrieval option set; choosing a default...'
  if ! [[ $(hostname) == *.jlab.org ]] && command_exists git-lfs ; then
      echo 'INFO: ... using `--lfs` since you are likely offsite and have git-lfs installed'
      useLfs=true
  elif [ -d /cvmfs/oasis.opensciencegrid.org/jlab ]; then
    echo 'INFO: ... using `--cvmfs` since you appear to have /cvmfs/oasis.opensciencegrid.org'
    useCvmfs=true
  else
    echo 'WARNING: default data-retrieval option cannot be determined; use `--help` for guidance' >&2
    sleep 1
  fi
fi

# if they chose too many, fail
if [[ $(count_download_opts) -gt 1 ]]; then
  echo 'ERROR: more than one data-retrieval option is set' >&2
  exit 1
fi


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

# install LFS
if $useLfs; then
  if ! command_exists git-lfs ; then
      echo 'ERROR: `git-lfs` not found; please install it, or use a different option other than `--lfs`' >&2
      exit 1
  fi
  git lfs install
fi

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

# print retrieval notice
notify_retrieval() {
  echo "Retrieving $1 from $2 ..."
}

# update an LFS submodule
download_lfs() {
  if ! $useLfs; then
    echo 'ERROR: attempted to use LFS, but option `--lfs` not set' >&2
    exit 1
  fi
  cd $src_dir > /dev/null
  git submodule update --init $1
  cd - > /dev/null
}

# download a magnetic field map
download_map () {
    ret=0
    if $useXrootd; then
        notify_retrieval 'field map' 'xrootd'
        xrdcp $1 ./
        ret=$?
    elif $useCvmfs; then
        notify_retrieval 'field map' 'cvmfs'
        cp $1 ./
        ret=$?
    elif command_exists wget ; then
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
    return $ret
}

# download the default field maps, as defined in libexec/env.sh:
# (and duplicated in etc/services/reconstruction.yaml):
if $downloadMaps; then
  if $useLfs; then
    notify_retrieval 'field maps' 'lfs'
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
if $downloadNets; then
  if $useLfs; then
    notify_retrieval 'neural networks' 'lfs'
    download_lfs etc/data/nnet
  elif $useCvmfs; then
    notify_retrieval 'neural networks' 'cvmfs'
    cp -r /cvmfs/oasis.opensciencegrid.org/jlab/hallb/clas12/sw/noarch/data/networks/* etc/data/nnet/
  else
    echo 'WARNING: neural networks not downloaded; run with `--help` for guidance' >&2
    sleep 1
  fi
fi

# download validation data
if $downloadData; then
  notify_retrieval 'validation data' 'lfs'
  download_lfs validation/advanced-tests/data
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
