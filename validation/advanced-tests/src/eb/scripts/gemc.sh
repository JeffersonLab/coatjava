#!/bin/sh

usage() { echo "Usage: $0 [-g GEMC] [-n NEV] [-p PARTS] [-c GCARD] [-m]" 1>&2; exit $1; }

run=11
gemc=5.10
nevents=100
particles=()

while getopts "g:n:c:p:mdh" o
do
    case ${o} in
        g) gemc=${OPTARG} ;;
        n) nevents=${OPTARG} ;;
        c) gcard=${OPTARG} ;;
        p) particles+=(${OPTARG}) ;;
        m) multithread=yes ;;
        d) dryrun=echo ;;
        h) usage 0 ;;
        *) usage 1 ;;
    esac
done

if [ ${#particles[@]} -eq 0 ]
then
    top=$(cd $(dirname ${BASH_SOURCE[0]}) && pwd)
    for x in $(awk '{print$1}' $top/list.txt)
    do
        particles+=($x)
    done
fi

if [ -z ${gcard+x} ]
then
    test -d clas12-config || git clone https://github.com/jeffersonlab/clas12-config
    gcard=clas12-config/gemc/$gemc/clas12-default.gcard 
fi

function run_gemc () {
    local OPTIND
    while getopts "r:g:n:c:i:o:d" o
    do
        case ${o} in
            r) _run=${OPTARG} ;;
            g) _gemc=${OPTARG} ;;
            n) _nevents=${OPTARG} ;;
            c) _gcard=${OPTARG} ;;
            i) _input=${OPTARG} ;;
            o) _output=${OPTARG} ;;
        esac
    done
    ! [ -e "$_input" ] && echo Missing input file:  $_input && exit 2
    [ -e "$_output" ] && echo Output file already exists:  $_output && exit 3
    [ -z ${dryrun+x} ] && set -x
    $dryrun gemc \
        $_gcard -RUNNO=$_run -USE_GUI=0 -N=$_nevents \
        -SAVE_ALL_MOTHERS=1 -SKIPREJECTEDHITS=1 -INTEGRATEDRAW="*" -NGENP=50 \
        -INPUT_GEN_FILE="LUND, $_input" \
        -OUTPUT="hipo, $_output" &
    pid=$!
    [ -z ${dryrun+x} ] && set +x
    [ -z ${multithread+x} ] && wait $pid
}

for p in "${particles[@]}"
do
    [ -z ${multithread+} ] && args= || args=-m 
    run_gemc -r $run -g $gemc -n $nevents -c $gcard -i $p.txt -o $p.hipo $args
done
[ -z ${multithread+x} ] || wait

