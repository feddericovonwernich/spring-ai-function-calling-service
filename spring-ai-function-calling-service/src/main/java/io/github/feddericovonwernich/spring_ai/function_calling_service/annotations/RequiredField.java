package io.github.feddericovonwernich.spring_ai.function_calling_service.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Fields annotated with this, will be on required list when generating JSON definition.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredField {
}
