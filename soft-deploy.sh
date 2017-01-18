#!/bin/bash
#
# Run from the directory where you build starexec with ant (build.xml).

# filled in by running ant with build.xml:
APPNAME=starexec_agiegerich
STAREXEC_DATA_DIR=/home/starexec/starexec_agiegerich/data_dir
WEB_HOME=/project/tomcat-webapps/webapps


SGE_SCRIPT_DIR=$STAREXEC_DATA_DIR/sge_scripts
WAR=$APPNAME.war
APPDIR=$WEB_HOME/$APPNAME
STAR_WAR=$WEB_HOME/$WAR

if [ ! -f $WAR ] ; then
  echo "This script should be run from the deployed/ directory, "
  echo "where starexec.war can be found."
  exit 1
fi

echo Moving existing war file: $STAR_WAR
rm -f $STAR_WAR 

# create the first argument to mkdirIf if it does not exist,
# and set ownership and permissions.
mkdirIf() {
if [ ! -d $1 ] ; then
  mkdir -p $1
  chmod 775 $1
  chgrp star-web $1
fi
}

echo "Copying the GridEngine scripts to $SGE_SCRIPT_DIR"
mkdirIf $SGE_SCRIPT_DIR
for f in src/org/starexec/config/sge/{jobscript,*.bash} ; do
  b=`basename $f`
  cp $f $SGE_SCRIPT_DIR
  chmod -f 775 $SGE_SCRIPT_DIR/$b
  chgrp -f star-web $SGE_SCRIPT_DIR/$b
done

echo "Copying default pictures $STAREXEC_DATA_DIR/pictures"
for d in users solvers benchmarks ; do
 mkdirIf $STAREXEC_DATA_DIR/pictures/$d
 cp starexec_res/default-pics/$d/Pic0.jpg $STAREXEC_DATA_DIR/pictures/$d
 chmod -f 775 $STAREXEC_DATA_DIR/pictures/$d/Pic0.jpg
 chgrp -f star-web $STAREXEC_DATA_DIR/pictures/$d/Pic0.jpg 
done

echo "Creating data directories if they are not present already"

for d in Benchmarks Solvers processor_scripts jobin jobxml jobout joboutput joboutput/logs batchSpace/uploads ; do
  mkdirIf $STAREXEC_DATA_DIR/$d
done

while [ -d $APPDIR ] ; do
  echo "waiting for tomcat to remove the app directory"
  sleep 4
done

if [ "$1" == "--remove" ] ; then
  echo Removed $APPNAME without restarting Tomcat.
  exit 0
fi

echo Copying new WAR to $WEB_HOME
cp $APPNAME.war $WEB_HOME/$WAR

#echo Changing permissions on $STAR_WAR
chmod 775 $STAR_WAR

while [ ! -d $APPDIR ] ; do
  echo "waiting for tomcat to recreate the app directory"
  sleep 4
done

echo Deployed $APPNAME without restarting Tomcat.
echo "Update the database (schema and procedures) as needed." 
