package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import lombok.Getter;

/*
 * This is an exception from which we hope to recover.
 */
@Getter
public class AssistantFailedException extends Exception {

    private final boolean rateLimited;

    public AssistantFailedException(String message, boolean rateLimited) {
        super(message);
        this.rateLimited = rateLimited;
    }

}
