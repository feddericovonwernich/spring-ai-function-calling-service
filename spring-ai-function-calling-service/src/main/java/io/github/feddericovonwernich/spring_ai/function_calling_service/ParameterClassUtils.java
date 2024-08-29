package io.github.feddericovonwernich.spring_ai.function_calling_service;

import com.google.gson.Gson;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

public class ParameterClassUtils {

    private static final Gson gson = new Gson();

    private static final Logger log = LoggerFactory.getLogger(ParameterClassUtils.class);

    public static String getParameterClassString(Class<?> clazz) {
        // This also validates that the JSON string is well formatted.
        Object json = gson.fromJson(getParameterClassStringInternal(clazz, ParameterClassResolution.MAIN, null), Object.class);
        return gson.toJson(json);
    }

    // TODO Move this out of here.
    public enum ParameterClassResolution {
        MAIN,
        INNER
    }

    static ThreadLocal<Map<String, String>> definitionsTL = new ThreadLocal<>();

    private static String getParameterClassStringInternal(Class<?> clazz, ParameterClassResolution parameterClassResolution, Boolean required) {
        if (clazz.getAnnotation(ParameterClass.class) == null) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " is not annotated with @ParameterClass");
        }

        if (parameterClassResolution.equals(ParameterClassResolution.MAIN)) {
            definitionsTL.set(new HashMap<>());
        }

        String clazzName = clazz.getSimpleName();

        // Get descriptions
        Map<String, String> fieldDescriptions = getFieldDescriptions(clazz);

        // Get required fields.
        List<String> requiredFieldsList = getRequiredFields(clazz);

        // This map is the source of truth for recognized fields.
        Map<String, String> fieldTypes = resolveFieldTypes(clazz);

        // Build required fields JSON
        StringJoiner requiredFields = new StringJoiner(", ");
        fieldTypes.keySet().forEach(key -> {
            requiredFields.add("\"" + key + "\"");
        });

        String propertiesJson = getPropertiesJson(fieldTypes, fieldDescriptions, clazz, requiredFieldsList);

        // TODO Do I really need these lines?
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ParameterClass.class)) {
                Class<?> fieldClass = field.getType();
                Boolean isRequired = requiredFieldsList.contains(field.getName());
                String fieldClassJson = getParameterClassStringInternal(fieldClass, ParameterClassResolution.INNER, isRequired);
                definitionsTL.get().put(fieldClass.getSimpleName(), fieldClassJson);
            }
        }

        // Convert the definitions map to a JSON string
        String definitionsJsonString = String.join(", ", definitionsTL.get().values());

        return switch (parameterClassResolution) {

            case INNER -> {

                String typeString;
                String typeName;
                if (required) {
                    typeString = String.format("""
                        "type": "object",
                        "properties": %s,
                        "required": [%s],
                        "additionalProperties": false
                    """, propertiesJson, requiredFields
                    );

                    typeName = clazzName;
                } else {
                    typeString = String.format(
                        """
                        "anyOf": [
                            {
                                "type": "null"
                            },
                            {
                                "type": "object",
                                "properties": %s,
                                "required": [%s],
                                "additionalProperties": false
                            }
                        ]
                        """, propertiesJson, requiredFields
                    );
                    typeName = clazzName + "NotRequired";
                }

                yield String.format(
                        """
                            "%s": {
                                %s
                            }
                        """,
                        typeName,
                        typeString
                );
            }

            case MAIN -> {
                definitionsTL.remove();
                yield String.format(
                        """
                            {
                                "type": "object",
                                "properties": {
                                    "%s": {
                                        "type": "object",
                                        "properties": %s,
                                        "required": [%s],
                                        "additionalProperties": false
                                    }
                                },
                                "required": ["%s"],
                                "additionalProperties": false,
                                "definitions": {
                                    %s
                                }
                            }
                        """,
                        clazzName,
                        propertiesJson,
                        requiredFields,
                        clazzName,
                        definitionsJsonString
                );
            }
        };
    }

    private static String getPropertiesJson(Map<String, String> fieldTypes, Map<String, String> fieldDescriptions,
                                            Class<?> parentClass, List<String> requiredFields) {
        StringBuilder propertiesBuilder = new StringBuilder();
        propertiesBuilder.append("{\n");
        StringJoiner propertiesJoiner = new StringJoiner(",\n");

        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            String description = fieldDescriptions.getOrDefault(fieldName, "");

            Class<?> fieldClass;
            Field field = null;
            try {
                field = parentClass.getDeclaredField(fieldName);
                fieldClass = field.getType();
            } catch (NoSuchFieldException e) {
                log.error("Field " + fieldName + " not found in " + parentClass.getName(), e);
                continue; // Skip this field if its definition is not found
            }

            propertiesJoiner.add(getFieldJson(fieldType, description, fieldName, field, fieldClass, requiredFields));
        }

        propertiesBuilder.append(propertiesJoiner);
        propertiesBuilder.append("\n    }");

        return propertiesBuilder.toString();
    }

    private static String getFieldJson(String fieldType, String description, String fieldName, Field field,
                                       Class<?> fieldClass, List<String> requiredFields) {
        String fieldFormat;
        if ("object".equals(fieldType)) {
            if (field.isAnnotationPresent(Reference.class)) {
                // TODO This is a bug, objects annotated with Reference should also be annotated with ParameterClass
                // Handle @Reference: Only display the field annotated with @ReferenceField
                Field referenceField = findReferenceField(fieldClass);
                if (referenceField != null) {
                    String referenceFieldDescription = findReferenceFieldDescription(referenceField);
                    String referenceFieldType = findReferenceFieldType(referenceField);

                    String typeString;
                    if (requiredFields.contains(fieldName)) {
                        typeString = "\"object\"";
                    } else {
                        typeString = "[\"object\", \"null\"]";
                    }

                    fieldFormat = String.format(
                        """
                            "%s": {
                                "type": %s,
                                "description": "%s",
                                "properties": {
                                    "%s": {
                                        "type": "%s",
                                        "description": "%s"
                                    }
                                },
                                "required": [%s],
                                "additionalProperties": false
                            }
                        """,
                        fieldName,
                        typeString,
                        description,
                        referenceField.getName(),
                        referenceFieldType,
                        referenceFieldDescription,
                        referenceField.getName()
                    );
                } else {
                    // TODO This should throw an exception
                    fieldFormat = ""; // Fallback if no @ReferenceField is found
                }
            } else {
                // Handle non-@Reference object: Use getParameterClassString recursively
                if (!fieldClass.isAnnotationPresent(ParameterClass.class)) {
                    throw new IllegalArgumentException("Complex objects not annotated with @ParameterClass are not supported.");
                }

                boolean isRequired;
                String typeName;
                if (requiredFields.contains(fieldName)) {
                    isRequired = true;
                    typeName = fieldClass.getSimpleName();
                } else {
                    isRequired = false;
                    typeName = fieldClass.getSimpleName() + "NotRequired";
                }

                String definition = getParameterClassStringInternal(fieldClass, ParameterClassResolution.INNER, isRequired);

                definitionsTL.get().put(fieldName, definition);

                fieldFormat = String.format(
                        """
                            "%s": {
                                "$ref": "#/definitions/%s"
                            }
                        """,
                        fieldName,
                        typeName
                );
            }
        } else if ("enum".equals(fieldType)) {

            String[] enumValues = Arrays.stream(fieldClass.getEnumConstants())
                    .map(Object::toString)
                    .toArray(String[]::new);

            String enumValuesJson = Arrays.stream(enumValues).map(e -> "\"" + e + "\"").collect(Collectors.joining(", "));

            String typeString;

            if (!requiredFields.contains(fieldName)) {
                typeString = "[\"string\", \"null\"]";
            } else {
                typeString = "\"string\"";
            }

            fieldFormat = String.format(
                    """
                        "%s": {
                            "type": %s,
                            "description": "%s",
                            "enum": [%s]
                        }
                    """,
                    fieldName,
                    typeString,
                    description,
                    enumValuesJson
            );

        } else if ("array".equals(fieldType)) {

            // TODO Need to handle the case where the array field is also annotated with @Reference

            // Handling arrays
            Class<?> arrayItemType = fieldClass.getComponentType(); // Get the type of array items

            if (arrayItemType == null) {
                arrayItemType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            }

            String arrayItemTypeStr;
            String arrayItemTypeStrValue;
            if (arrayItemType.isPrimitive() || arrayItemType == String.class) {
                arrayItemTypeStr = "type";
                arrayItemTypeStrValue = arrayItemType.getSimpleName().toLowerCase(); // Handle primitive or String types
            } else if (arrayItemType.isAnnotationPresent(ParameterClass.class)) {
                arrayItemTypeStr = "$ref";
                arrayItemTypeStrValue = "#/definitions/" + arrayItemType.getSimpleName();
                /*
                 * Required is true here, as for arrays, we want the regular definition. The array could always be empty.
                 */
                String definition = getParameterClassStringInternal(arrayItemType, ParameterClassResolution.INNER, true);
                definitionsTL.get().put(arrayItemType.getSimpleName(), definition);
            } else {
                throw new IllegalArgumentException("Array items of complex types not annotated with @ParameterClass are not supported.");
            }

            fieldFormat = String.format(
                """
                    "%s": {
                        "type": "array",
                        "description": "%s",
                        "items": {
                            "%s": "%s"
                        }
                    }
                """,
                fieldName,
                description,
                arrayItemTypeStr,
                arrayItemTypeStrValue
            );

        } else {

            String typeString;
            if (requiredFields.contains(fieldName)) {
                typeString = "\"" + fieldType + "\"";
            } else {
                typeString = "[\"" + fieldType + "\", \"null\"]";
            }

            fieldFormat = !description.isEmpty()
            ? String.format(
                """
                    "%s": {
                        "type": %s,
                        "description": "%s"
                    }
                """,
                fieldName,
                typeString,
                description
            )
            : String.format(
                """
                    "%s": {
                        "type": %s
                    }
                """,
                fieldName,
                typeString
            );
        }
        return fieldFormat;
    }

    private static String findReferenceFieldType(Field referenceField) {
        try {
            return determineFieldTypeOrThrow(referenceField);
        } catch (Exception e) {
            return ""; // Exception handling, return empty string or propagate the exception
        }
    }

    private static String findReferenceFieldDescription(Field referenceField) {
        FieldDescription description = referenceField.getAnnotation(FieldDescription.class);
        if (description != null) {
            return description.description();
        }
        return ""; // Return an empty string if not found or no description present.
    }

    private static Field findReferenceField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ReferenceField.class)) {
                return field;
            }
        }
        return null;
    }

    // Method to create a map of field names to their descriptions for fields annotated with @FieldDescription
    private static Map<String, String> getFieldDescriptions(Class<?> clazz) {
        Map<String, String> fieldDescriptions = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            FieldDescription description = field.getAnnotation(FieldDescription.class);
            if (description != null) {
                fieldDescriptions.put(field.getName(), description.description());
            }
        }
        return fieldDescriptions;
    }

    // Method to get a list of field names annotated with @RequiredField
    private static List<String> getRequiredFields(Class<?> clazz) {
        List<String> requiredFields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getAnnotation(RequiredField.class) != null) {
                requiredFields.add(field.getName());
            }
        }
        return requiredFields;
    }

    // Method to resolve and return field types as a map
    private static Map<String, String> resolveFieldTypes(Class<?> clazz) {
        Map<String, String> fieldTypeMap = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            try {
                String fieldType = determineFieldTypeOrThrow(field);
                fieldTypeMap.put(field.getName(), fieldType);
            } catch (Exception e) {
                /*
                 * Any object that's not annotated with ParameterClass interface will just be ignored.
                 * Any field annotated with Ignore annotation will be ignored.
                 */
                log.debug(e.getMessage());
            }
        }
        return fieldTypeMap;
    }

    // Helper method to determine the type of field
    private static String determineFieldTypeOrThrow(Field field) throws Exception {
        if (field.isAnnotationPresent(Ignore.class)) {
            throw new Exception("Field " + field.getName() + " is ignored because it's annotated with @Ignore.");
        }
        Class<?> type = field.getType();
        if (type.isArray() || List.class.isAssignableFrom(type)) {
            return "array";
        } else if ((type.isPrimitive()
                && type != boolean.class
                    && type != char.class
                        && type != byte.class
                            && type != void.class) || Number.class.isAssignableFrom(type)) {
            return "number";
        } else if (type.equals(String.class)) {
            return "string";
        } else if (type.isEnum()) {
            return "enum";
        } else {
            if (!type.isAnnotationPresent(ParameterClass.class)) {
                throw new Exception("Type " + type.getSimpleName() + " is not supported because it's not annotated with @ParameterClass.");
            }
            return "object";
        }
    }

}
