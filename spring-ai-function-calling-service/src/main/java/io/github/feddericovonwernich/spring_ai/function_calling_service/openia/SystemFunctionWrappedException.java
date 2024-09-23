package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import lombok.Getter;

@Getter
public class SystemFunctionWrappedException extends RuntimeException {

    private final AssistantFailedException assistantFailedException;

    public SystemFunctionWrappedException(AssistantFailedException assistantFailedException) {
        this.assistantFailedException = assistantFailedException;
    }

}
