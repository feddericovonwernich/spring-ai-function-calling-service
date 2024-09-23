package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class InMemoryAssistantServiceRequestQueue implements AssistantServiceRequestQueue {

    private final BlockingQueue<AssistantRequestInteraction> queue = new LinkedBlockingQueue<>();

    @Override
    public void enqueue(AssistantRequestInteraction interaction) {
        queue.add(interaction);  // Enqueues the interaction
    }

    @Override
    public AssistantRequestInteraction dequeue() {
        try {
            return queue.take();  // Blocks until an interaction is available
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
