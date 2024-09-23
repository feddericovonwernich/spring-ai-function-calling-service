package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.OpenAIAssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Assistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticRunSummary;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticRunSummaryRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;

import java.util.List;

public class SummarizatorLink implements AssistantChainLink<Assistant> {

    private static String SUMMARIZATOR_ASSISTANT = "SummarizatorAssistant";
    private static String SUMMARIZATOR_MODEL = "gpt-4o-mini-2024-07-18";
    private static String SUMMARIZATOR_PROMPT = "Your task is to summarize the conversation given in the prompt. Goal is to save it in the most compact way, without losing information. BE VERY CONCISE.";
    private static String SUMMARIZATOR_DESCRIPTION = "Assistant that summarizes information.";
    private static String SUMMARIZATOR_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "summary": {
                        "type": "string",
                        "description": "Summary of the given information. Very concise."
                    }
                },
                "required": ["summary"],
                "additionalProperties": false
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Assistant assistant;

    private final AssistantService<Assistant> assistantService;
    private final SemanticRunSummaryRepository semanticRunSummaryRepository;

    public SummarizatorLink(AssistantService<Assistant> assistantService, SemanticRunSummaryRepository semanticRunSummaryRepository) {
        this.assistantService = assistantService;
        this.semanticRunSummaryRepository = semanticRunSummaryRepository;
    }

    @Override
    public String process(AssistantChainRun assistantChainRun, Long lastRunId) throws AssistantFailedException {
        // Get the conversation in String format.
        String prompt = getSummarizatorPrompt(assistantChainRun);

        // Get the summary from the assistant and save it.
        String summary = parseAssistantResponse(assistantService.processRequest(prompt, getAssistant()));
        SemanticRunSummary semanticRunSummary = SemanticRunSummary.builder()
                .summary(summary)
                .assistantChainRun(assistantChainRun)
                .semanticThread(assistantChainRun.getSemanticThread()) // TODO This is redundant.
                .build();

        semanticRunSummaryRepository.save(semanticRunSummary);

        // Now I need to return the executors' response.
        return assistantChainRun.getMessages().getLast();
    }

    private String getSummarizatorPrompt(AssistantChainRun assistantChainRun) {
        List<String> conversation = assistantChainRun.getMessages();
        return String.join("\n", conversation);
    }

    private String parseAssistantResponse(String assistantJsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(assistantJsonResponse);
            JsonNode summaryNode = rootNode.get("summary");
            if (summaryNode != null) {
                return summaryNode.asText();
            } else {
                throw new IllegalArgumentException("The provided JSON does not contain a 'summary' field.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while parsing summarizator response.", e);
        }
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
            .name(SUMMARIZATOR_ASSISTANT)
            .description(SUMMARIZATOR_DESCRIPTION)
            .prompt(SUMMARIZATOR_PROMPT)
            .model(SUMMARIZATOR_MODEL)
            .schema(SUMMARIZATOR_SCHEMA)
            .build();
    }

    /*
     * This link should summarize the important bits of the interaction and save them to database, before returning the response.
     */
}
