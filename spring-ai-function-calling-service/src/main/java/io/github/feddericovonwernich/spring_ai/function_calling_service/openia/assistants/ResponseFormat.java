package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ResponseFormat {

    /**
     * Assistant will use this format.
     */
    ResponseFormatEnum type;

    /**
     * Only used if format is JSON_SCHEMA
     */
    AssistantJsonSchema jsonSchema;

}
