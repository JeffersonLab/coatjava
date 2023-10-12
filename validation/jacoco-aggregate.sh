#!/bin/bash
# simple script to aggregate jacoco results, until we figure out a better way

mkdir -p publish
rm -r publish
mkdir -p publish
for d in $(find -type d -name 'jacoco'); do
  target=publish/$(echo $d | sed 's;^\./;;')
  mkdir -p $target
  cp -r $d/* $target/
done

pushd publish

cat << EOF > index.html
<html>
<head>
<title>JaCoCo Coverage Summary</title>
</head>
<body>
<h1>JaCoCo Coverage Summary</h1>
<ul>
EOF

for indexPage in $(find . -name "index.html" | grep 'jacoco/index'); do
  link=$(echo $indexPage | sed 's;^./;;')
  name=$(echo $link | sed 's;/target/site/.*;;')
  cat << EOF >> index.html
<li><a href="$link">$name</a></li>
EOF
done

cat << EOF >> index.html
</body>
</html>
EOF

echo "==============="
cat index.html
echo "==============="
popd
