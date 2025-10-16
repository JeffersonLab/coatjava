#!/usr/bin/env bash

set -euo pipefail

# generate the documentation
if [ $# -ne 1 ]; then
  echo "USAGE: $0 [output_dir]" >&2
  exit 2
fi

# make output dir
output_dir=$(realpath $1)
mkdir -p $output_dir

# make build directory
build_dir=${output_dir}__build
mkdir -p $build_dir
rm -r $build_dir
mkdir -p $build_dir

# source directories
src_dir=$(dirname $0)
top_dir=$src_dir/../..

# generate build files
cp $src_dir/mkdocs.yaml $build_dir/
cp -r $src_dir/docs $build_dir/
$src_dir/src/banks.rb $top_dir/etc/bankdefs/hipo4 $build_dir/docs
tree $build_dir

# build
mkdocs build --config-file $build_dir/mkdocs.yaml --site-dir $output_dir
tree $output_dir
