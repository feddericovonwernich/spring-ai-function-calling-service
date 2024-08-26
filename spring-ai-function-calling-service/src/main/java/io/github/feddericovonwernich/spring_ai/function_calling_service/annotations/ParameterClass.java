package io.github.feddericovonwernich.spring_ai.function_calling_service.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Classes annotated with this, will be eligible for JSON definition generation.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterClass {
}
