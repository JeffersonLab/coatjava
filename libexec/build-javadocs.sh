#!/usr/bin/env bash
# analyze maven dependencies
set -euo pipefail
mvn javadoc:javadoc --no-transfer-progress
