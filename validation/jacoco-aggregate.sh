#!/bin/bash
# Prepare JaCoCo report for docs site CI.
# With single-module Maven layout, target/site/jacoco/ is produced directly.
# This script copies the single report to publish/ for CI artifact ingestion.

set -e

mkdir -p publish
rm -rf publish
mkdir -p publish

# Single module: copy root target/site/jacoco directly
if [ -d "target/site/jacoco" ]; then
  cp -r target/site/jacoco publish/
  echo "Copied target/site/jacoco to publish/jacoco"
else
  echo "ERROR: target/site/jacoco not found (build may not have run)" >&2
  exit 1
fi

pushd publish

cat << EOF > index.html
<html>
<head>
<title>JaCoCo Coverage Summary</title>
</head>
<body>
<h1>JaCoCo Coverage Summary</h1>
<ul>
<li><a href="jacoco/index.html">JaCoCo Coverage Report</a></li>
</ul>
</body>
</html>
EOF

echo "==============="
cat index.html
echo "==============="
popd
