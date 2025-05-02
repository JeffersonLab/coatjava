#!/usr/bin/env bash

set -euo pipefail

# generate the documentation
if [ $# -ne 1 ]; then
  echo "USAGE: $0 [output_dir]" >&2
  exit 2
fi

# make output dir
output_dir=$1
mkdir -p $output_dir

# make build directory
build_dir=${output_dir}__build
mkdir $build_dir # prefer to fail if already exists

# source directories
src_dir=$(dirname $0)
top_dir=$(src_dir)/../..

# generate build files
cp $src_dir/mkdocs.yaml $build_dir/
cp -r $src_dir/files $build_dir/
$src_dir/src/banks.rb $top_dir/etc/bankdefs/hipo4 > $build_dir/files/banks.md
tree $build_dir

# build
mkdocs build --config-file $build_dir/mkdocs.yaml --site-dir $output_dir
tree $output_dir

# no need to keep the build dir
rm -r $build_dir
