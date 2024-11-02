package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.parameter_classes.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Fields annotated with this, shouldn't have their whole definition resolved. It should just show their field
 * annotated with @ReferenceField
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Reference {
}
