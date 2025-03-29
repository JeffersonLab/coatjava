#!/usr/bin/env bash

# FIXME
# FIXME
# FIXME
# FIXME: remove before marking PR as ready
# FIXME
# FIXME
# FIXME
#

set -e
[ $# -ne 2 ] && echo "input output" && exit 3

mkdir -p $2
rm -r $2
mkdir -p $2

for j in $(find $1 -type f -name "*.jar"|sort); do
  jar tf $j | sort > $2/$(basename $j|sed 's/-1.*//g').out
done
