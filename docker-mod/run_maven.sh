#!/bin/bash

echo "START run_java.sh"
date

url=$1
sha=$2
module="$3"
test_class="$4"
jfc_config="$5"
mvn_type="$6"
mvn_cmnd="$7"
random="$8"
custom_order="$9"
custom_cmnds="${10}"
method_profile="${11}"

insert_jvm_args_in_pom_path="/Scratch/insert_jvm_args_in_pom.py"
get_default_package_name_path="/Scratch/get_default_package_name.py"
CustomOrderer_path="/Scratch/CustomOrderer.java"

# maven options
MVN_OPTS="-Djacoco.skip=true -Dmaven.javadoc.skip=true -Drat.skip=true -Dmaven.test.failure.ignore=true \
-fn -Dlicense.skip=true -Dcheckstyle.skip -Denforcer.skip=true -Dspotbugs.skip=true -Dfindbugs.skip=true \
-DfailIfNoTests=false -DdetailLevel=elaborate -DskipSurefireTests=true -Dmaven.failAtEnd=true -DskipUTs=true"

mkdir -p experiment
mkdir -p results-java
exDir="$(pwd)/experiment"
res_dir="$(pwd)/results-java"

cp -r /Scratch/MavenMyListener "${exDir}"

# go into experiment/
cd "${exDir}"

cd MavenMyListener
/home/runtimeprofiler/apache-maven/bin/mvn clean install
cd ..

