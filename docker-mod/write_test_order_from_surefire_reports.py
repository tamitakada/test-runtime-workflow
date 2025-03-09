import os, sys, re
import xml.etree.ElementTree as ET

project_root = sys.argv[1]
out_dir = sys.argv[2]

def remove_brackets_and_parentheses(s):
    # Remove text inside square brackets
    s = re.sub(r'\[.*?\]', '', s)
    # Remove text inside parentheses
    s = re.sub(r'\(.*?\)', '', s)
    return s

def get_test_methods(xml_file):
    tree = ET.parse(xml_file)
    root = tree.getroot()
    test_methods = []

    for testcase in root.findall('testcase'):
        class_name = testcase.attrib['classname']
        method_name = testcase.attrib['name']
        method_name = remove_brackets_and_parentheses(method_name)
        test_method = f"{class_name}#{method_name}"
        if test_method not in test_methods:  # Ensure uniqueness
            test_methods.append(test_method)

    return test_methods

def get_test_methods_from_surefire_reports(surefire_reports_dir):
    test_methods = []
    for filename in sorted(os.listdir(surefire_reports_dir)):
        if filename.endswith('.xml'):
            xml_file = os.path.join(surefire_reports_dir, filename)
            test_methods.extend(get_test_methods(xml_file))
    return test_methods

def write_order_to_file(order, output_path):
    with open(output_path, 'w') as file:
        file.writelines([item + '\n' for item in order])


surefire_reports_dir = os.path.join(project_root, 'target/surefire-reports')
test_methods = get_test_methods_from_surefire_reports(surefire_reports_dir)
print("len(test_methods) =", len(test_methods))
# write order file
out_file = os.path.join(out_dir, f'original-order') # some order to start with
write_order_to_file(test_methods, out_file)
