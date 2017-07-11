package com.testvagrant.intents.core;


import com.testvagrant.intents.Intent;
import com.testvagrant.intents.exceptions.NoMatchingStepFoundException;
import cucumber.api.DataTable;
import cucumber.api.java.en.*;
import cucumber.runtime.Utils;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * In Cucumber every step definition method can be identified by a unique pattern.
 * MethodExecutor reflects into all the classes with Cucumber annotations
 * and identifies a matching method for the step in an {@link Intent}
 * </p>
 */
public class MethodExecutor {

    private static Map<String, Method> patterns;
    private static boolean patternFound;
    private Reflections reflections;
    private Matcher matcher;
    private Optional<DataTable> dataTable;
    private static List<String> overRideData;
    private Optional<String> packageName;

    public MethodExecutor() {
        patterns = new HashMap<>();
        dataTable = Optional.empty();
        overRideData = new ArrayList<>();
    }

    public void setDataTable(Optional<DataTable> dataTable) {
        this.dataTable = dataTable;
        if(dataTable.isPresent()) {
            overRideData.addAll(dataTable.get().cells(0).get(0));
        }
    }

    public void setPackageName(Optional<String> packageName) {
        this.packageName = packageName;
    }

    public void exec(String name) throws NoMatchingStepFoundException {
        findPatterns().ifNotFound();
        String pattern = findMatchingPattern(name);
        Method method = patterns.get(pattern);
        List<String> args = getData();
        try {
            if(method.getParameterCount()>0) {
                Utils.invoke(method.getDeclaringClass().newInstance(), method, 200, args.toArray());
            }
            else {
                Utils.invoke(method.getDeclaringClass().newInstance(), method, 200);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(0);
        }
    }

    private synchronized MethodExecutor findPatterns() {
        if(!patternFound) {
            reflections = new Reflections(getPackageName(), new MethodAnnotationsScanner());
            Set<Method> allCucumberMethods = getAnnotatedMethods();
            allCucumberMethods.forEach(method -> {
                Arrays.stream(method.getDeclaredAnnotations()).forEach(annotation -> {
                    readPattern(method, annotation);
                });
            });
        }
        return this;
    }

    private Set<Method> getAnnotatedMethods() {
        Set<Method> allCucumberMethods = new HashSet<>();
        allCucumberMethods = reflections.getMethodsAnnotatedWith(Given.class);
        allCucumberMethods.addAll(reflections.getMethodsAnnotatedWith(When.class));
        allCucumberMethods.addAll(reflections.getMethodsAnnotatedWith(Then.class));
        allCucumberMethods.addAll(reflections.getMethodsAnnotatedWith(And.class));
        allCucumberMethods.addAll(reflections.getMethodsAnnotatedWith(But.class));
        return allCucumberMethods;
    }

    public synchronized void ifNotFound() {
        if(patterns.size()>0) {
            patternFound = true;
        }
    }

    private void readPattern(Method method, Annotation annotation) {
        if (annotation instanceof Given) {
            patterns.put(((Given) annotation).value(), method);
        } else if (annotation instanceof When) {
            patterns.put(((When) annotation).value(), method);
        } else if (annotation instanceof Then) {
            patterns.put(((Then) annotation).value(), method);
        } else if (annotation instanceof And) {
            patterns.put(((And) annotation).value(), method);
        } else if (annotation instanceof But) {
            patterns.put(((But) annotation).value(), method);
        }
    }


    private String findMatchingPattern(String name) throws NoMatchingStepFoundException {
        Optional<String> first = patterns.keySet().stream().filter(pattern -> {
            Pattern pattern1 = Pattern.compile(pattern);
            matcher = pattern1.matcher(name);
            return matcher.matches();
        }).findFirst();
        if(first.isPresent()) {
            return first.get();
        }
        throw new NoMatchingStepFoundException(name);
    }



    private List<String> getData() {
        List<String> args = new ArrayList<>();
        if(dataTable.isPresent()) {
            int matches = matcher.groupCount() - 1;
            if(matches>0) {
                for (int index = 0; index <= matches; index++) {
                    args.add(overRideData.get(index));
                }
                for (int index = 0; index <= matches; index++) {
                    overRideData.remove(0);
                }
            }
        } else {
            for (int groupIndex = 1; groupIndex <= matcher.groupCount(); groupIndex++) {
                args.add(matcher.group(groupIndex));
            }
        }
        return args;
    }

    private String getPackageName() {
       return packageName.orElse("steps");
    }
}
