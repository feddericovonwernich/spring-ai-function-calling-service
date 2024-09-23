package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

public interface AssistantServiceQueue<InteractionType extends AssistantInteraction> {

    void enqueue(InteractionType interaction);

    InteractionType dequeue();

    boolean isEmpty();

}
