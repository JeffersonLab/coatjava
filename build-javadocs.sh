#!/bin/bash
#
# surely this should be done more properly with only maven, meanwhile ...
#

set -e

##### generate documentation
mvn javadoc:javadoc -Ddoclint=none


##### collect documentation
src=target/reports/apidocs
dest=docs/javadoc

for dir in `find . -type d | grep $src$`
do
    mkdir -p $dest/${dir%$src}
    cp -r $dir/* $dest/${dir%$src}
done


##### generate front index page
pushd $dest

cat > index.html << EOF
<html><body bgcolor="cccccc"><head>
<title>coatjava javadocs</title>
<STYLE TYPE="text/css"></STYLE>
</head>
<p><p align="center"><b><font size="5">CLAS12 Coatjava Javadocs <br></b></font></p>
<br>
<br>
EOF

pages=($(find -name "index.html" | sed 's;^\./;;' | sed 's;/index.html;;' | grep -v index.html | sort))
header=""
for page in ${pages[@]}; do
  headerTmp=$(echo $page | sed 's;/.*;;g')
  obj=$(echo $page | sed "s;^$headerTmp/;;")
  if [ "$header" != "$headerTmp" ]; then
    [ "$header" != "" ] && echo "</ul>" >> index.html
    header=$headerTmp
    echo "<h3>$header</h3>" >> index.html
    echo "<ul>" >> index.html
  fi
  echo "<li><a href=\"$page/index.html\">$obj</a></li>" >> index.html
done

cat >> index.html << EOF
</ul>
</body>
</html>
EOF

popd
echo "Documentation generated: $dest/index.html"
