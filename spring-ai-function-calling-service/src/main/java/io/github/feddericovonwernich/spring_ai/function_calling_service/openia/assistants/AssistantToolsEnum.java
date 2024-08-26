package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AssistantToolsEnum {

    @JsonProperty("code_interpreter")
    CODE_INTERPRETER,

    @JsonProperty("function")
    FUNCTION,

    @JsonProperty("retrieval")
    RETRIEVAL
}
