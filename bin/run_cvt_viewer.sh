#!/usr/bin/env bash

set -euo pipefail

INPUT_FILE="$1"

if [ -z "$INPUT_FILE" ] ; then
  echo "Usage: ./run_bank_browser.sh <input.hipo>"
  echo "Example: ./run_bank_browser.sh data.hipo "
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT_DIR="$REPO_DIR/reconstruction/cvt"
POM_FILE="$PROJECT_DIR/pom.xml"

if [ ! -f "$POM_FILE" ]; then
  echo "ERROR: No pom.xml found at $POM_FILE"
  exit 2
fi

INPUT_ABS="$(cd "$(dirname "$INPUT_FILE")" && pwd)/$(basename "$INPUT_FILE")"

if [ ! -f "$INPUT_ABS" ]; then
  echo "ERROR: Input file not found: $INPUT_FILE"
  exit 3
fi

mvn -f "$POM_FILE" -q exec:java \
  -Dexec.mainClass="org.jlab.qcddat.CVTBrowser" \
  -Dexec.args="$INPUT_ABS"

