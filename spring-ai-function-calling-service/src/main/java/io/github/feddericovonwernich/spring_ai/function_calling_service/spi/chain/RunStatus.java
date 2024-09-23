package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain;

public enum RunStatus {
    CREATED,
    USER_ACTION,
    COMPLETED,
    FAILED
}
