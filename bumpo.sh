#!/usr/bin/env bash
set -euo pipefail

[ $# -ne 1 ] && echo "USAGE: $0 [NEW_VERSION_NUMBER]" && exit 2

ver_num=$(echo $1|sed 's/-SNAPSHOT//g')
ver_pom=$ver_num-SNAPSHOT

mvn --batch-mode release:update-versions -DdevelopmentVersion=$ver_pom
#FIXME: can `./pom.xml` and `common-tools/coat-lib/pom.xml` be combined? see also `build-coatjava.sh`
cd common-tools/coat-lib
mvn --batch-mode release:update-versions -DdevelopmentVersion=$ver_pom
cd -

sed -i "s/^VERSION=.*/VERSION=$ver_num/g" common-tools/coat-lib/deployDistribution.sh
