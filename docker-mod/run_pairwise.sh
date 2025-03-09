SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
SCRIPT_USERNAME="runtimeprofiler"

image="rp-11-imaging"

# Create base Docker image if it does not exist
docker inspect $image > /dev/null 2>&1
if [ $? == 1 ]; then
    repo_link="https://github.com/apache/commons-imaging.git"
    repo_name="commons-imaging"
    sed -i '.bak' -e "s|REPO_LINK|$repo_link|g" -e "s|REPO_NAME|$repo_name|g" ProjectDockerFile
    docker build -t $image -f ProjectDockerFile .
    rm ProjectDockerFile
    mv ProjectDockerFile.bak ProjectDockerFile
fi

test_classes=()
while read line; do
   # tc=$(echo "$line")
   tc=$(echo "$line" | cut -d ',' -f1)
   if [ "$tc" != "Name" ]; then
       test_classes+=("$tc")
   fi
done < "$1"

# echo "$2" > ord.txt

# docker run -t --name "imaging_runtimeprofiler_container" -v ${SCRIPT_DIR}:/Scratch ${image} /bin/bash -x /Scratch/run_pair.sh "$2" /Scratch/ord.txt

# docker wait imaging_runtimeprofiler_container

# mkdir -p results-java-cpy/commons-imaging.git/baseline/$2

# mv results-java/commons-imaging.git/* results-java-cpy/commons-imaging.git/baseline/$2

# docker rm imaging_runtimeprofiler_container

# test_classes=()
# while read line; do
#    tc=$(echo "$line")
#    if [ "$tc" != "Name" ]; then
#        echo "$tc\n$2" > ord.txt
       
#        docker run -t --name "imaging_runtimeprofiler_container" -v ${SCRIPT_DIR}:/Scratch ${image} /bin/bash -x /Scratch/run_pair.sh "$2,$tc" /Scratch/ord.txt
       
#        docker wait imaging_runtimeprofiler_container
       
#        mkdir -p results-java-cpy/commons-imaging.git/pairwise/$2/$tc
       
#        mv results-java/commons-imaging.git/* results-java-cpy/commons-imaging.git/pairwise/$2/$tc
#        mv ord.txt results-java-cpy/commons-imaging.git/pairwise/$2/$tc
       
#        docker rm imaging_runtimeprofiler_container
#    fi
# done < "$1"

length=${#test_classes[@]}
for (( i=$length-1; i>=($length-5); i-- )); do
    tc1=${test_classes[$i]}
    mkdir -p results-java-cpy/commons-imaging.git/$tc1

    echo "$tc1\n" > ord.txt

    docker run -t --name "imaging_runtimeprofiler_container" -v ${SCRIPT_DIR}:/Scratch ${image} /bin/bash -x /Scratch/run_pair.sh "$tc1" /Scratch/ord.txt

    docker wait imaging_runtimeprofiler_container

    mkdir -p results-java-cpy/commons-imaging.git/$tc1/none

    mv results-java/commons-imaging.git/* results-java-cpy/commons-imaging.git/$tc1/none

    docker rm imaging_runtimeprofiler_container

    for (( j=$length-1; j>=0; j-- )); do
        if [ "$i" != "$j" ]; then
            tc2=${test_classes[$j]}

            echo "$tc2\n$tc1" > ord.txt
            
            docker run -t --name "imaging_runtimeprofiler_container" -v ${SCRIPT_DIR}:/Scratch ${image} /bin/bash -x /Scratch/run_pair.sh "$tc1,$tc2" /Scratch/ord.txt
            
            docker wait imaging_runtimeprofiler_container
            
            mkdir -p results-java-cpy/commons-imaging.git/$tc1/$tc2
            
            mv results-java/commons-imaging.git/* results-java-cpy/commons-imaging.git/$tc1/$tc2
            mv ord.txt results-java-cpy/commons-imaging.git/$tc1/$tc2
            
            docker rm imaging_runtimeprofiler_container
        fi
    done
done
