#!/bin/bash

url="$1"
sha="$2"
module="$3"
test_class="$4"
jfc_config="$5"
mvn_type="$6"
mvn_cmnd="$7"
random="$8"
custom_order="$9"
method_profile="${10}"
custom_cmnds="${11}"

SCRIPT_USERNAME="runtimeprofiler"


# edit the jfr settings file to enable objectcount (this is for jdk 11, probably different or easier for jdk 17)
sed -i 's/<setting name="enabled" control="memory-profiling-enabled-all">false<\/setting>/<setting name="enabled" control="memory-profiling-enabled-all">true<\/setting>/' /usr/lib/jvm/java-11-openjdk-amd64/lib/jfr/default.jfc

# run
srcBaseDir="/home/runtimeprofiler/results-java"
dstBaseDir="/Scratch/results-java"

project_name=$(basename "$url")
project_log_dir="${dstBaseDir}/${project_name}"
slug=$(echo ${url} | rev | cut -d'/' -f1-2 | rev)
modified_slug=${slug//\//_}
if [ -n "$module" ]; then
	modified_module=${module//\//_}  # Replace '/' with '_'
    modified_slug="${modified_slug}_${modified_module}"
    project_log_dir="${project_log_dir}/$modified_module"
fi

LOGFILE="$modified_slug.log"

su "$SCRIPT_USERNAME" -c "/Scratch/run_maven.sh ${url} ${sha} \"${module}\" \"${test_class}\" ${jfc_config} ${mvn_type} ${mvn_cmnd} \"${random}\" \"${custom_order}\" ${custom_cmnds} ${method_profile}" |& tee $LOGFILE

chmod -R 777 $srcBaseDir
chmod -R 777 $LOGFILE

# copy files
mkdir -p $dstBaseDir
if [ -d "$srcBaseDir" ]; then
    cp -r "$srcBaseDir/"* "$dstBaseDir/"
fi

# copy log
if [ -f "$LOGFILE" ]; then
    cp "$LOGFILE" "$project_log_dir/"
fi

chmod -R 777 $dstBaseDir
