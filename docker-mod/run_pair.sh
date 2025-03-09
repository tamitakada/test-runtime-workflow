#!/bin/bash

test_class="$1"
custom_order="$2"

SCRIPT_USERNAME="runtimeprofiler"

srcBaseDir="/home/runtimeprofiler/results-java"
dstBaseDir="/Scratch/results-java"

project_log_dir="${dstBaseDir}/commons-imaging.git"
LOGFILE="imaging.log"

echo "Here!!!"

insert_jvm_args_in_pom_path="/Scratch/insert_jvm_args_in_pom.py"
get_default_package_name_path="/Scratch/get_default_package_name.py"
CustomOrderer_path="/Scratch/CustomOrderer.java"

# maven options
MVN_OPTS="-Djacoco.skip=true -Dmaven.javadoc.skip=true -Drat.skip=true -Dmaven.test.failure.ignore=true \
-fn -Dlicense.skip=true -Dcheckstyle.skip -Denforcer.skip=true -Dspotbugs.skip=true -Dfindbugs.skip=true \
-DfailIfNoTests=false -DdetailLevel=elaborate -DskipSurefireTests=true -Dmaven.failAtEnd=true -DskipUTs=true \
-Dtest=$test_class"

exDir="$(pwd)/experiment"
res_dir="$(pwd)/results-java"

cd "${exDir}"

project_name="commons-imaging.git"
project_res_dir="${res_dir}/${project_name}"
mkdir -p "$project_res_dir"

cd commons-imaging

project_root_dir="$(pwd)"

jfc_file="/Scratch/custom.jfc"

# Run tests
run_tests() {
    local iteration=$1
    local order_suffix=$2

    dest="$project_res_dir/$order_suffix"
    mkdir -p "$dest"

    flight_recording_option="-XX:+FlightRecorder -XX:StartFlightRecording=filename=imaging_flight_$iteration.jfr,settings=$jfc_file -XX:FlightRecorderOptions=stackdepth=1024"
    gc_option="-Xlog:gc=debug:file=gc.log:time"
    listener_dep="<groupId>customlistener</groupId><artifactId>MavenMyListener</artifactId><version>1.0-SNAPSHOT</version>"

    #python3 "$insert_jvm_args_in_pom_path" --dir "$project_root_dir" --args "$flight_recording_option" --deps "$listener_dep"
    python3 "$insert_jvm_args_in_pom_path" --dir "$project_root_dir" --args "$flight_recording_option $gc_option" --deps "$listener_dep" --junit4listener
    #${mvn_type} ${mvn_cmnd} -DargLine="$flight_recording_option" -DjfrPath="./${modified_slug}_flight_$iteration.jfr" $MVN_OPTS
    /home/runtimeprofiler/apache-maven/bin/mvn test -DargLine="$flight_recording_option $gc_option" -DjfrPath="./imaging_flight_$iteration.jfr" $MVN_OPTS
    python3 "$insert_jvm_args_in_pom_path" --dir "$project_root_dir" --restore

    cp "imaging_flight_$iteration.jfr" "$dest"
    cp gc.log "$dest"
    cp -r /home/listener/output/logs "$dest"
    rm -rf /home/listener/output
    cp -r "target/surefire-reports" "$dest"
}

default_package_name=$(python3 ${get_default_package_name_path} "$project_root_dir")
package_dir=$(echo "$default_package_name" | sed 's/\./\//g')
destination_dir="${project_root_dir}/src/test/java/${package_dir}"
cp "$CustomOrderer_path" "$destination_dir/"
sed -i "1i package $default_package_name;" "$destination_dir/$(basename "$CustomOrderer_path")" # insert package name at the top of the file
mkdir -p "${project_root_dir}/src/test/resources"
cp "$custom_order" "${project_root_dir}/src/test/resources/custom-order.txt"

# add lines to junit-platform.properties
class_order_line="junit.jupiter.testclass.order.default=${default_package_name}.CustomOrderer"
method_order_line="junit.jupiter.testmethod.order.default=${default_package_name}.CustomOrderer"
props_file=$(find "${project_root_dir/src/test/resources}" -type f -name "junit-platform.properties")
if [ -n "$props_file" ]; then
    # add lines to the file
    echo -e "$class_order_line\n$method_order_line" >> "$props_file"
    echo "Added custom order lines to existing junit-platform.properties file."
else
    # create the file with the lines
    echo -e "$class_order_line\n$method_order_line" > "${project_root_dir}/src/test/resources/junit-platform.properties"
    echo "Created junit-platform.properties with custom order lines."
fi

for i in {1..10}; do
    echo -e "\niteration: $i\n"
    run_tests "$i" "$i"
done

cd ../..

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
