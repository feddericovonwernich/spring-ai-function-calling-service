package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChain;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class AssistantServiceRequestConsumer {

    @Value("${assistant.consumer.maxThreads:1}")
    private final int maxThreads;

    private final AssistantServiceRequestQueue requestQueue;
    private final AssistantServiceResponseQueue responseQueue;
    private final AssistantChain assistantChain;
    private final ExecutorService executorService;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public AssistantServiceRequestConsumer(AssistantServiceRequestQueue queue,
                                           AssistantServiceResponseQueue responseQueue,
                                           AssistantChain assistantChain,
                                           int maxThreads) {
        this.requestQueue = queue;
        this.maxThreads = maxThreads;
        this.responseQueue = responseQueue;
        this.assistantChain = assistantChain;
        this.executorService = Executors.newFixedThreadPool(this.maxThreads);
        startConsuming();
    }

    private void startConsuming() {
        log.debug("Starting AssistantServiceRequestConsumer with {} workers.", maxThreads);
        for (int i = 0; i < maxThreads; i++) {
            executorService.submit(new Worker());
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown.set(true);
        executorService.shutdown();

        // TODO This shut down waiting needs to be configurable.
        try {
            if (!executorService.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            log.debug("Starting worker on thread: {}", Thread.currentThread().getName());
            while (!shutdown.get()) {
                if (!requestQueue.isEmpty()) {
                    log.debug("Queue is not empty, consuming requests.");
                    while (!requestQueue.isEmpty()) {
                        AssistantRequestInteraction interaction = requestQueue.dequeue();
                        if (interaction != null) {
                            try {
                                processInteraction(interaction);
                            } catch (Exception e) {
                                log.error("Error while processing an item from the queue.", e);
                            }
                        }
                    }
                } else {
                    // TODO Make this configurable.
                    // We wait a configurable amount of time to wait for requests.
                    long waitMillis = 1000L;
                    log.debug("Queue is empty, sleeping {} millis to wait for requests...", waitMillis);
                    try {
                        Thread.sleep(waitMillis);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private void processInteraction(AssistantRequestInteraction request) {
        AssistantResponseInteraction responseInteraction = null;
        try {
            responseInteraction = assistantChain.runThroughChain(request.getAssistantChainRun(), request.getRunId());
        } catch (AssistantFailedException e) {
            if (e.isRateLimited()) {

                // We're saving the assistantChainRun we're retrying for in the new assistantChainRun.
                // I hope to use that later to find the original runId for the user request.

                // We enqueue the request again for processing. // TODO I'd like to mark the request somehow as rate limited, maybe put it in another queue.
                AssistantQueueRequest assistantQueueRequest = getAssistantQueueRequest(request);
                requestQueue.enqueue(assistantQueueRequest);

                // We wait some time. // TODO Maybe make this configurable or dynamic / exponential backoff.
                try {
                    Thread.sleep(Duration.of(1, ChronoUnit.MINUTES));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                // We enqueue a response.
                String responseString = "We're sorry, there was an error processing the request. " +
                        "An administrator will be notified about the error. " +
                        "You may try again or contact administrators.";
                responseInteraction
                        = new AssistantResponseInteraction(request.getRunId(), responseString);
            }
        }
        if (responseInteraction != null) {
            responseQueue.enqueue(responseInteraction);
        }
    }

    private static AssistantQueueRequest getAssistantQueueRequest(AssistantRequestInteraction request) {
        String originalPrompt = request.getAssistantChainRun()
                .getMessages()
                .getFirst();
        Long lastRunId = request.getAssistantChainRun()
                .getId();
        return new AssistantQueueRequest(originalPrompt, lastRunId, request.getAssistantChainRun());
    }

}