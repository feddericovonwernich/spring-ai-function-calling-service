package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AssistantSortOrder {

    @JsonProperty("asc")
    ASC,

    @JsonProperty("desc")
    DESC
}
