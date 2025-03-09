import os
import sys

def get_default_package_name(project_dir):
    src_path = os.path.join(project_dir, 'src')
    package_str = None

    for dirpath, _, _ in os.walk(src_path):
        if 'main/java' in dirpath:
            if len([file for file in os.listdir(dirpath)]) > 1 and any(w in dirpath for w in ['org', 'net']):
                package_str = dirpath
                break

    if package_str:
        package_str_arr = package_str.split(os.sep)
        default_package_name = '.'.join(package_str_arr[package_str_arr.index('java') + 1:])
        return default_package_name
    else:
        return None

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python get_default_package_name.py <project_root_dir>")
        sys.exit(1)

    project_root_dir = sys.argv[1]
    package_name = get_default_package_name(project_root_dir)

    if package_name:
        print(package_name)
    else:
        print("No default package name found.")
        sys.exit(1)
