package parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;


public class Utils {
    public static File findFile(File dir, String name) throws FileNotFoundException {
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().equals(name)) return file;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                try { 
                    return findFile(file, name);
                } catch (FileNotFoundException e) {
                    continue;
                }
            }
        }

        throw new FileNotFoundException();
    }

    public static File findFileWith(File dir, String contains) {
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().contains(contains)) return file;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                File f = findFileWith(file, contains);
                if (f != null) return f;
            }
        }

        return null;
    }

    public static void writeResults(String destDir, String fileName, String data) {
        (new File(destDir)).mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(destDir, fileName).toString()))) {
            writer.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, ArrayList<TestClassData>> mapTestClassData(TestClassData[][][] orders) {
        HashMap<String, ArrayList<TestClassData>> testClasses = new HashMap<>();
        Arrays.stream(orders)
            .forEach((order) -> Arrays.stream(order)
                .forEach((trial) -> Arrays.stream(trial)
                    .forEach((tc) -> {
                        testClasses.putIfAbsent(tc.getName(), new ArrayList<>());
                        testClasses.get(tc.getName()).add(tc);
                    })
                )
            );
        return testClasses;
    }

    public static HashMap<String, Integer> mapTestClassOrder(TestClassData[][] order) {
        final HashMap<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < order[0].length; i++)
            indexMap.put(order[0][i].getName(), i);
        return indexMap;
    }

    public static int[] getTestClassOrder(HashMap<String, Integer> indexMap, TestClassData[][] order) {
        return IntStream.range(0, order[0].length)
            .boxed()
            .sorted((i1, i2) -> indexMap.get(order[0][i1].getName()).compareTo(indexMap.get(order[0][i2].getName())))
            .mapToInt(Integer::intValue)
            .toArray();
    }
}