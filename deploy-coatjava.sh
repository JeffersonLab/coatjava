#!/bin/bash

set -euo pipefail

# version number
src_dir=$(cd $(dirname ${BASH_SOURCE[0]:-$0}) && pwd -P)
ver_current=$($src_dir/libexec/version.sh)
ver_release=$(echo $ver_current | sed 's;-SNAPSHOT;;')

# printouts for this script (different from Maven printouts)
log() { echo ">>> $@"; }

# usage guide
usage() {
  echo """
  USAGE: $0 [OPTIONS]...

    -v VERSION   set the version number to deploy
                 default: $ver_release
                 NOTE: append '-SNAPSHOT' if you want to deploy
                       a timestamped snapshot version

    -h           show this usage guide
  """
}

# parse arguments
while getopts "v:h" opt; do
  case $opt in
    v)
      ver_release=$OPTARG
      ;;
    h)
      usage
      exit 2
      ;;
    *)
      exit 1
      ;;
  esac
done

log "========================"
log "CURRENT VERSION = $ver_current"
log "RELEASE VERSION = $ver_release"
log "========================"

# change the version number, if different
# NOTE: `maven-release-plugin` could be used to better automate the versioning
# here, but since this deployment is _only_ done in the `coat-lib` POM (the
# shaded JAR), and we also want to make a tarball with _all_ of the POMs at the
# correct version number, we may as well do the version bump here
if [ "$ver_current" != "$ver_release" ]; then
  log "change version number $ver_current -> $ver_release"
  $src_dir/version-bump.sh --no-git --no-snap $ver_release
fi

# rebuild coatjava, cleanly, to be sure we deploy the correct version
log "cleanly rebuild coatjava"
$src_dir/build-coatjava.sh --clean
$src_dir/build-coatjava.sh

# deploy
log "deploy coatjava version $ver_release"
mvn clean deploy -f $src_dir/common-tools/coat-lib/pom.xml

# revert the version number and rebuild coatjava
# NOTE: the standard approach is to increment the version number, but we typically
# do that just before a new release, using `version-bump.sh`, so we can choose
# which numbers to increment (major, minor, or patch) based on the recent changes;
# so instead here we just revert to the previous state
# FIXME: if anything failed between the `version-bump.sh` calls, your repository
# may have the "wrong" version number; you can use `version-bump.sh` to fix (or
# `git reset`); surely there is a smarter way to ensure no version number change
# no matter what
if [ "$ver_current" != "$ver_release" ]; then
  log "revert version number to $ver_current"
  $src_dir/version-bump.sh --no-git --no-snap $ver_current
  log "rebuild coatjava with the original version number"
  $src_dir/build-coatjava.sh
fi


#-------------------------------------------------------------------------------------------------
# Script is exporting existing Jar files to repository
#-------------------------------------------------------------------------------------------------

# cd `dirname $0`


# rm -rvf testDeployment
# mvn clean deploy
# tree target
# tree testDeployment
# exit
#
# repo=$src_dir/myLocalMvnRepo
#
# mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file \
#     -Dfile=target/coat-libs-${VERSION}-SNAPSHOT.jar \
#     -DgroupId=org.jlab.coat \
#     -DartifactId=coat-libs \
#     -Dversion=${VERSION}-SNAPSHOT \
#     -Dpackaging=jar \
#     -DlocalRepositoryPath=$repo
# exit
#
# scp -r $repo/org/jlab/coat/coat-libs/${VERSION}-SNAPSHOT \
#     clas12@jlabl1:/group/clas/www/clasweb/html/clas12maven/org/jlab/coat/coat-libs/.
#
#
# cd $repo/..
# tar -czvf coatjava-${VERSION}.tar.gz coatjava
# scp coatjava-${VERSION}.tar.gz \
#     clas12@jlabl1:/group/clas/www/clasweb/html/clas12offline/distribution/coatjava/.
#
