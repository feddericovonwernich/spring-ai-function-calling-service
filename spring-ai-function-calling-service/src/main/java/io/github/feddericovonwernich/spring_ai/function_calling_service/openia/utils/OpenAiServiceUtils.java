package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.utils;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.common.OpenAiHttpException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.service.ServiceOpenAI;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.threads.Thread;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.threads.ThreadRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAiServiceUtils {

    public static Thread createNewThread(ServiceOpenAI aiService) {
        ThreadRequest threadRequest = ThreadRequest.builder().build();
        Thread thread = aiService.createThread(threadRequest);
        log.debug("Created thread: {} at: {}", thread.getId(), thread.getCreatedAt());
        return thread;
    }

    public static Thread getExistingThread(String threadId, ServiceOpenAI aiService) {
        try {
            return aiService.retrieveThread(threadId);
        } catch (OpenAiHttpException ex) {
            if (ex.statusCode == 404) {
                return null;
            } else {
                throw new RuntimeException("Error while retrieving thread.", ex);
            }
        }
    }

}
