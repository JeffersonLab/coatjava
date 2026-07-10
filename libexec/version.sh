#!/usr/bin/env bash
# return the version number
set -euo pipefail
pom_file=$(cd $(dirname ${BASH_SOURCE[0]:-$0})/.. && pwd -P)/pom.xml
mvn -q help:evaluate -Dexpression=project.version -DforceStdout -f $pom_file
