#!/usr/bin/env bash
# run spotbugs; additional arguments are passed to `mvn` (such as `-T`)
set -euo pipefail
mvn spotbugs:check "$@"
# mvn spotbugs:gui --no-transfer-progress # must be single-threaded
