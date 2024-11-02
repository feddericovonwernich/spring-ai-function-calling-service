package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.parameter_classes.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Fields annotated with this will be ignored from resolution when generating JSON definition.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Ignore {
}
