package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import java.util.List;

/**
 * Provides a contract for tools that need to dynamically resolve parameters for function calls.
 * Implementors can customize the mechanism by which function parameters are determined from
 * a given string representation, facilitating the invocation of the function with those parameters.
 *
 * @author Federico von Wernich
 */
public interface ToolParameterAware {

    /**
     * Resolves and retrieves the parameters required for invoking a specific function,
     * given the function's name and a string representation of parameters.
     * Implementors must define the logic to parse the parametersString into a list of objects
     * that match the expected parameters of the function being called.
     *
     * @param functionName The name of the function for which parameters need to be resolved.
     * @param parametersString A string representation of the parameters expected by the function.
     *                         This could be in any format chosen by the implementor, such as JSON.
     * @return A List of Objects that represent the parameters to be passed to the function.
     */
    List<Object> getParametersForFunction(String functionName, String parametersString);

}
