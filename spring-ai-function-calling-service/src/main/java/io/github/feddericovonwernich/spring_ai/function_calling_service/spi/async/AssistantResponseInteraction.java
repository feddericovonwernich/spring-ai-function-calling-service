package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import lombok.*;

@Getter
public class AssistantResponseInteraction extends AssistantInteraction {

    private final String assistantResponse;

    public AssistantResponseInteraction(Long runId, String assistantResponse) {
        super(runId);
        this.assistantResponse = assistantResponse;
    }

}
