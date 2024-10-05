package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import jakarta.transaction.Transactional;

public interface AssistantServiceRequestQueue extends AssistantServiceQueue<AssistantRequestInteraction> {

    AssistantChainRun enqueue(AssistantQueueRequest interaction);

}
