package io.github.feddericovonwernich.spring_ai.function_calling_service;

import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FieldDescription;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.Ignore;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.ParameterClass;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.Reference;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.ReferenceField;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.RequiredField;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ParameterClassUtilsTest {

    public enum TestEnum {
        VALUEONE, VALUETWO
    }

    @Getter
    @Setter
    @ParameterClass
    public static class MainClass {

        @RequiredField
        @FieldDescription(description = "The main reference to another complex type")
        private NestedClass nestedClass;

        @FieldDescription(description = "A nested object that's not required.")
        private NestedClass notRequiredNestedClass;

        @Reference
        @FieldDescription(description = "A nested object that's a reference.")
        private NestedClass referenceNestedClass;

        @Ignore
        private int ignoredField;

        @FieldDescription(description = "A descriptive string field")
        private String description;

        @FieldDescription(description = "An example numeric field")
        private int exampleNumber;

        @FieldDescription(description = "A list of strings")
        private List<String> stringList;

        @FieldDescription(description = "Enum test.")
        private TestEnum enumReference;

        @FieldDescription(description = "Enum test required.")
        @RequiredField
        private TestEnum requiredEnum;

    }

    @Getter
    @Setter
    @ParameterClass
    public static class NestedClass {

        @RequiredField
        @ReferenceField
        @FieldDescription(description = "Simple identification field")
        private String id;

        @Reference
        @FieldDescription(description = "A list of SecondNestedClass instances")
        // TODO This list is annotated with reference, and definition has the full definition.
        private List<SecondNestedClass> secondNestedClassListReference;

        @FieldDescription(description = "A list of SecondNestedClass instances")
        private List<SecondNestedClass> secondNestedClassList;

    }

    @Getter
    @Setter
    @ParameterClass
    public static class SecondNestedClass {

        @RequiredField
        @ReferenceField
        @FieldDescription(description = "Simple identification field")
        private String id;

        @FieldDescription(description = "A simple counter to test references.")
        private int counter;

    }

    private final static String MAIN_CLASS_JSON_STRING = "{\"type\":\"object\",\"properties\":{\"MainClass\":{\"type\":\"object\",\"properties\":{\"nestedClass\":{\"$ref\":\"#/definitions/NestedClass\"},\"enumReference\":{\"type\":[\"string\",\"null\"],\"description\":\"Enum test.\",\"enum\":[\"VALUEONE\",\"VALUETWO\"]},\"exampleNumber\":{\"type\":[\"number\",\"null\"],\"description\":\"An example numeric field\"},\"requiredEnum\":{\"type\":\"string\",\"description\":\"Enum test required.\",\"enum\":[\"VALUEONE\",\"VALUETWO\"]},\"stringList\":{\"type\":\"array\",\"description\":\"A list of strings\",\"items\":{\"type\":\"string\"}},\"notRequiredNestedClass\":{\"$ref\":\"#/definitions/NestedClassNotRequired\"},\"description\":{\"type\":[\"string\",\"null\"],\"description\":\"A descriptive string field\"},\"referenceNestedClass\":{\"type\":[\"object\",\"null\"],\"description\":\"A nested object that\\u0027s a reference.\",\"properties\":{\"id\":{\"type\":\"string\",\"description\":\"Simple identification field\"}},\"required\":[\"id\"],\"additionalProperties\":false}},\"required\":[\"nestedClass\",\"enumReference\",\"exampleNumber\",\"requiredEnum\",\"stringList\",\"notRequiredNestedClass\",\"description\",\"referenceNestedClass\"],\"additionalProperties\":false}},\"required\":[\"MainClass\"],\"additionalProperties\":false,\"definitions\":{\"NestedClass\":{\"type\":\"object\",\"properties\":{\"secondNestedClassListReference\":{\"type\":\"array\",\"description\":\"A list of SecondNestedClass instances\",\"items\":{\"$ref\":\"#/definitions/SecondNestedClass\"}},\"id\":{\"type\":\"string\",\"description\":\"Simple identification field\"},\"secondNestedClassList\":{\"type\":\"array\",\"description\":\"A list of SecondNestedClass instances\",\"items\":{\"$ref\":\"#/definitions/SecondNestedClass\"}}},\"required\":[\"secondNestedClassListReference\",\"id\",\"secondNestedClassList\"],\"additionalProperties\":false},\"NestedClassNotRequired\":{\"anyOf\":[{\"type\":\"null\"},{\"type\":\"object\",\"properties\":{\"secondNestedClassListReference\":{\"type\":\"array\",\"description\":\"A list of SecondNestedClass instances\",\"items\":{\"$ref\":\"#/definitions/SecondNestedClass\"}},\"id\":{\"type\":\"string\",\"description\":\"Simple identification field\"},\"secondNestedClassList\":{\"type\":\"array\",\"description\":\"A list of SecondNestedClass instances\",\"items\":{\"$ref\":\"#/definitions/SecondNestedClass\"}}},\"required\":[\"secondNestedClassListReference\",\"id\",\"secondNestedClassList\"],\"additionalProperties\":false}]},\"SecondNestedClass\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\",\"description\":\"Simple identification field\"},\"counter\":{\"type\":[\"number\",\"null\"],\"description\":\"A simple counter to test references.\"}},\"required\":[\"id\",\"counter\"],\"additionalProperties\":false}}}";

    @Test
    public void getParameterClassStringTest() {
        String stringRepresentation = ParameterClassUtils.getParameterClassString(MainClass.class);
        Assertions.assertEquals(MAIN_CLASS_JSON_STRING, stringRepresentation);
    }

}