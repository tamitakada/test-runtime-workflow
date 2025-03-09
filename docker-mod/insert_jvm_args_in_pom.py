#!/usr/bin/python3

import os
import sys
import argparse


def parseArgs(argv):
    '''
    Parse the args of the script.
    '''
    parser = argparse.ArgumentParser()
    parser.add_argument('--dir', help='Change all the pom files in the dir', required=False)
    parser.add_argument('--args', help='The args to be inserted', required=False)
    parser.add_argument('--deps', help='The deps to be inserted', required=False)
    parser.add_argument('--junit4listener', help='Flag to add surefire plugin listener configuration', action='store_true', required=False)
    parser.add_argument('--restore', help='Flag to restore poms to original state', action='store_true', required=False)
    if len(argv) == 0:
        parser.print_help()
        exit(1)
    opts = parser.parse_args(argv)
    return opts


def findAllPomsInDir(target_dir):
    poms = []
    for dir_path, _, files in os.walk(target_dir):
        for f in files:
            if f == 'pom.xml':
                poms.append(dir_path + '/' + f)
    return poms


def restoreAllPomsToOriginalState(target_dir):
    cwd = os.getcwd()
    os.chdir(target_dir)
    os.system("git checkout -- .")
    os.chdir(cwd)

def addSurefireListener(lines):
    listener_config = """<properties>
                    <property>
                        <name>listener</name>
                        <value>customlistener.JUnit4Listener</value>
                    </property>
                </properties>"""

    build_start = -1
    surefire_start = -1
    config_start = -1
    project_end = -1

    for i, line in enumerate(lines):
        if "<build>" in line:
            build_start = i
        if (
            "<artifactId>maven-surefire-plugin</artifactId>" in line
            and build_start != -1
        ):
            surefire_start = i
        if (
            "<version>" in line
            and surefire_start != -1
            and i > surefire_start
        ):
            surefire_start = i  # Update to include the <version> line
        if (
            "<configuration>" in line
            and surefire_start != -1
            and i > surefire_start
        ):
            config_start = i
            break
        if "</project>" in line:
            project_end = i

    if build_start == -1:
        # Add <build> with plugins and the surefire plugin configuration before </project>
        new_build = f"""  <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        {listener_config}
                    </configuration>
                </plugin>
            </plugins>
        </build>
        """
        lines.insert(project_end, new_build)
    elif surefire_start == -1:
        # Add surefire plugin to the existing <build>
        new_plugin = f"""    <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    {listener_config}
                </configuration>
            </plugin>
        """
        # Find the end of the <build> section
        build_end = next(
            (i for i, line in enumerate(lines[build_start:], start=build_start) if "</build>" in line), -1)
        if build_end != -1:
            lines.insert(build_end, new_plugin)
    elif config_start == -1:
        # Add <configuration> to existing surefire plugin
        lines.insert(
            surefire_start + 1,
            f"""    <configuration>
            {listener_config}
        </configuration>\n""",
        )
    else:
        # Add listener to existing configuration
        lines.insert(
            config_start + 1,
            f"""                {listener_config}\n""",
        )

    return lines


def insertInOnePom(args, deps, pom, add_listener=False):
    with open(pom, 'r') as fr:
        lines = fr.readlines()

    addedDeps = False
    for i in range(len(lines)):
        if '</argLine>' in lines[i]:
            lines[i] = lines[i].replace('</argLine>', ' ' + args + '</argLine>')
        elif not addedDeps and '</dependencies>' in lines[i]:
            lines[i] = lines[i].replace(
                '</dependencies>',
                ''.join(['<dependency>' + dep + '</dependency>' for dep in deps]) + '</dependencies>'
            )
            addedDeps = True

    if add_listener:
        lines = addSurefireListener(lines)

    with open(pom, 'w') as fw:
        fw.write(''.join(lines))


def insertInAllPoms(args, deps, target_dir, add_listener=False):
    poms = findAllPomsInDir(target_dir)
    for pom in poms:
        insertInOnePom(args, deps, pom, add_listener)


if __name__ == '__main__':
    opts = parseArgs(sys.argv[1:])
    if opts.dir:
        target_dir = opts.dir
    else:
        exit(0)
    if opts.restore:
        restoreAllPomsToOriginalState(target_dir)
    else:
        args = []
        if opts.args:
            args = opts.args

        deps = []
        if opts.deps:
            deps = opts.deps
            if type(deps) != list:
                deps = [deps]

        add_junit_4_listener = False
        if opts.junit4listener:
            add_junit_4_listener = True 

        if not opts.args and not opts.deps and not opts.junit4listener:
            exit(0)

        insertInAllPoms(args, deps, target_dir, add_junit_4_listener)
