#!/usr/bin/env bash
set -euo pipefail
sep() { echo '=================================================================================='; }
# ./build-coatjava.sh -T8 --clara

### test with local RG-D test file (as done in the CI)
rm -vfr tmp
coatjava/bin/run-clara \
  -y etc/services/rgd-clarode.yml \
  -t 8 \
  -n 500 \
  -c ./clara \
  -o ./tmp \
  validation/advanced-tests/data/evio/rg-d/clas_018779.evio.01339
sep
grep \
  --color \
  '^clockbug.*' \
  $(find tmp/log -type f -name '*dpe.log')
sep
coatjava/bin/run-groovy scan.groovy tmp/rec_clas_018779.evio.01339.hipo
