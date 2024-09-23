package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChain;
import jakarta.annotation.PreDestroy;
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
        for (int i = 0; i < maxThreads; i++) {
            executorService.submit(new Worker());
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown.set(true);
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(180, TimeUnit.SECONDS)) {
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
            while (!shutdown.get()) {
                AssistantRequestInteraction interaction = requestQueue.dequeue();
                if (interaction != null) {
                    processInteraction(interaction);
                }
            }
        }
    }

    private void processInteraction(AssistantRequestInteraction request) {
        AssistantResponseInteraction responseInteraction = null;
        try {
            responseInteraction = assistantChain.runThroughChain(request.getUserRequest(), request.getRunId());
        } catch (AssistantFailedException e) {
            // TODO Here's where I need to handle any error being hit.
            //  If it's not rate limit, then we'd want the response.

            if (e.isRateLimited()) {
                // We enqueue the request again for processing.
                // TODO I'd like to mark the request somehow as rate limited, maybe put it in another queue.
                requestQueue.enqueue(request);

                // We wait some time.
                // TODO Maybe make this configurable or dynamic / exponential backoff.
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

}