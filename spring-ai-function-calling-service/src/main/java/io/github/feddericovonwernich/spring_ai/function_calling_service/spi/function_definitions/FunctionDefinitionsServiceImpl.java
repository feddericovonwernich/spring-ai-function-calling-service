package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.function_definitions;

import io.github.feddericovonwernich.spring_ai.function_calling_service.ParameterClassUtils;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.ParameterClass;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.aop.SpringProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.*;

@Component
@Slf4j
public class FunctionDefinitionsServiceImpl implements FunctionDefinitionsService {

    private final ApplicationContext appContext;

    private final Map<String, FunctionDefinition> functionDefinitions = new HashMap<>(); // TODO Concurrent hashmap?
    private final List<Class<?>> toolProviders = new ArrayList<>();
    private final Set<Class<?>> parameterClasses;

    @Value("#{'${assistant.scan.packages:}'.split(',')}")
    private final List<String> scanPackages = new ArrayList<>();

    public FunctionDefinitionsServiceImpl(ApplicationContext appContext) {
        this.appContext = appContext;
        this.parameterClasses = scanForAnnotatedClasses(scanPackages, ParameterClass.class);
    }

    @PostConstruct
    public void init() {
        // First we need to get the beans that are carrying tools.
        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);

        // Calculate function definitions map.
        for (Object bean : beans.values()) {

            Class<?> beanClass = getBeanClass(bean);

            if (beanClass.getAnnotation(AssistantToolProvider.class) == null) {
                throw new IllegalArgumentException("class must be annotated with AssistantToolProvider");
            }

            toolProviders.add(beanClass);

            Method[] methods = beanClass.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isSynthetic()) continue;

                FunctionDefinition functionDefinition = method.getDeclaredAnnotation(FunctionDefinition.class);
                if (functionDefinition != null) {
                    functionDefinitions.put(getFunctionName(method), functionDefinition);
                }
            }
        }
    }

    private static String getFunctionName(Method method) {
        return method.getDeclaringClass().getSimpleName() + "_" + method.getName();
    }

    @Override
    public Set<String> getSystemFunctionsNames() {
        return functionDefinitions.keySet();
    }

    // TODO OWL_TODO Im not sure why there are no function definitions registered, but should be looking into that.

    @Override
    public String getParametersDefinition(String operation) {
        FunctionDefinition functionDefinition = functionDefinitions.get(operation);

        if (functionDefinition == null) return null;

        if (functionDefinition.parameters() != null && !functionDefinition.parameters().isEmpty()) {
            return functionDefinition.parameters();
        } else if (functionDefinition.parameterClass() != null) {
            return ParameterClassUtils.getParameterClassString(functionDefinition.parameterClass());
        } else {
            throw new IllegalArgumentException(
                    String.format("FunctionDefinition %s should have either parameters or parameterClass set.", functionDefinition)
            );
        }
    }

    @Override
    public List<Class<?>> getToolProviders() {
        return toolProviders;
    }

    private Class<?> getBeanClass(Object bean) {
        Class<?> beanClass;
        if (isSpringProxy(bean.getClass())) {
            beanClass = bean.getClass().getSuperclass();
        } else {
            beanClass = bean.getClass();
        }
        return beanClass;
    }

    private boolean isSpringProxy(Class<?> bean) {
        for (AnnotatedType annotatedType : Arrays.stream(bean.getAnnotatedInterfaces()).toList()) {
            if (annotatedType.getType().equals(SpringProxy.class)) {
                return true;
            }
        }
        return false;
    }

    // TODO Could live in an utils class?
    private Set<Class<?>> scanForAnnotatedClasses(List<String> packageNames, Class<? extends Annotation> annotation) {
        Set<Class<?>> allAnnotatedClasses = new HashSet<>();

        for (String packageName : packageNames) {
            log.debug("Scanning packageName: " + packageName);
            Reflections reflections = new Reflections(
                    new ConfigurationBuilder().forPackage(packageName).addScanners(Scanners.TypesAnnotated)
            );
            Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(annotation);
            log.debug("Found annotated classes: " + annotatedClasses);
            allAnnotatedClasses.addAll(annotatedClasses);
        }

        // Iterate over the set and process each class with ParameterClassUtils.getParameterClassString(clazz)
        Iterator<Class<?>> iterator = allAnnotatedClasses.iterator();
        while (iterator.hasNext()) {
            Class<?> clazz = iterator.next();
            try {
                // Attempt to generate the parameter class string
                String parameterClassString = ParameterClassUtils.getParameterClassString(clazz);
                log.debug("Generated parameter class string for class: " + clazz.getName());
            } catch (StackOverflowError e) {
                // Log the error and remove the class from the set
                log.error("StackOverflowError encountered for class: " + clazz.getName() + ". Removing from the set.");
                iterator.remove();
            }
        }

        return allAnnotatedClasses;
    }

}
