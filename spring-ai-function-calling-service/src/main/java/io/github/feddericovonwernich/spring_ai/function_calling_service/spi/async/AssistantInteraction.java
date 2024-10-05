package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import lombok.*;

@Getter
@ToString
public class AssistantInteraction {

    private final Long runId;

    public AssistantInteraction(Long runId) {
        this.runId = runId;
    }
}
