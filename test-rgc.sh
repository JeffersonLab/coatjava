#!/usr/bin/env bash
set -euo pipefail
sep() { echo '=================================================================================='; }
# ./build-coatjava.sh -T8 --clara

### decode an RG-C Summer 2022 NH3 file
rm rgc.hipo -f
coatjava/bin/decoder \
  -l FINE \
  -n 10000 \
  -o rgc.hipo \
  /cache/clas12/rg-c/data/clas_016330/clas_016330.evio.00808 2>&1 | tee clockbug.log
grep --color '^clockbug.*' clockbug.log

### reconstruct; its `README.json` file is copied here to `rg-c__summer22__10.5gev__NH3__README.json`
# rm -vfr tmp
# coatjava/bin/run-clara \
#   -y rg-c__summer22__10.5gev__NH3.yaml \
#   -t 8 \
#   -n 500 \
#   -c ./clara \
#   -o ./tmp \
#   /cache/clas12/rg-c/data/clas_016330/clas_016330.evio.00808
#   # rgc.hipo
# sep
# grep \
#   --color \
#   '^clockbug.*' \
#   $(find tmp/log -type f -name '*dpe.log')
# sep
# coatjava/bin/run-groovy scan.groovy tmp/rec_clas_016330.evio.00808.hipo
