package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ResponseFormatEnum {

    @JsonProperty("json_schema")
    JSON_SCHEMA,

    @JsonProperty("json_object")
    JSON_OBJECT

}
