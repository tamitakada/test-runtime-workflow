src=""
dest="."
test_class=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--dest)
            dest="$2"
            shift 2
            ;;
        -c|--class)
            test_class="$2"
            shift 2
            ;;
        *)
            if [ "$src" = "" ]; then
                src="$1"
                shift 1
            else
                echo "Unknown option: $1"
                exit 1
            fi
            ;;
    esac
done

if [ "$src" = "" ] || [ ! -d "$src" ]; then
    echo "Usage: ./run.sh <profiling_results_path> [-d <destination_dir_path>]"
    exit 1
else
    mkdir -p $dest
    mkdir -p $dest/all
    mkdir -p $dest/html

    cd parser
    mvn clean install

    echo "Begin running parser"

    csv_name=""
    if [ "$test_class" = "" ]; then
        csv_name="test_suite_summary"
        mvn exec:java -Dexec.args="../$src ../$dest"
    else
        csv_name="test_methods_summary"
        mvn exec:java -Dexec.args="../$src ../$dest $test_class"
    fi

    # echo "Finished running parser"

    cd ..
    . venv/bin/activate

    # cd postprocess
    # python3 stat_analysis.py ../$dest

    # echo "Run outlier detector"

    # cd ..
    # ./run_outlier_detector.sh $dest $csv_name
fi
