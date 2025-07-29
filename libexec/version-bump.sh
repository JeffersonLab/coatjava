#!/usr/bin/env bash
set -euo pipefail
[ $# -lt 1 ] && echo "ERROR: provide a new version number" >&2 && exit 1
ver=$1
echo ">>> bumping version number to: $ver"
pom_file=$(cd $(dirname ${BASH_SOURCE[0]:-$0})/.. && pwd -P)/pom.xml
mvn versions:set -DnewVersion=$ver -DprocessAllModules=true -f $pom_file
mvn versions:commit -DprocessAllModules=true -f $pom_file
echo ">>> bumped version number to: $ver"
