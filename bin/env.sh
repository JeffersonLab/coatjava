#!/bin/sh

export CLAS12DIR=`dirname $0`/..

# Set default field maps (but do not override user's env):
if [ -z "$COAT_MAGFIELD_TORUSMAP" ]; then
    export COAT_MAGFIELD_TORUSMAP=Symm_torus_r2501_phi16_z251_24Apr2018.dat
fi
if [ -z "$COAT_MAGFIELD_TORUSSECONDARYMAP" ]; then
    export COAT_MAGFIELD_TORUSSECONDARYMAP=Full_torus_r251_phi181_z251_25Jan2021.dat
fi
if [ -z "$COAT_MAGFIELD_SOLENOIDMAP" ]; then
    export COAT_MAGFIELD_SOLENOIDMAP=Symm_solenoid_r601_phi1_z1201_13June2018.dat
fi

# additional environment variables for groovy or interactive use
# - call as `source $0 groovy` or `source $0 jshell`
if [ $# -ge 1 ]; then
  if [ "$1" == "groovy" -o "$1" == "jshell" ]; then

    # add jar files to class path
    for lib in clas services utils; do
      for jars in $(ls -a $CLAS12DIR/lib/$lib/*.jar); do
        JYPATH="${JYPATH:+${JYPATH}:}${jars}"
      done
    done

    # additional variables and settings for groovy
    if [ "$1" == "groovy" ]; then
      JYPATH="${JYPATH:+${JYPATH}:}${CLAS12DIR}/lib/packages"
      export JAVA_OPTS="-Dsun.java2d.pmoffscreen=false -Djava.util.logging.config.file=$CLAS12DIR/etc/logging/debug.properties -Xms1024m -Xmx2048m -XX:+UseSerialGC"
    fi

    export JYPATH

  fi
fi
