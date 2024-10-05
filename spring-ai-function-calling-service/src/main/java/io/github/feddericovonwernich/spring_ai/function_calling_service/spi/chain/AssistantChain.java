package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain;


import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async.AssistantResponseInteraction;

public interface AssistantChain {

    void addLink(AssistantChainLink assistantChainLink);

    AssistantResponseInteraction runThroughChain(AssistantChainRun assistantChainRun, Long lastRunId) throws AssistantFailedException;

}
