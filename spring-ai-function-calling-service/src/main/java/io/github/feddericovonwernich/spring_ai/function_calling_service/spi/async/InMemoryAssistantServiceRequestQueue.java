package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRunRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.RunStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class InMemoryAssistantServiceRequestQueue implements AssistantServiceRequestQueue {

    private final BlockingQueue<AssistantRequestInteraction> queue = new LinkedBlockingQueue<>();

    private final AssistantChainRunRepository assistantChainRunRepository;

    public InMemoryAssistantServiceRequestQueue(AssistantChainRunRepository assistantChainRunRepository) {
        this.assistantChainRunRepository = assistantChainRunRepository;
    }

    @Override
    public AssistantChainRun enqueue(AssistantQueueRequest queueRequest) {
        AssistantChainRun assistantChainRun = AssistantChainRun.builder()
                .status(RunStatus.CREATED)
                .message(queueRequest.getPrompt())
                .build();

        assistantChainRunRepository.save(assistantChainRun);

        AssistantRequestInteraction assistantRequestInteraction
                = new AssistantRequestInteraction(queueRequest.getRunId(), assistantChainRun);

        queue.add(assistantRequestInteraction);
        log.debug("Enqueued: {}, Generated: {}", assistantRequestInteraction, assistantChainRun);

        return assistantChainRun;
    }

    @Override
    public AssistantRequestInteraction dequeue() {
        try {
            AssistantRequestInteraction interaction = queue.take();
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
