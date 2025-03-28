#!/usr/bin/env bash
set -euo pipefail

[ $# -ne 1 ] && echo "USAGE: $0 [NEW_VERSION_NUMBER]" && exit 2

ver_num=$(echo $1|sed 's/-SNAPSHOT//g')
ver_pom=$ver_num-SNAPSHOT

mvn --batch-mode release:update-versions -DdevelopmentVersion=$ver_pom

sed -i "s/^VERSION=.*/VERSION=$ver_num/g" common-tools/coat-lib/deployDistribution.sh
