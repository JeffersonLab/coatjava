#!/usr/bin/env bash
# analyze maven dependencies
# NOTE: skips `coat-libs`, the shaded JAR module
set -euo pipefail
mvn javadoc:aggregate -pl '!org.jlab.coat:coat-libs' --no-transfer-progress
