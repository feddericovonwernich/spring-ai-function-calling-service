package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class AssistantQueueRequest {

    public AssistantQueueRequest(String prompt, Long runId) {
        this.prompt = prompt;
        this.runId = runId;
    }

    public AssistantQueueRequest(String prompt, Long runId, AssistantChainRun retryFor) {
        this.prompt = prompt;
        this.runId = runId;
        this.retryFor = retryFor;
    }

    String prompt;

    Long runId;

    AssistantChainRun retryFor;

}
