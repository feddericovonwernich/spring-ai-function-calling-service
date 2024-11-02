package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.parameter_classes.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation should be present in all fields of a class annotated with @ParameterClass, should contain the
 * semantic description of the field it annotates.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldDescription {

    /**
     * A description of the field's purpose.
     *
     * @return The field description.
     */
    String description();

}
