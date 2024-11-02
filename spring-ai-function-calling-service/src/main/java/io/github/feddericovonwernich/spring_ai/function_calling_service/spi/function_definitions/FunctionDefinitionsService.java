package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.function_definitions;

import java.util.List;
import java.util.Set;

public interface FunctionDefinitionsService {

    Set<String> getSystemFunctionsNames();

    String getParametersDefinition(String operation);

    String getParametersDefinition(String operation, Boolean onlyAnnotatedFieldsAsRequired);

    List<Class<?>> getToolProviders();
}
