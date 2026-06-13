#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="${AI_VALIDATION_MODULE:-$SCRIPT_DIR}"

if [[ ! -f "$MODULE_DIR/pom.xml" ]]; then
    echo "Cannot find clas-ai-validation pom.xml in: $MODULE_DIR" >&2
    exit 1
fi

export CLAS12DIR="${CLAS12DIR:-$(cd "$MODULE_DIR/../.." && pwd)}"

ARGS=""
if [[ $# -gt 1 ]]; then
    echo "Usage: run_histogram_viewer.sh [HISTOGRAM_FILE.hipo]" >&2
    exit 2
elif [[ $# -eq 1 ]]; then
    if [[ ! -f "$1" ]]; then
        echo "Histogram file does not exist: $1" >&2
        exit 1
    fi
    HISTOGRAM_FILE="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
    ARGS="$HISTOGRAM_FILE"
fi

cd "$MODULE_DIR"

mvn -q -DskipTests \
    compile \
    org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=org.jlab.clas.tracking.validation.analysis.AITrackingHistogramViewer \
    -Dexec.args="$ARGS"
