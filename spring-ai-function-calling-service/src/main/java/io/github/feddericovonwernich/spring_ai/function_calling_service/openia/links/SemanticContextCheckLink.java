package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.OpenAIAssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Assistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticRunSummary;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticRunSummaryRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticThread;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticThreadRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRunRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class SemanticContextCheckLink implements AssistantChainLink<Assistant> {

    // TODO Make these configurable.

    private static String SEMANTIC_CONTEXT_CHECK_ASSISTANT = "SemanticContextCheckAssistant";
    private static String MODEL = "gpt-4o-mini-2024-07-18";
    private static String PROMPT = "Your task is to identify if the user request is related to the provided context.";
    private static String DESCRIPTION = "Checks if the user request is related to previous request.";
    private static String SCHEMA = """
        {
            "type": "object",
            "properties": {
                "context": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    },
                    "description": "From the given context, this is the list of the most relevant sentences to pass on as context. Can be an empty list if there's no relevant content."
                },
                "related": {
                    "type": "string",
                    "enum": ["RELATED", "NOT_RELATED"]
                }
            },
            "required": ["context", "related"],
            "additionalProperties": false
        }
        """;

    private final AssistantChainRunRepository assistantChainRunRepository;
    private final SemanticThreadRepository semanticThreadRepository;
    private final SemanticRunSummaryRepository semanticRunSummaryRepository;
    private final AssistantService<Assistant> assistantService;

    private Assistant assistant;

    private final ObjectMapper mapper = new ObjectMapper();


    public SemanticContextCheckLink(AssistantChainRunRepository assistantChainRunRepository,
                                    SemanticThreadRepository semanticThreadRepository,
                                    SemanticRunSummaryRepository semanticRunSummaryRepository,
                                    AssistantService<Assistant> assistantService) {
        this.assistantChainRunRepository = assistantChainRunRepository;
        this.semanticThreadRepository = semanticThreadRepository;
        this.semanticRunSummaryRepository = semanticRunSummaryRepository;
        this.assistantService = assistantService;
    }

    @Override
    public String process(AssistantChainRun assistantChainRun, Long lastRunId) throws AssistantFailedException {

        AssistantChainRun lastChainRun = null;
        if (lastRunId != null) {
            try {
                lastChainRun = assistantChainRunRepository.getReferenceById(lastRunId);
            } catch (EntityNotFoundException e) {
                log.warn("Non-existent lastRunId: {}, treating request as without context.", lastRunId);
            }
        }

        SemanticThread semanticThread;
        List<SemanticRunSummary> semanticRunSummaries = null;
        if (lastChainRun != null) {

            // TODO SemanticThread is stored in database with AssistantChainRun, again,
            //  AssistantChainRun should not have knowledge about SemanticThread.
            //  It should be stored somewhere else in database.
            semanticThread = lastChainRun.getSemanticThread();

            semanticRunSummaries = semanticRunSummaryRepository.findAll(new Example<>() {
                @Override
                @NonNull
                public SemanticRunSummary getProbe() {
                    return SemanticRunSummary.builder()
                            .semanticThread(semanticThread)
                            .build();
                }

                @Override
                @NonNull
                public ExampleMatcher getMatcher() {
                    return ExampleMatcher.matching()
                            .withIgnoreNullValues();
                }
            });

            log.debug("Running with semanticThread: {}, lastRunId: {}, Summaries found: {}",
                    semanticThread, lastRunId, semanticRunSummaries);
        } else {
            // If there's no last run ID, then there's no earlier context to fetch. We create a new one.
            semanticThread = semanticThreadRepository.save(SemanticThread.builder().build());
            log.debug("Run without context, created semanticThread: {}", semanticThread);
        }

        String userRequest = assistantChainRun.getMessages().getLast();
        String response = userRequest;

        if (semanticRunSummaries != null && !semanticRunSummaries.isEmpty()) {
            List<String> context = getContextFromSummaries(userRequest, semanticRunSummaries);

            if (!context.isEmpty()) {
                log.debug("Adding context to prompt: {}", context);
                StringBuilder responseStringBuilder = new StringBuilder();
                for (String contextString : context) {
                    responseStringBuilder.append("Conversation Context: ")
                            .append(contextString)
                            .append(System.lineSeparator());
                }
                responseStringBuilder.append("User request: ").append(userRequest);
                response = responseStringBuilder.toString();
            }
        }

        assistantChainRun.addMessage(response);

        // TODO Technically, AssistantChainRun should not have knowledge about SemanticThread.
        //  Ideally, we should be setting this SemanticThread in some Chain Context.
        assistantChainRun.setSemanticThread(semanticThread);
        //assistantChainRunRepository.save(assistantChainRun);

        return response;
    }

    @Override
    public Assistant getAssistant() {
        if (assistant == null) {
            assistant = assistantService.getOrCreateAssistant(getAssistantDefinition());
        }
        return assistant;
    }

    @Override
    public AssistantDefinition getAssistantDefinition() {
        return OpenAIAssistantDefinition.builder()
                .name(SEMANTIC_CONTEXT_CHECK_ASSISTANT)
                .description(DESCRIPTION)
                .model(MODEL)
                .prompt(PROMPT)
                .schema(SCHEMA)
                .build();
    }

    private List<String> getContextFromSummaries(String userRequest, List<SemanticRunSummary> semanticRunSummaries) throws AssistantFailedException {

        if (semanticRunSummaries.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder promptStringBuilder = new StringBuilder();

        for (SemanticRunSummary semanticRunSummary : semanticRunSummaries) {
            promptStringBuilder.append("Summary entry: ")
                    .append(semanticRunSummary.getSummary())
                    .append(System.lineSeparator());
        }

        promptStringBuilder.append("User Request: ")
                .append(userRequest);

        String responseJson = assistantService.processRequest(promptStringBuilder.toString(), getAssistant());

        try {
            log.debug("Response Json: {}", responseJson);
            JsonNode rootNode = mapper.readTree(responseJson);
            String related = rootNode.path("related").asText();
            if (related.equals("RELATED")) {
                JsonNode contextNode = rootNode.path("context");
                return extractListFromContextNode(contextNode);
            } else {
                // It's not related.
                return Collections.emptyList();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while parsing SemanticContextCheckLink assistant response.", e);
        }
    }

    private static List<String> extractListFromContextNode(JsonNode contextNode) {
        List<String> contextList = new ArrayList<>();
        if (contextNode.isArray()) {
            for (JsonNode node : contextNode) {
                contextList.add(node.asText());
            }
        } else if (contextNode.isTextual()) {
            // If 'context' is a single string instead of an array
            contextList.add(contextNode.asText());
        }
        return contextList;
    }

}
