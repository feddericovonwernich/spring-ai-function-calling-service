package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AssistantJsonSchema {

    String name;

    boolean strict;

    JsonNode schema;

}
