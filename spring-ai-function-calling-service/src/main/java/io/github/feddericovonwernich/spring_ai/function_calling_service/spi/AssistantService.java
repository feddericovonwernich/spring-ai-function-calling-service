package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

/**
 * Provides services for processing user requests through an AI model.
 * This interface defines the capabilities of an assistant service
 * including processing individual requests and managing conversational context.
 *
 * @author Federico von Wernich
 */
public interface AssistantService {

    /**
     * Is able to process a user request using underlying AI model.
     *
     * @param userInput The input provided by the user.
     * @return The processed string response.
     */
    AssistantResponse processRequest(String userInput);

    /**
     * Is able to process a user request using underlying AI model. Follow the conversation from the given thread id.
     *
     * @param userInput The input provided by the user.
     * @param threadId  The identifier for the processing thread.
     * @return The processed string response.
     */
    String processRequest(String userInput, String threadId);
}
