package io.github.feddericovonwernich.spring_ai.function_calling_service.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to mark classes that should be scanned for {@link FunctionDefinition} annotations.
 * <p>
 * This annotation serves as a discovery mechanism for classes that define functions,
 * facilitating the automatic detection and registration of functions within the application.
 * Classes annotated with {@code AssistantToolProvider} will be considered during the scanning process
 * to locate {@link FunctionDefinition} annotations, which specify the details of each function, including
 * its name, description, and parameters.
 * </p>
 *
 * @see FunctionDefinition for details about the function definitions.
 *
 * @author Federico von Wernich
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AssistantToolProvider {
}