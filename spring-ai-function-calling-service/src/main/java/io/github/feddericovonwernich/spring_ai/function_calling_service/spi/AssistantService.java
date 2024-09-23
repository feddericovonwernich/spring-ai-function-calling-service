package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Assistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs.ToolCallFunction;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async.AssistantRequestInteraction;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async.AssistantResponseInteraction;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Provides services for processing user requests through an AI model.
 * This interface defines the capabilities of an assistant service
 * including processing individual requests and managing conversational context.
 *
 * @author Federico von Wernich
 */
public interface AssistantService<AssistantType> {

    void setSystemFunction(Function<ToolCallFunction, String> function, String functionId);

    Assistant getOrCreateAssistant(AssistantDefinition definition);

    Assistant createAssistant(AssistantDefinition definition);

    String processRequest(String prompt, AssistantType assistantType) throws AssistantFailedException;

    String processRequest(String prompt, AssistantType assistantType, Map<String, ?> context) throws AssistantFailedException;


}
