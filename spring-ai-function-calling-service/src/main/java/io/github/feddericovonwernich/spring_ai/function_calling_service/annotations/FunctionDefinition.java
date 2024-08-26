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
     *
     * @return The name of the function.
     */
    String name() default "unset";

    /**
     * A description of the function's purpose.
     *
     * @return The function description.
     */
    String description();

    /**
     * A description of the function's parameters.
     * Has priority over parameterClass
     *
     * @return A string detailing the parameters of the function.
     */
    String parameters() default "";

    /**
     * A class that is annotated with @ParameterClass annotation.
     *
     * @return A class
     */
    Class<?> parameterClass() default Void.class;

    // TODO Need to implement tests that test this functionality.
}
