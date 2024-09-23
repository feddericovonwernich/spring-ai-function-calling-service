package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;

public interface AssistantChainLink<AssistantType> {

    String process(AssistantChainRun assistantChainRun, Long lastRunId) throws AssistantFailedException;

    AssistantType getAssistant();

    AssistantDefinition getAssistantDefinition();

}
