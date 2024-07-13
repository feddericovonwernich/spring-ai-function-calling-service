package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AssistantSortOrder {

    @JsonProperty("asc")
    ASC,

    @JsonProperty("desc")
    DESC
}
