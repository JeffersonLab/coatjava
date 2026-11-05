#!/bin/bash

export CLAS12DIR=$(cd $(dirname ${BASH_SOURCE[0]:-$0})/.. && pwd -P)

export PATH=$CLAS12DIR/bin:$PATH

if [ "${1-}" = '--shell' ]; then
    return
fi

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

# set log manager
export JAVA_OPTS="-Djava.util.logging.manager=org.jlab.logging.SplitLogManager ${JAVA_OPTS-}"

# additional environment variables for groovy or interactive use
# - call as `source $0 groovy` or `source $0 jshell`
if [ $# -ge 1 ]; then
  if [ "$1" = "groovy" -o "$1" = "jshell" ]; then
    if [ "$1" = "groovy" ]; then
      export JAVA_OPTS="-Dsun.java2d.pmoffscreen=false -Djava.util.logging.config.file=$CLAS12DIR/etc/logging/debug.properties -Xms1024m -Xmx2048m -XX:+UseSerialGC ${JAVA_OPTS-}"
    fi
    export JYPATH=${JYPATH:+${JYPATH}:}$COATJAVA_CLASSPATH
  fi
fi

# get the number of threads from the command line options:
function get_threads() {
    # look for a "-t" threading option:
    for ((i=0; i<${#class_options[@]}; i++)); do
        if [[ "${class_options[$i]}" == "-t" ]]; then
            # found it;  if it contains commas, split and get the max:
            # FIXME:  breaks for threads suffixed by +/- for numa tasksetting
            let i=i+1
            threads=$(echo ${class_options[$i]} | awk -F, 'NR==1 {max=$1} { for(i=1; i<=NF; i++) { if($i > max) max=$i } } END {print max}')
            break
        fi
    done
    [ -z ${threads+x} ] && echo 0 || echo $threads
}

# get a cpu list for taskset, of all cpus on a given NUMA node:
function get_all_numa_cpus() {
     echo $(numactl -H | grep "^node $1 cpus:" | awk '{for(i=4;i<=NF;++i)print$i}') | sed 's/ /,/g'
}

# get a cpu list for tasket, of the first N cpus on a given NUMA node:
function get_numa_cpus() {
    echo $(numactl -H | grep "^node $1 cpus:" | awk -v T="$2" '{for(i=4;i<=T;++i)print$i}') | sed 's/ /,/g'
}

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

