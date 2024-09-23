package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Tool;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantDefinition;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenAIAssistantDefinition extends AssistantDefinition {

    private String schema;

    @Singular
    private List<Tool> toolList;

    @Builder
    public OpenAIAssistantDefinition(String name, String description, String prompt, String model, String schema, List<Tool> toolList) {
        super(name, description, prompt, model);
        this.schema = schema;
        this.toolList = toolList;
    }

}
