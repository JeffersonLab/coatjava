#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage:
  run_analysis.sh INPUT.hipo [OUTPUT.hipo] [MAX_EVENTS] [MIN_TRUTH_HITS]

Arguments:
  INPUT.hipo       Reconstructed HIPO input file.
  OUTPUT.hipo      Histogram output. Default:
                   <input-base>_ai_validation_histos.hipo beside the input.
  MAX_EVENTS       Maximum events to process; <= 0 means all. Default: -1.
  MIN_TRUTH_HITS   Minimum truth-associated DC hits for particle denominators.
                   Default: 5.
USAGE
}

if [[ $# -lt 1 || $# -gt 4 ]]; then
    usage >&2
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="${AI_VALIDATION_MODULE:-$SCRIPT_DIR}"

if [[ ! -f "$MODULE_DIR/pom.xml" ]]; then
    echo "Cannot find clas-ai-validation pom.xml in: $MODULE_DIR" >&2
    exit 1
fi

export CLAS12DIR="${CLAS12DIR:-$(cd "$MODULE_DIR/.." && pwd)}"

INPUT_FILE="$1"
if [[ ! -f "$INPUT_FILE" ]]; then
    echo "Input HIPO file does not exist: $INPUT_FILE" >&2
    exit 1
fi
INPUT_FILE="$(cd "$(dirname "$INPUT_FILE")" && pwd)/$(basename "$INPUT_FILE")"

if [[ $# -ge 2 && -n "$2" ]]; then
    OUTPUT_DIR="$(dirname "$2")"
    mkdir -p "$OUTPUT_DIR"
    OUTPUT_FILE="$(cd "$OUTPUT_DIR" && pwd)/$(basename "$2")"
else
    INPUT_DIR="$(dirname "$INPUT_FILE")"
    INPUT_NAME="$(basename "$INPUT_FILE")"
    INPUT_BASE="${INPUT_NAME%.[Hh][Ii][Pp][Oo]}"
    OUTPUT_FILE="$INPUT_DIR/${INPUT_BASE}_ai_validation_histos.hipo"
fi

MAX_EVENTS="${3:--1}"
MIN_TRUTH_HITS="${4:-5}"

cd "$MODULE_DIR"

echo "CLAS12DIR:       $CLAS12DIR"
echo "Module:          $MODULE_DIR"
echo "Input:           $INPUT_FILE"
echo "Output:          $OUTPUT_FILE"
echo "Maximum events:  $MAX_EVENTS"
echo "Minimum hits:    $MIN_TRUTH_HITS"

mvn -q -DskipTests \
    compile \
    org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=org.jlab.clas.tracking.validation.analysis.AITrackingPerformanceAnalysis \
    -Dexec.args="$INPUT_FILE $OUTPUT_FILE $MAX_EVENTS $MIN_TRUTH_HITS"
