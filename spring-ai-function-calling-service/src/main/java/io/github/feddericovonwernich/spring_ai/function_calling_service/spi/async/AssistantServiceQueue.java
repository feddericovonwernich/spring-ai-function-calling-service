package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

public interface AssistantServiceQueue<InteractionType extends AssistantInteraction> {

    InteractionType dequeue();

    boolean isEmpty();

}
