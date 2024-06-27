package io.github.feddericovonwernich.spring_ai.function_calling_service.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents the definition of a functional interface.
 * This annotation is used to provide metadata for function definitions.
 *
 * @author Federico von Wernich
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface FunctionDefinition {

    /**
     * The name of the function.
     * @return The name of the function.
     */
    String name();

    /**
     * A description of the function's purpose.
     * @return The function description.
     */
    String description();

    /**
     * A description of the function's parameters.
     * @return A string detailing the parameters of the function.
     */
    String parameters();
}