slug=$(echo "${url}" | rev | cut -d'/' -f1-2 | rev)
modified_slug=${slug//\//_}  # Replace forward slashes with underscores
project_name=$(basename "$url")
project_res_dir="${res_dir}/${project_name}"
mkdir -p "$project_res_dir"

# clone the project with the slug name
if [ ! -d "${slug}" ]; then
    git clone "${url}" "${slug}"
fi

# move into the cloned project
cd "${slug}"

project_root_dir="$(pwd)"

# checkout the sha
git checkout -f "${sha}"


echo $mvn_type
if [ "$mvn_type" = "mvnw" ]; then
    mvn_type="${project_root_dir}/mvnw"
fi
echo $mvn_type
#java -XX:+PrintFlagsFinal | grep G1GCThreads

if [ -n "$module" ]; then
    /Scratch/$custom_cmnds
    ${mvn_type} clean install  ${MVN_OPTS} -q -DskipTests -DskipSurefireTests -DskipUTs
    cd "$module"
    modified_module=${module//\//_}  # replace '/' with '_'
    modified_slug="${modified_slug}_${modified_module}"
    project_res_dir="${project_res_dir}/$modified_module"
    mkdir -p $project_res_dir
    
else
    $mvn_type clean install  ${MVN_OPTS} -q -DskipTests -DskipSurefireTests -DskipUTs
fi

jfc_file="default.jfc"
if [ "$jfc_config" == "profile" ]; then
    jfc_file="profile.jfc"
elif [ "$jfc_config" != "default" ]; then
    jfc_file="$jfc_config"
fi


# Run tests
run_tests() {
    local iteration=$1
    local order_suffix=$2

    dest="$project_res_dir/$order_suffix"
    mkdir -p "$dest"

    flight_recording_option="-XX:+FlightRecorder -XX:StartFlightRecording=filename=${modified_slug}_flight_$iteration.jfr,settings=$jfc_file -XX:FlightRecorderOptions=stackdepth=1024"
    gc_option="-Xlog:gc=debug:file=gc.log:time"
    listener_dep="<groupId>customlistener</groupId><artifactId>MavenMyListener</artifactId><version>1.0-SNAPSHOT</version>"

    #python3 "$insert_jvm_args_in_pom_path" --dir "$project_root_dir" --args "$flight_recording_option" --deps "$listener_dep"
    python3 "$insert_jvm_args_in_pom_path" --dir "$project_root_dir" --args "$flight_recording_option $gc_option" --deps "$listener_dep" --junit4listener
    #${mvn_type} ${mvn_cmnd} -DargLine="$flight_recording_option" -DjfrPath="./${modified_slug}_flight_$iteration.jfr" $MVN_OPTS
    ${mvn_type} ${mvn_cmnd}  -DargLine="$flight_recording_option $gc_option" -DjfrPath="./${modified_slug}_flight_$iteration.jfr" $MVN_OPTS
    python3 "$insert_jvm_args_in_pom_path" --dir "$project_root_dir" --restore

    cp "${modified_slug}_flight_$iteration.jfr" "$dest"
    cp gc.log "$dest"
    cp -r /home/listener/output/logs "$dest"
    rm -rf /home/listener/output
    cp -r "target/surefire-reports" "$dest"
}

if [ "$custom_order" != "" ]; then
    default_package_name=$(python3 ${get_default_package_name_path} "$project_root_dir")
    package_dir=$(echo "$default_package_name" | sed 's/\./\//g')
    destination_dir="${project_root_dir}/src/test/java/${package_dir}"
    cp "$CustomOrderer_path" "$destination_dir/"
    sed -i "1i package $default_package_name;" "$destination_dir/$(basename "$CustomOrderer_path")" # insert package name at the top of the file
    mkdir -p "${project_root_dir}/src/test/resources"  # create the 'resources' directory if it doesn't exist
    cp "$custom_order" "${project_root_dir}/src/test/resources/custom-order.txt" # copy the custom order

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

elif [ "$random" != "" ]; then
    mkdir -p "${project_root_dir}/src/test/resources"  # create the 'resources' directory if it doesn't exist
    for i in {1..10}; do
        echo -e "\norder: $i\n"
        random_seed=$(od -An -N7 -t u8 /dev/urandom)
        echo "Using seed: $random_seed"

        props_file=$(find "$project_root_dir/src/test/resources" -name "junit-platform.properties")
        if [ -n "$props_file" ]; then
            echo -e "\njunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer\$Random\njunit.jupiter.execution.order.random.seed=$random_seed" >> "$props_file"
        else
            echo -e "junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer\$Random\njunit.jupiter.execution.order.random.seed=$random_seed" > "$project_root_dir/src/test/resources/junit-platform.properties"
        fi

        for j in {1..10}; do
            echo -e "\niteration: $j\n"
            run_tests "$j" "order_$i/$j"
        done
    done
else
    echo "Using default test class order"
    for i in {1..10}; do
        echo ""
        echo "iteration: $i"
        echo ""

        dest="$project_res_dir/$i"
        mkdir -p $dest

        # create the flight recording option
        flight_recording_option="-XX:+FlightRecorder -XX:StartFlightRecording=filename=${modified_slug}_flight_$i.jfr,settings=$jfc_file -XX:FlightRecorderOptions=stackdepth=1024"
        
        # create gc option
        gc_option="-Xlog:gc=debug:file=gc.log:time"

        # create test method listener dep
        listener_dep="<groupId>customlistener</groupId><artifactId>MavenMyListener</artifactId><version>1.0-SNAPSHOT</version>"

        # insert the flight recorder flags into pom.xml
        python3 ${insert_jvm_args_in_pom_path} --dir "${project_root_dir}" --args "${flight_recording_option} ${gc_option}" --deps "${listener_dep}" --junit4listener
        #python3 ${insert_jvm_args_in_pom_path} --dir "${project_root_dir}" --args "${flight_recording_option}" --deps "${listener_dep}"

        # run tests
        if [ -n "$test_class" ]; then
            echo "will run test class $test_class only"
            ${mvn_type} ${mvn_cmnd}  -Dtest="$test_class" -DargLine="${flight_recording_option} ${gc_option}" -DjfrPath=./${modified_slug}_flight_$i.jfr ${MVN_OPTS}
            #${mvn_type} ${mvn_cmnd} -Dtest="$test_class" -DargLine="${flight_recording_option}" -DjfrPath=./${modified_slug}_flight_$i.jfr ${MVN_OPTS}
        else
            echo "mvn type: ${mvn_type} mvn cmnd: ${mvn_cmnd}"
            ${mvn_type} ${mvn_cmnd}  -DargLine="${flight_recording_option} ${gc_option}" -DjfrPath=./${modified_slug}_flight_$i.jfr ${MVN_OPTS}
            #${mvn_type} ${mvn_cmnd} -DargLine="${flight_recording_option}" -DjfrPath=./${modified_slug}_flight_$i.jfr ${MVN_OPTS}
        fi

        # reset pom.xml
        python3 ${insert_jvm_args_in_pom_path} --dir "${project_root_dir}" --restore

        # copy .jfr file (we set the name for the file in java command above)
        cp "${modified_slug}_flight_$i.jfr" $dest
        
        cp gc.log $dest

        # copy listener data
        cp -r /home/listener/output/logs $dest
        rm -rf /home/listener/output

        cp -r "target/surefire-reports" $dest
    done
fi


# Function to run method profiling tests
run_method_profile_tests() {
	local order_suffix=$1

	 dest="$project_res_dir/$order_suffix"
	 mkdir -p "$dest"
	 
	 # create the flight recording option
        flight_recording_option="-XX:+FlightRecorder -XX:StartFlightRecording=filename=${modified_slug}_flight_$i.jfr,settings=$jfc_file -XX:FlightRecorderOptions=stackdepth=1024"
        
        gc_option="-Xlog:gc=debug:file=gc.log:time"

        # create test method listener dep
        listener_dep="<groupId>customlistener</groupId><artifactId>MavenMyListener</artifactId><version>1.0-SNAPSHOT</version>"

        # insert the flight recorder flags into pom.xml
        #python3 ${insert_jvm_args_in_pom_path} --dir "${project_root_dir}" --args "${flight_recording_option}" --deps "${listener_dep}" || { echo "Failed to insert JVM args into pom.xml"; exit 1; }
        python3 ${insert_jvm_args_in_pom_path} --dir "${project_root_dir}" --args "${flight_recording_option} ${gc_option}" --deps "${listener_dep}" --junit4listener|| { echo "Failed to insert JVM args into pom.xml"; exit 1; }

	 # Run tests
	 if [ -n "$test_class" ]; then
		echo "Will run test class $test_class only"
		${mvn_type} ${mvn_cmnd}  -Dtest="$test_class" ${MVN_OPTS}
	 else
		echo "Will run all tests"
		${mvn_type} ${mvn_cmnd}  ${MVN_OPTS}
	 fi

	 # Add a short sleep to ensure all files are written
	 sleep 15

	 # Reset pom.xml
	 echo "Restoring pom.xml to its original state"
	 python3 "${insert_jvm_args_in_pom_path}" --dir "${project_root_dir}" --restore || { echo "Failed to restore pom.xml"; exit 1; }

	 # Copy listener data
	 echo "Copying listener logs"
	 # echo "Contents of method-data.csv:"
	 # cat /home/listener/output/logs/csv/method-data.csv for some reason this fixes the cp command ????
	 
	 sync

	 cp -r /home/listener/output/logs "$dest"
     cp gc.log "$dest"

	 # Copy surefire reports
	 echo "Copying surefire reports"
	 cp -r "target/surefire-reports" "$dest"
}

# Do the fourth iteration for method profiling
if [ -n "$method_profile" ]; then
    echo "Starting method profiling run\n"
    
    MVN_OPTS="${MVN_OPTS} -DattachByteBuddy=${method_profile}"

    order_suffix="method_profile"

    echo -e "Method Profiling"
    run_method_profile_tests "$order_suffix"
fi

date
