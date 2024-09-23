package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.OpenAIAssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs.ToolCallFunction;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.service.ServiceOpenAI;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.threads.Thread;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.utils.OpenAiServiceUtils;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRunRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.RunStatus;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.function_definitions.FunctionDefinitionsService;
import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;

import java.util.*;
import java.util.function.Function;

@AssistantToolProvider
@Slf4j
public class OrchestratorLink implements AssistantChainLink<Assistant> {

    // TODO Make these configurable.

    private static String ORCHESTRATOR_ASSISTANT = "OrchestratorAssistant";
    private static String ORCHESTRATOR_MODEL = "gpt-4o-mini-2024-07-18";
    private static String ORCHESTRATOR_PROMPT = "Your task is to identify which action on the system the user wants to perform, identify which information the user has given, and formulate a plan for another assistant to follow and execute to fulfill the users request. BE VERY CONCISE.";
    private static String ORCHESTRATOR_DESCRIPTION = "Assistant that understands user request and can formulate a plan to fulfill it.";
    private static String ORCHESTRATOR_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "response": {
                        "type": "string",
                        "description": "A formulated plan for another assistant to execute if result_type = PLAN, or a request for information from the user if result_type = USER_INFO."
                    },
                    "result_type": {
                        "type": "string",
                        "enum": ["PLAN", "USER_INFO"]
                    }
                },
                "required": ["response", "result_type"],
                "additionalProperties": false
            }
            """;

    private final AssistantService<Assistant> assistantService;
    private final FunctionDefinitionsService functionDefinitionsService;
    private final OrchestratorThreadRepository orchestratorThreadRepository;
    private final ServiceOpenAI aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Assistant assistant;

    public OrchestratorLink(AssistantService<Assistant> assistantService,
                            FunctionDefinitionsService functionDefinitionsService,
                            OrchestratorThreadRepository orchestratorThreadRepository, ServiceOpenAI aiService) {
        this.assistantService = assistantService;
        this.functionDefinitionsService = functionDefinitionsService;
        this.orchestratorThreadRepository = orchestratorThreadRepository;
        this.aiService = aiService;
    }

    @Override
    public String process(AssistantChainRun assistantChainRun, Long lastRunId) throws AssistantFailedException {

        // TODO Should be getting the SemanticThread from some context, it should not live in AssistantChainRun
        SemanticThread semanticThread = assistantChainRun.getSemanticThread();

        // TODO Can we have more than one OrchestratorThread per SemanticThread? If so,
        //  then this probably needs to change.
        Optional<OrchestratorThread> orchestratorThreadOptional = orchestratorThreadRepository.findOne(new Example<>() {
            @Override
            @NonNull
            public OrchestratorThread getProbe() {
                return OrchestratorThread.builder()
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

        Thread thread;
        OrchestratorThread orchestratorThread;

        if (orchestratorThreadOptional.isPresent()) {
            // We have the orchestrator thread, there's a thread to use.
            orchestratorThread = orchestratorThreadOptional.get();

            if (orchestratorThread.getStatus().equals(OrchestratorThreadStatus.DONE)) {
                //  A plan was created already, why are using this orchestrator thread again?
                //   We should be using a new one.
                thread = OpenAiServiceUtils.createNewThread(aiService);

                orchestratorThread = OrchestratorThread.builder()
                        .openAiThreadId(thread.getId())
                        .status(OrchestratorThreadStatus.CREATED)
                        .semanticThread(semanticThread)
                        .build();

                // Save it to database.
                orchestratorThreadRepository.save(orchestratorThread);
            } else {

                Thread existingThread
                        = OpenAiServiceUtils.getExistingThread(orchestratorThread.getOpenAiThreadId(), aiService);

                // TODO What should happen if there's no such thread? This is definitely a bug.
                //  For now, we create a new thread, but should log something or handle this in some way.
                thread = Objects.requireNonNullElseGet(existingThread,
                        () -> OpenAiServiceUtils.createNewThread(aiService));
            }
        } else {

            // TODO Looks duplicated huh?

            // We have to create the whole thing thread.
            thread = OpenAiServiceUtils.createNewThread(aiService);

            orchestratorThread = OrchestratorThread.builder()
                    .openAiThreadId(thread.getId())
                    .status(OrchestratorThreadStatus.CREATED)
                    .semanticThread(semanticThread)
                    .build();

            // Save it to database.
            orchestratorThreadRepository.save(orchestratorThread);
        }

        Map<String, ?> context = Collections.singletonMap("thread", thread);
        String prompt = assistantChainRun.getMessages().getLast();

        String responseJsonString = assistantService.processRequest(prompt, getAssistant(), context);

        try {
            // TODO Should be deserialized to a known entity made for this use case.

            // Parse the JSON into a JsonNode
            JsonNode jsonNode = objectMapper.readTree(responseJsonString);

            // Extract the values
            String response = jsonNode.get("response").asText();
            String resultType = jsonNode.get("result_type").asText();

            // Update statuses.
            if (resultType.equals("USER_INFO")) {
                orchestratorThread.setStatus(OrchestratorThreadStatus.USER_INFO);
                assistantChainRun.setStatus(RunStatus.USER_ACTION);
            } else if (resultType.equals("PLAN")) {
                orchestratorThread.setStatus(OrchestratorThreadStatus.DONE);
            } else {
                throw new IllegalStateException("State should not be possible. Assistant ");
            }

            // Add the response to the list of messages.
            assistantChainRun.getMessages().add(response);

            // Return response to the system.
            return response;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while parsing orchestrator response.", e);
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

        String prompt = addSystemOperationsToPrompt(ORCHESTRATOR_PROMPT);
        Tool dataIdentifierTool = getDataIdentifierTool();

        assistantService.setSystemFunction(getEvaluateInformationFunction(), getEvaluateInformationFunctionId());

        return OpenAIAssistantDefinition.builder()
                .name(ORCHESTRATOR_ASSISTANT)
                .description(ORCHESTRATOR_DESCRIPTION)
                .prompt(prompt)
                .model(ORCHESTRATOR_MODEL)
                .schema(ORCHESTRATOR_SCHEMA)
                .toolList(Collections.singletonList(dataIdentifierTool))
                .build();
    }

    private String getEvaluateInformationFunctionId() {
        return getClass().getSimpleName() + "_evaluateInformation_systemagent";
    }

    private String addSystemOperationsToPrompt(String prompt) {
        StringBuilder stringBuilder = new StringBuilder(prompt);
        Set<String> systemOperations = functionDefinitionsService.getSystemFunctionsNames();

        stringBuilder.append("\n").append("\n");
        stringBuilder.append("System available operations: ");

        for (String operation : systemOperations) {
            stringBuilder.append(operation).append("\n");
        }

        return stringBuilder.toString();
    }


    private static final String DATA_IDENTIFIER_TOOL_SCHEMA =
        """
            {
                "type": "object",
                "properties": {
                    "user_prompt": {
                        "type": "string",
                        "description": "Information given by the user. Be concise."
                    },
                    "operations": {
                        "type": "array",
                        "items": {
                            "type": "string",
                            "enum": %s
                        },
                        "description": "List of operations that need to be performed in the system."
                    }
                },
                "required": ["user_prompt", "operations"],
                "additionalProperties": false
            }
        """;

    private static String getDataIdentifierIdentifierToolJsonSchema(Set<String> systemTools) {
        // Create a StringJoiner to join the enum values with commas
        StringJoiner enumValues = new StringJoiner(", ", "[", "]");
        for (String value : systemTools) {
            enumValues.add("\"" + value + "\"");
        }

        // One of the values should always be null to allow for the assistant to say there weren't any entities.
        enumValues.add("\"null\"");

        // Build the JSON schema string
        return DATA_IDENTIFIER_TOOL_SCHEMA.formatted(enumValues.toString());
    }

    private Tool getDataIdentifierTool() {

        String description = "This function should identify if the user prompt has enough information " +
                "for the functions the system needs to call to accomplish user request.";

        String functionParametersSchema = getDataIdentifierIdentifierToolJsonSchema(getAgentFunctions());

        // Orchestrator needs to output a json to be able to call evaluateInformation(String prompt, List<String> operations)
        AssistantFunction assistantFunction = AssistantFunction.builder()
                .name(getEvaluateInformationFunctionId())
                .description(description)
                .parameters(AssistantFunctionUtils.buildParameters(functionParametersSchema))
                .build();

        return new Tool(AssistantToolsEnum.FUNCTION, assistantFunction);
    }

    @Nonnull
    private Set<String> getAgentFunctions() {
        return functionDefinitionsService.getSystemFunctionsNames();
    }

    private Function<ToolCallFunction, String> getEvaluateInformationFunction() {
        return toolCallFunction -> {
            try {
                // TODO Unchecked casts warnings will probably go away if we deserialize
                //  to a known class made for this task.

                // Parse the JSON string into a map
                Map<String, Object> parsedParams = objectMapper.readValue(toolCallFunction.getArguments(), Map.class);

                // Extract the "user_prompt" and "operations" from the map
                String prompt = (String) parsedParams.get("user_prompt");
                List<String> operations = (List<String>) parsedParams.get("operations");

                Map<String, String> result = evaluateInformation(prompt, operations);

                // Return this as a JSON string using ObjectMapper
                return objectMapper.writeValueAsString(result);

            } catch (Exception e) {
                log.error("Error while parsing functionParams: {}", toolCallFunction.getArguments(), e);
                throw new RuntimeException(e);
            }
        };
    }

    /*
     * This link needs to understand the user request, determine if the user has given enough information to fulfill its request, and formulate a plan for the executor to follow.
     *
     * Since this link could maintain a conversation with the user on an open ia thread, it should delegate needed data inspection to another assistant to avoid carrying context.
     *
     * Assistant should have knowledge about all possible operations in the system.
     * Assistant should have knowledge about every entity in the system.
     *
     * Steps:
     *  - Identify what the user wants to do.
     *  - Tool call to another assistant giving identified entities, and identified operation, and user given information asking if it is enough.
     *      - Put identified entities in context.
     *      - Build prompt to check if user given info is enough to fulfill.
     * - If user info is not enough, ask the user for more information.
     * - If you think we have enough information, formulate a plan of action for the executor to follow.
     */


    // TODO Make these configurable.

    private static String DATA_IDENTIFIER_ASSISTANT = "DataIdentifierAssistant";
    private static String DATA_IDENTIFIER_MODEL = "gpt-4o-mini-2024-07-18";
    private static String DATA_IDENTIFIER_PROMPT = "You will be given the schema for a tool call, and the list of current information. Your task is to determine if the system has enough information to execute desired command. BE VERY CONCISE.";
    private static String DATA_IDENTIFIER_DESCRIPTION = "Assistant that can check function schemas and determine if the system has enough information to execute the function.";
    private static String DATA_IDENTIFIER_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "response": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "key": {
                                "type": "string"
                            },
                            "description": {
                                "type": "string"
                            }
                        }
                    },
                    "description": "Keys and descriptions of missing information if result_type = MISSING. Empty list if result_type = OK."
                },
                "result_type": {
                    "type": "string",
                    "enum": ["OK", "MISSING"]
                }
            },
            "required": ["response", "result_type"],
            "additionalProperties": false
        }
        """;

    private Assistant dataIdentifierAssistant;

    private Assistant getDataIdentifierAssistant() {
        if (dataIdentifierAssistant == null) {
            dataIdentifierAssistant = assistantService.getOrCreateAssistant(getDataIdentifierAssistantDefinition());
        }
        return dataIdentifierAssistant;
    }

    private AssistantDefinition getDataIdentifierAssistantDefinition() {
        return OpenAIAssistantDefinition.builder()
                .name(DATA_IDENTIFIER_ASSISTANT)
                .description(DATA_IDENTIFIER_DESCRIPTION)
                .prompt(DATA_IDENTIFIER_PROMPT)
                .model(DATA_IDENTIFIER_MODEL)
                .schema(DATA_IDENTIFIER_SCHEMA)
                .build();
    }

    /*
     * The prompt would hold the information the user has given.
     * Operations is the list of operations the orchestrator thinks need to be performed.
     */
    private Map<String, String> evaluateInformation(String prompt, List<String> operations) throws AssistantFailedException {
        Map<String, String> returnMap = new HashMap<>();

        // For each operation, I need to get the parameters.
        for (String operation : operations) {
            String parameterDefinition = functionDefinitionsService.getParametersDefinition(operation);

            String finalPrompt = buildDataIdentifierEvaluationPrompt(prompt, parameterDefinition, operation);
            String assistantResponse = assistantService.processRequest(finalPrompt, getDataIdentifierAssistant());

            // Parse the assistant response and populate the returnMap.
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode rootNode = mapper.readTree(assistantResponse);
                String resultType = rootNode.get("result_type").asText();

                if ("MISSING".equals(resultType)) {
                    JsonNode responseArray = rootNode.get("response");
                    if (responseArray != null && responseArray.isArray()) {
                        for (JsonNode itemNode : responseArray) {
                            String key = itemNode.get("key").asText();
                            String description = itemNode.get("description").asText();
                            returnMap.put(key, description);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed parsing response from evaluateInformation assistant.", e);
                throw new RuntimeException(e);
            }
        }

        return returnMap;
    }

    private String buildDataIdentifierEvaluationPrompt(String prompt, String parameterDefinition, String operation) {
        return "User given information: " + prompt + "\n" +
                "System operation: " + operation + "\n" +
                "Operation parameters schema: " + parameterDefinition;
    }

}
