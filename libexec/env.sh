#!/bin/bash

export CLAS12DIR=$(cd $(dirname ${BASH_SOURCE[0]:-$0})/.. && pwd -P)

# Set default field maps (but do not override user's env):
if [ -z "${COAT_MAGFIELD_TORUSMAP-}" ]; then
    export COAT_MAGFIELD_TORUSMAP=Symm_torus_r2501_phi16_z251_24Apr2018.dat
fi
if [ -z "${COAT_MAGFIELD_TORUSSECONDARYMAP-}" ]; then
    export COAT_MAGFIELD_TORUSSECONDARYMAP=Full_torus_r251_phi181_z251_25Jan2021.dat
fi
if [ -z "${COAT_MAGFIELD_SOLENOIDMAP-}" ]; then
    export COAT_MAGFIELD_SOLENOIDMAP=Symm_solenoid_r601_phi1_z1201_13June2018.dat
fi

# set the classpath
if [ "${1-}" != '--no-classpath' ]; then
  COATJAVA_CLASSPATH=''
  jar_dirs=(
    $CLAS12DIR/lib/clas # prioritize the shaded JAR
    $CLAS12DIR/lib/services
    $CLAS12DIR/lib/utils
  )
  for jar_dir in ${jar_dirs[@]}; do
    if [ -d $jar_dir ]; then
      COATJAVA_CLASSPATH=${COATJAVA_CLASSPATH:+${COATJAVA_CLASSPATH}:}$jar_dir/\*
    else
      echo "WARNING: installation directory does not exist: $jar_dir" >&2
    fi
  done
  unset jar_dirs
  export COATJAVA_CLASSPATH
fi

# additional environment variables for groovy or interactive use
# - call as `source $0 groovy` or `source $0 jshell`
if [ $# -ge 1 ]; then
  if [ "$1" = "groovy" -o "$1" = "jshell" ]; then
    if [ "$1" = "groovy" ]; then
      export JAVA_OPTS="-Dsun.java2d.pmoffscreen=false -Djava.util.logging.config.file=$CLAS12DIR/etc/logging/debug.properties -Xms1024m -Xmx2048m -XX:+UseSerialGC ${JAVA_OPTS-}"
    fi
    export JYPATH=$COATJAVA_CLASSPATH
  fi
fi

function split_cli {
    jvm_options=()
    class_options=()
    while [[ $# -gt 0 ]]
    do
        case $1 in 
            --) shift && jvm_options=("${@}") && break ;;
            *)  class_options+=($1) && shift ;;
        esac
    done
}

