import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.MethodDescriptor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CustomOrderer implements ClassOrderer, MethodOrderer {

    private static final String ORDER_FILE_PATH = "src/test/resources/custom-order.txt";
    private List<String> orderList;

    public CustomOrderer() {
        try {
            orderList = Files.readAllLines(Paths.get(ORDER_FILE_PATH));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test order file", e);
        }
    }

    @Override
    public void orderClasses(ClassOrdererContext context) {
        List<String> classOrder = orderList.stream()
                .map(line -> line.split("#")[0])  // Extract class names only
                .distinct()  // Remove duplicates
                .collect(Collectors.toList()); 

        context.getClassDescriptors().sort(Comparator.comparingInt(descriptor ->
            classOrder.indexOf(descriptor.getTestClass().getName())
        ));
    }

    @Override
    public void orderMethods(MethodOrdererContext context) {
        context.getMethodDescriptors().sort(Comparator.comparingInt(descriptor -> 
            orderList.indexOf(descriptor.getMethod().getDeclaringClass().getName() + "#" + descriptor.getMethod().getName())
        ));
    }
}
