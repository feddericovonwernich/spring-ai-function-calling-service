package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
public class AssistantInteraction {

    private final Long runId;

    public AssistantInteraction(Long runId) {
        this.runId = runId;
    }
}
