package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.parameter_classes.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Should be only one @ReferenceField annotation per class annotated with @ParameterClass. This field will be used when the
 * containing type is used on another class annotated with @ParameterClass. If that field is annotated with @Reference,
 * then only the field annotated with @ReferenceField will be used when generating the JSON definition.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface  ReferenceField {
}
