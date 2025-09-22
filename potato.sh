#!/usr/bin/env bash
set -euo pipefail
./build-coatjava.sh -T8
java -Dorg.jlab.logging.TestSplitLogger.level=FINEST -Xmx1536m -Xms1024m -XX:+UseSerialGC -cp '/home/dilks/j/coatjava/coatjava/lib/clas/*:/home/dilks/j/coatjava/coatjava/lib/services/*:/home/dilks/j/coatjava/coatjava/lib/utils/*' org.jlab.logging.TestSplitLogger
