#!/usr/bin/env bash
# analyze maven dependencies
# NOTE: skips `coat-libs`, since shaded JAR dependencies are "unused" according to `dependency:analyze`
set -euo pipefail
mvn dependency:analyze -DfailOnWarning=true -pl '!org.jlab.coat:coat-libs' --no-transfer-progress
