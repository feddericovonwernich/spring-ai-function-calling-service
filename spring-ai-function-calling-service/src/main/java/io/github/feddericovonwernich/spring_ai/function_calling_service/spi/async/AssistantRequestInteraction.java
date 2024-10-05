package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class AssistantRequestInteraction extends AssistantInteraction {

    private final AssistantChainRun assistantChainRun;

    public AssistantRequestInteraction(Long runId, AssistantChainRun assistantChainRun) {
        super(runId);
        this.assistantChainRun = assistantChainRun;
    }

}
