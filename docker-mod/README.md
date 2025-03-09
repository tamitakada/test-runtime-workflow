## Available Options

| Option | Description | Required? | Default | 
| - | - | - | - |
| `-p \| --projfile` | Path to CSV file listing projects to profile with each line following `url,sha,module` (where module is optional) | Y | N/A |
| `-c \| --class` | Test class to profile | N | N/A |
| `-jc \| --jfc-config` | Path to JFC configuration file; must prepend path with `/Scratch/` | N | N/A |
| `-r \| --random` | Run trials for 3 unique orders | N | N/A |
| `-co \| --custom-order` | Seed to run custom order | N | N/A |
| `-mp \| --method-profile` | Comma separated list of methods to profile or `all` for all methods | N | N/A |
| `-cc \| --custom-commands` | Executable to run before install and verify/test | N | N/A |
| `-v \| --ver` | Specify Java version number to use | N | 11 |
| `-mvnw` | Run with project's copy of Maven instead of global | N | N/A |
| `-mvnv` | Run integration tests instead of unit tests | N | N/A |

## Running Integration Tests

1. Provide additional commands in some executable. For instance, create a `setup.sh` with:
```setup.sh
find . -name 'pom.xml' -print0 | xargs -0 -I {} xmlstarlet ed -L \
    -N ns="http://maven.apache.org/POM/4.0.0" \
    -s "/ns:project/ns:build/ns:pluginManagement/ns:plugins/ns:plugin[ns:artifactId='maven-surefire-plugin'][not(ns:configuration)]" -t elem -n "configuration" \
    -s "/ns:project/ns:build/ns:pluginManagement/ns:plugins/ns:plugin[ns:artifactId='maven-surefire-plugin']/ns:configuration[not(ns:skipTests)]" -t elem -n "skipTests" -v "\${skipSurefireTests}" \
    -s "/ns:project/ns:build/ns:plugins/ns:plugin[ns:artifactId='maven-surefire-plugin'][not(ns:configuration)]" -t elem -n "configuration" \
    -s "/ns:project/ns:build/ns:plugins/ns:plugin[ns:artifactId='maven-surefire-plugin']/ns:configuration[not(ns:skipTests)]" -t elem -n "skipTests" -v "\${skipSurefireTests}" \
    {}
```

2. Use `-cc` to provide the executable, `-mvnv` to use `verify` instead of `test`, and optionally provide a Java version number with `-v`. For example, using the previous `setup.sh`:

```
./create_and_run_dockers.sh -p <projfile> -cc setup.sh -mvnv -v 17
```