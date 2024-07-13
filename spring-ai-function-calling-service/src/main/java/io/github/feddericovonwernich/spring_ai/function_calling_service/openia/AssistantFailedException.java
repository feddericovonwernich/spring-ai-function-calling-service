package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

public class AssistantFailedException extends Exception {

    public AssistantFailedException(String message) {
        super(message);
    }

}
