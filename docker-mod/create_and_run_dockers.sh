#!/bin/bash

date

projfile=""
test_class=""
jfc_config="default"  # Default to "default.jfc" if -config is not provided
random=""
custom_order=""
method_profile=""
custom_cmnds=""
java_ver="11"
mvn_type="/home/runtimeprofiler/apache-maven/bin/mvn"
mvn_cmnd="test"

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--projfile)
            projfile=$(realpath -e "$2")
            shift 2
            ;;
        -c|--class)
            test_class="$2"
            shift 2
            ;;
        -jc|--jfc-config)
            jfc_config="$2"
            shift 2
            ;;
        -r|--random)
            random="True"
            shift 1
            ;;
        -co|--custom-order)
            custom_order="$2"
            shift 2
            ;;
        -cc|--custom-commands)
            custom_cmnds="$2"
            shift 2
            ;;
        -mp|--method-profile)
            method_profile="$2"
            shift 2
            ;;
        -v|--ver)
            java_ver="$2"
            shift 2
            ;;
        -mvnw)
            mvn_type="mvnw"
            shift 1
            ;;
        -mvnv)
            mvn_cmnd="verify"
            shift 1
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check if -p option (projfile) is provided
if [[ -z "$projfile" ]]; then
    echo "You must provide the -p|--projfile option."
    exit 1
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
SCRIPT_USERNAME="runtimeprofiler"

echo "*******************runtimeprofiler DEBUG************************"
echo "Making base image"
date

# Create base Docker image if it does not exist
docker inspect runtimeprofiler-$java_ver:latest > /dev/null 2>&1
if [ $? == 1 ]; then
    sed -i.bak -e "s/JAVA_VER/$java_ver/g" baseDockerFile
    docker build -t runtimeprofiler-$java_ver:latest -f baseDockerFile .
    rm baseDockerFile
    mv baseDockerFile.bak baseDockerFile
fi

image="runtimeprofiler-$java_ver:latest"

process_project() {
    local url="$1"
    local sha="$2"
    local module="$3"
    echo "About to run: $url, $sha, $module"
    slug=$(echo ${url} | rev | cut -d'/' -f1-2 | rev)
    safe_project=$(echo ${slug} | sed 's;/;_;g')
    if [ -n "$module" ]; then
        safe_module=$(echo ${module} | sed 's;/;_;g')
    	safe_project="$safe_project-$safe_module"
    fi
    
    docker run -t --name "${safe_project}_runtimeprofiler_container" -v ${SCRIPT_DIR}:/Scratch ${image} /bin/bash -x /Scratch/run_experiment.sh "$url" "$sha" "$module" "$test_class" "$jfc_config" "$mvn_type" "$mvn_cmnd" "$random" "$custom_order" "$method_profile" "$custom_cmnds"
    
    ## use the line below if we want to limit the cpu and mempry
    # docker run -t --name "${safe_project}_runtimeprofiler_container" -v ${SCRIPT_DIR}:/Scratch -m=8g --cpus=2 ${image} /bin/bash -x /Scratch/run_experiment.sh "$url" "$sha" "$module" "$test_class"
}

# Number of concurrent containers
max_concurrent=3

while IFS=, read -r url sha module; do
    # Check if the maximum number of concurrent containers has been reached
    running_containers=$(docker ps -q | wc -l)
    while [ $running_containers -ge $max_concurrent ]; do
        sleep 10 # Wait for a few seconds before checking again
        running_containers=$(docker ps -q | wc -l)
    done
    
    process_project "${url}" "${sha}" "${module}" &
    sleep 5 # Wait for a few seconds to allow the system to register the new container
done < "${projfile}"

date
