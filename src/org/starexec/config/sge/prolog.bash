#!/bin/bash

# /////////////////////////////////////////////
# NAME:        
# StarExec Global Prolog Script
#
# AUTHOR:      
# Tyler Jensen
#
# MODIFIED:    
# 10/25/2013
#
# DESCRIPTION:
# This script runs BEFORE the user's job and
# is used to set up the node's environment by
# copying files locally to the node from the NFS.
# There are many environmental variables used here
# That are defined by SGE and by the jobscript in
# the starexec project.
#
# NOTE: This file is deployed to use when the project
# is built with the production build scripts
#
# /////////////////////////////////////////////

# Include the predefined status codes and functions
. /home/starexec/sge_scripts/functions.bash

# Path to local workspace for each node in cluster.
#TODO: Should be either sandbox1 or sandbox2
SANDBOX=1

if [ $SANDBOX -eq 1 ]
then
WORKING_DIR='/export/starexec/sandbox'
else
WORKING_DIR='/export/starexec/sandbox2'
fi


# Path to Olivier Roussel's runSolver
RUNSOLVER_PATH="/home/starexec/Solvers/runsolver"

# Path to where the solver will be copied
LOCAL_SOLVER_DIR="$WORKING_DIR/solver"

#path to where cached solvers are stored
SOLVER_CACHE_PATH="/export/solvercache/$SOLVER_TIMESTAMP/$SOLVER_ID"

#whether we were able to find a cached solver
SOLVER_CACHED=0

# Path to where the benchmark will be copied
LOCAL_BENCH_DIR="$WORKING_DIR/benchmark"

# Path to the job input directory
JOB_IN_DIR="$SHARED_DIR/jobin"

# Actual qualified name of the config run script
CONFIG_NAME="starexec_run_$CONFIG_NAME"

# The benchmark's name
BENCH_NAME="${BENCH_PATH##*/}"

# The path to the benchmark on the execution host
LOCAL_BENCH_PATH="$LOCAL_BENCH_DIR/$BENCH_NAME"

# The path to the config run script on the execution host
CONFIG_PATH="$LOCAL_SOLVER_DIR/bin/$CONFIG_NAME"

# The path to the bin directory of the solver on the execution host
BIN_PATH="$LOCAL_SOLVER_DIR/bin"

# Array of secondary benchmarks starexec paths
declare -a BENCH_DEPENDS_ARRAY

# Array of secondary benchmarks execution host paths
declare -a LOCAL_DEPENDS_ARRAY

# /////////////////////////////////////////////
# Functions
# /////////////////////////////////////////////

#fills arrays from file
function fillDependArrays {
#separator
sep=',,,'

INDEX=0

log "has depends = $HAS_DEPENDS"

if [ $HAS_DEPENDS -eq 1 ]
then
while read line
do
  BENCH_DEPENDS_ARRAY[INDEX]=${line//$sep*};
  LOCAL_DEPENDS_ARRAY[INDEX]=${line//*$sep};
  INDEX=$((INDEX + 1))
done < "$JOB_IN_DIR/depend_$PAIR_ID.txt"
fi

return $?
}



#will see if a solver is cached and change the SOLVER_PATH to the cache if so
function checkCache {
	if [ -d "$SOLVER_CACHE_PATH" ]; then
  		SOLVER_PATH = $SOLVER_CACHE_PATH
  		SOLVER_CACHED= 1 	
	fi
}

function copyDependencies {
	log "copying solver:  cp -r $SOLVER_PATH/* $LOCAL_SOLVER_DIR"
	cp -r "$SOLVER_PATH"/* "$LOCAL_SOLVER_DIR"
	if [ $SOLVER_CACHED -eq 0 ]; then
		#store solver in a cache
	cp -r "#LOCAL_SOLVER_DIR"/* "$SOLVER_CACHE_PATH"
	fi
	
	log "solver copy complete"

        log "chmod gu+rwx on the solver directory on the execution host ($LOCAL\
_SOLVER_DIR)"
        chmod -R gu+rwx $LOCAL_SOLVER_DIR

	log "copying runSolver to execution host..."
	cp "$RUNSOLVER_PATH" "$BIN_PATH"
	log "runsolver copy complete"
	ls -l "$BIN_PATH"

	log "copying benchmark (${BENCH_PATH##*/}) to execution host..."
	cp "$BENCH_PATH" "$LOCAL_BENCH_DIR"
	log "benchmark copy complete"
	
	log "copying benchmark dependencies to execution host..."
	for (( i = 0 ; i < ${#BENCH_DEPENDS_ARRAY[@]} ; i++ ))
	do
		#log "Axiom location = '${BENCH_DEPENDS_ARRAY[$i]}'"
		NEW_D=$(dirname "$LOCAL_BENCH_DIR/${LOCAL_DEPENDS_ARRAY[$i]}")
		mkdir -p $NEW_D
		cp "${BENCH_DEPENDS_ARRAY[$i]}" "$LOCAL_BENCH_DIR/${LOCAL_DEPENDS_ARRAY[$i]}"
	done
	log "benchmark dependencies copy complete"
	return $?	
}

#benchmark dependencies not currently verified.
function verifyWorkspace { 
	# Make sure the configuration exists before we execute it
	if ! [ -x "$CONFIG_PATH" ]; then
		log "job error: could not locate the configuration script '$CONFIG_NAME' on the execution host"
		sendStatus $ERROR_RUNSCRIPT
	else
		log "execution host solver configuration verified"	
	fi	

	# Make sure the benchmark exists before the job runs
	if ! [ -r "$LOCAL_BENCH_PATH" ]; then
                echo "job error: could not locate the readable benchmark '$BENCH_NAME' on the execution host."
                sendStatus $ERROR_BENCHMARK
        else
		log "execution host benchmark verified"
	fi		

	return $?
}
#TODO: need to change "sandbox" to "sandbox1"
function sandboxWorkspace {
	if [[ $WORKING_DIR == *sandbox2* ]] 
	then
	sudo chown -R sandbox2 $WORKING_DIR 
	else
	sudo chown -R sandbox $WORKING_DIR
	fi
	ls -lR "$WORKING_DIR"
	return 0
}

# /////////////////////////////////////////////
# MAIN
# /////////////////////////////////////////////

whoami
echo "STAREXEC Job Log"
echo "(C) `date +%Y` The University of Iowa"
echo "====================================="
echo "date: `date`"
echo "user: #$USER_ID"
echo "sge job: #$JOB_ID"
echo "solver: $SOLVER_NAME"
echo "configuration: $CONFIG_NAME"
echo "benchmark: ${BENCH_PATH##*/}"
echo "execution host: $HOSTNAME"
echo ""

sendStatus $STATUS_PREPARING
sendNode "$HOSTNAME"
cleanWorkspace
fillDependArrays
checkCache
copyDependencies
verifyWorkspace
sandboxWorkspace

# Determine if there were errors in the job setup
JOB_ERROR=`grep 'job error:' $SGE_STDOUT_PATH`

# If there was no error...
if [ "$JOB_ERROR" = "" ]; then
	log "running $SOLVER_NAME ($CONFIG_NAME) on $BENCH_NAME"
	sendStatus $STATUS_RUNNING
fi

exit 0
