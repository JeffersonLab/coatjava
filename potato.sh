#!/usr/bin/env bash
set -euo pipefail
# ./build-coatjava.sh -T8
java \
  -Xmx1536m -Xms1024m -XX:+UseSerialGC \
  -Djava.util.logging.config.file=tomato.properties \
  -cp '/home/dilks/j/coatjava/coatjava/lib/clas/*:/home/dilks/j/coatjava/coatjava/lib/services/*:/home/dilks/j/coatjava/coatjava/lib/utils/*' \
  org.jlab.logging.TestSplitLogger

  # -Djava.util.logging.level=FINEST \
  # -Dorg.jlab.logging.TestSplitLogger.level=FINEST \
