#!/usr/bin/env bash

set -euo pipefail

# generate the documentation
if [ $# -ne 1 ]; then
  echo "USAGE: $0 [output_dir]" >&2
  echo "WARNING: the [output_dir] will be REMOVED before generation!" >&2
  exit 2
fi

# make output directory
output_dir=$(realpath $1)
mkdir -p $output_dir
rm -rv $output_dir
mkdir -p $output_dir
echo '*' > $output_dir/.gitignore

# source directories
src_dir=$(dirname $0)
top_dir=$src_dir/../..

# generate build files
cp $src_dir/zensical.toml $output_dir/
cp -r $src_dir/docs $output_dir/
$src_dir/src/banks.rb $top_dir/etc/bankdefs/hipo4 $output_dir/docs

# build
zensical build --config-file $output_dir/zensical.toml
# tree $output_dir
echo """
Done; if you want to serve it locally, run:
  zensical serve --config-file $output_dir/zensical.toml
"""
