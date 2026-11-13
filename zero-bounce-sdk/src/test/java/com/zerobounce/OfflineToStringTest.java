package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every response model's {@code toString} names its own type and includes every field.
 * <p>
 * The models are found by scanning the {@code com.zerobounce} package, so a new
 * response type, or a new field on an existing one, is covered without editing this test.
 */
class OfflineToStringTest {

    /** Response models: the classes that come back from the API. */
    private static boolean isModel(Class<?> type) {
        String name = type.getSimpleName();
        return Modifier.isPublic(type.getModifiers())
                && !type.isInterface() && !type.isEnum() && type.getEnclosingClass() == null
                && (name.endsWith("Response") || name.equals("ZBS3ListFileEntry") || name.equals("DomainFormat"));
    }

    private static List<Class<?>> models() throws Exception {
        URL root = OfflineToStringTest.class.getClassLoader().getResource("com/zerobounce/ZeroBounceSDK.class");
        assertNotNull(root);
        File dir = new File(root.toURI()).getParentFile();
        List<Class<?>> models = new ArrayList<>();
        TreeSet<String> names = new TreeSet<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
        assertNotNull(files);
        for (File file : files) {
            names.add(file.getName().replace(".class", ""));
        }
        for (String name : names) {
            Class<?> type = Class.forName("com.zerobounce." + name);
            if (isModel(type)) {
                models.add(type);
            }
        }
        return models;
    }

    private static Object instance(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void everyResponseModelToStringNamesTheTypeAndEveryField() throws Exception {
        List<Class<?>> models = models();
        assertTrue(models.size() >= 15, "found only " + models);
        List<String> failures = new ArrayList<>();
        int number = 1000;
        for (Class<?> type : models) {
            Object target = instance(type);
            List<String[]> expected = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                String value = null;
                Class<?> fieldType = field.getType();
                if (fieldType == String.class) {
                    value = "v-" + field.getName();
                    field.set(target, value);
                } else if (fieldType == int.class || fieldType == Integer.class) {
                    value = String.valueOf(++number);
                    field.set(target, Integer.valueOf(number));
                } else if (fieldType == long.class || fieldType == Long.class) {
                    value = String.valueOf(++number);
                    field.set(target, Long.valueOf(number));
                } else if (fieldType == double.class || fieldType == Double.class) {
                    value = String.valueOf((double) ++number);
                    field.set(target, Double.valueOf(number));
                }
                expected.add(new String[]{field.getName(), value});
            }
            String text;
            try {
                text = target.toString();
            } catch (RuntimeException e) {
                failures.add(type.getSimpleName() + ".toString() threw " + e);
                continue;
            }
            if (!text.startsWith(type.getSimpleName() + "{")) {
                failures.add(type.getSimpleName() + " names itself wrongly: " + text);
            }
            for (String[] field : expected) {
                if (!text.contains(field[0] + "=")) {
                    failures.add(type.getSimpleName() + " leaves out " + field[0] + ": " + text);
                } else if (field[1] != null && !text.contains(field[1])) {
                    failures.add(type.getSimpleName() + " leaves out the value of " + field[0] + ": " + text);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void everyResponseModelToStringToleratesUnsetFields() throws Exception {
        for (Class<?> type : models()) {
            String text = instance(type).toString();
            assertTrue(text.startsWith(type.getSimpleName() + "{"), text);
        }
    }

    /**
     * The clients print no key (check S6, standard.md section 3): {@code toString} of a
     * client holding the API key, and of a Warmup client holding the Warmup key, contains
     * neither key. The key fields are private and are read only by the request code.
     */
    @Test
    void theClientsPrintNoKey() throws Exception {
        String key = "tostring-api-key-7d21x";
        String warmupKey = "tostring-warmup-key-b4c9y";
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.initialize(key);
        ZeroBounceWarmup warmup = new ZeroBounceWarmup();
        warmup.initialize(warmupKey);
        for (Object client : List.of(sdk, warmup)) {
            String printed = client.toString();
            assertFalse(printed.contains(key) || printed.contains(warmupKey), printed);
        }
    }
}
