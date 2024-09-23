package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import lombok.Getter;

@Getter
public class AssistantRequestInteraction extends AssistantInteraction {

    private final String userRequest;

    public AssistantRequestInteraction(Long runId, String userRequest) {
        super(runId);
        this.userRequest = userRequest;
    }

}
