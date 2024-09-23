package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async.AssistantResponseInteraction;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class AssistantChainImpl implements AssistantChain {

    private final List<AssistantChainLink> chainLinks = new ArrayList<>();

    private final AssistantChainRunRepository assistantChainRunRepository;

    public AssistantChainImpl(AssistantChainRunRepository assistantChainRunRepository) {
        this.assistantChainRunRepository = assistantChainRunRepository;
    }

    @Override
    public void addLink(AssistantChainLink assistantChainLink) {
        chainLinks.add(assistantChainLink);
    }

    @Override
    @Transactional
    public AssistantResponseInteraction runThroughChain(String prompt, Long lastRunId) throws AssistantFailedException {

        AssistantChainRun assistantChainRun = AssistantChainRun.builder()
                .status(RunStatus.CREATED)
                .message(prompt)
                .build();

        assistantChainRunRepository.save(assistantChainRun);

        String response = null;
        try {
            for (AssistantChainLink chainLink : chainLinks) {
                response = chainLink.process(assistantChainRun, lastRunId);
                // Go through the chain and break if a link ask for user action.
                if (assistantChainRun.getStatus().equals(RunStatus.USER_ACTION)) break;
            }
            if (!assistantChainRun.getStatus().equals(RunStatus.USER_ACTION)
                    && !assistantChainRun.getStatus().equals(RunStatus.FAILED)) {
                // If it's not failed or asking for action, then it completed.
                assistantChainRun.setStatus(RunStatus.COMPLETED);
            }
        } catch (Exception e) {
            log.error("Error while running chain.", e);

            // TODO Could definitely improve how the user is communicated the error. Or how do we even want to handle errors here.
            response = "Sorry, an unexpected error happened, administrator will be warned about this interaction.";

            assistantChainRun.setStatus(RunStatus.FAILED);
            assistantChainRun.getMessages().add(response + " | ExceptionMessage: " + e.getMessage());

            // We let the caller decide how to handle the error.
            if (e instanceof AssistantFailedException) {
                throw e;
            }

        } finally {
            assistantChainRunRepository.save(assistantChainRun);
        }

        return new AssistantResponseInteraction(assistantChainRun.getId(), response);
    }

}
