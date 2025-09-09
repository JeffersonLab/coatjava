#!/usr/bin/env bash
# print the maven dependency tree
set -euo pipefail
mvn dependency:tree -Ddetail=true --no-transfer-progress
