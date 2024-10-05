package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class InMemoryAssistantServiceResponseQueue implements AssistantServiceResponseQueue {

    private final BlockingQueue<AssistantResponseInteraction> queue = new LinkedBlockingQueue<>();

    @Override
    public void enqueue(AssistantResponseInteraction interaction) {
        queue.add(interaction);  // Enqueues the interaction
        log.debug("Enqueued: {}", interaction);
    }

    @Override
    public AssistantResponseInteraction dequeue() {
        try {
            AssistantResponseInteraction interaction = queue.take();
            log.debug("Dequeued: {}", interaction);
            return interaction;  // Blocks until an interaction is available
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

}
