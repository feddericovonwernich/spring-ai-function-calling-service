package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.parameter_classes.ParameterClassUtils;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.OpenAIAssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.ServiceAssistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.SystemFunctionWrappedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs.ToolCallFunction;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantFailedException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.function_definitions.FunctionDefinitionsService;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.AssistantFunctionUtils.buildParameters;

@Slf4j
public class ExecutorLink implements AssistantChainLink<Assistant> {

    private static String EXECUTOR_ASSISTANT = "ExecutorAssistant";
    private static String EXECUTOR_MODEL = "gpt-4o-mini-2024-07-18";
    private static String EXECUTOR_PROMPT = "Your task is to execute the plan given you, using the tools registered at your disposal, and return the status of the plan execution. BE VERY CONCISE.";
    private static String EXECUTOR_DESCRIPTION = "Assistant that is supposed to execute the plan to fulfil user request.";
    private static String EXECUTOR_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "response": {
                        "type": "string",
                        "description": "The outcome of having executed the plan. What was done, and how it went. VERY CONCISE OUTPUT."
                    }
                },
                "required": ["response"],
                "additionalProperties": false
            }
            """;

    private static String AGENT_ASSISTANT_MODEL = "gpt-4o-mini-2024-07-18";

    private static String AGENT_ASSISTANT_PROMPT = "Entity agent assistant. You're in charge of executing functions in the system to fulfil user request. Do not guess information. BE VERY CONCISE.";

    private final AssistantService<Assistant> assistantService;
    private final FunctionDefinitionsService functionDefinitionsService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Assistant assistant;
    private final Map<String, ServiceAssistant> serviceAssistants = new HashMap<>();

    public ExecutorLink(AssistantService<Assistant> assistantService,
                        FunctionDefinitionsService functionDefinitionsService) {
        this.assistantService = assistantService;
        this.functionDefinitionsService = functionDefinitionsService;
    }

    @Override
    public String process(AssistantChainRun assistantChainRun, Long lastRunId) throws AssistantFailedException {
        String prompt = assistantChainRun.getMessages().getLast();
        String executorResponse = parseAssistantResponse(assistantService.processRequest(prompt, getAssistant()));
        // TODO I'm thinkin that maybe we could do this more generically. So the user doesn't have to remember to put messages in the list.
        //  But what about SummarizatorLink that doesn't have to append anything? Or it does? It does.
        assistantChainRun.addMessage(executorResponse);
        return executorResponse;
    }

    private String parseAssistantResponse(String assistantJsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(assistantJsonResponse);
            JsonNode responseNode = rootNode.get("response");
            if (responseNode != null && responseNode.isTextual()) {
                return responseNode.asText();
            } else {
                throw new IllegalArgumentException("Invalid or missing 'response' field in JSON.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while parsing executor response.", e);
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

        List<Tool> toolList = getSystemToolList();

        for (String assistantFunctionId : serviceAssistants.keySet()) {
            assistantService.setSystemFunction(getServiceAssistantFunction(), assistantFunctionId);
        }

        return OpenAIAssistantDefinition.builder()
                .name(EXECUTOR_ASSISTANT)
                .description(EXECUTOR_DESCRIPTION)
                .prompt(EXECUTOR_PROMPT)
                .model(EXECUTOR_MODEL)
                .schema(EXECUTOR_SCHEMA)
                .toolList(toolList)
                .build();
    }

    private Function<ToolCallFunction, String> getServiceAssistantFunction() {
        return toolCallFunction -> {
            ServiceAssistant serviceAssistant = serviceAssistants.get(toolCallFunction.getName());
            String instructions = getInstructions(toolCallFunction);
            String agentResponse;
            try {
                agentResponse = assistantService.processRequest(instructions, serviceAssistant.getAssistant());
            } catch (AssistantFailedException e) {
                log.error("Error while running service assistant function. Assistant: {}, instructions: {}",
                        serviceAssistant, instructions, e);
                throw new SystemFunctionWrappedException(e);
            }
            log.debug("Agent: " + serviceAssistant.getAssistantFunctionId() + " | agentResponse: " + agentResponse);
            return agentResponse;
        };
    }

    private static String getInstructions(ToolCallFunction toolCallFunction) {
        JsonElement rootElement = JsonParser.parseString(toolCallFunction.getArguments());
        JsonObject rootObject = rootElement.getAsJsonObject();
        JsonElement instructionsElement = rootObject.get("instructions");
        return instructionsElement.getAsString();
    }

    private List<Tool> getSystemToolList() {
        List<Class<?>> toolProviders = functionDefinitionsService.getToolProviders();
        List<Tool> assistantToolList = new ArrayList<>();

        for (Class<?> toolProvider : toolProviders) {

            Assistant assistant = createOpenAiAssistant(toolProvider);

            String assistantFunctionName = getAssistantFunctionName(toolProvider);

            List<String> assistantFunctions = functionDefinitionsService.getSystemFunctionsNames()
                    .stream()
                    .filter(s -> s.startsWith(assistantFunctionName))
                    .toList();

            ServiceAssistant serviceAssistant = ServiceAssistant.builder()
                    .assistant(assistant)
                    .assistantFunctionId(assistantFunctionName)
                    .functions(assistantFunctions)
                    .build();

            serviceAssistants.put(assistantFunctionName, serviceAssistant);

            AssistantFunction assistantFunction = createAssistantFunction(serviceAssistant);

            assistantToolList.add(new Tool(AssistantToolsEnum.FUNCTION, assistantFunction));
        }

        return assistantToolList;
    }

    private Assistant createOpenAiAssistant(Class<?> toolProvider) {
        List<Tool> toolList = getToolsForProvider(toolProvider);
        String serviceName = toolProvider.getSimpleName();
        String assistantName = getAssistantFunctionName(toolProvider);

        OpenAIAssistantDefinition assistantDefinition = OpenAIAssistantDefinition.builder()
                .description("Specialized assistant to interact with " + serviceName)
                .model(AGENT_ASSISTANT_MODEL)
                .name(assistantName)
                .prompt(AGENT_ASSISTANT_PROMPT)
                .toolList(toolList)
                .build();

        return assistantService.getOrCreateAssistant(assistantDefinition);
    }

    private List<Tool> getToolsForProvider(Class<?> toolProvider) {
        List<Tool> toolList = new ArrayList<>();
        List<AssistantFunction> functions = new ArrayList<>();

        if (toolProvider.getAnnotation(AssistantToolProvider.class) == null) {
            throw new IllegalArgumentException("class must be annotated with AssistantToolProvider");
        }

        Method[] methods = toolProvider.getDeclaredMethods();

        for (Method method : methods) {
            if (method.isSynthetic()) continue;

            FunctionDefinition functionDefinition = method.getDeclaredAnnotation(FunctionDefinition.class);
            if (functionDefinition != null) {
                AssistantFunction assistantFunction = createAssistantProviderFunction(functionDefinition, method);
                if (assistantFunction != null) {
                    log.debug("Loading function: " + assistantFunction.getName());
                    functions.add(assistantFunction);
                }
            }
        }

        functions.forEach(assistantFunction ->
                toolList.add(new Tool(AssistantToolsEnum.FUNCTION, assistantFunction))
        );

        return toolList;
    }

    private AssistantFunction createAssistantProviderFunction(FunctionDefinition functionDefinition, Method method) {
        String functionName = determineFunctionName(functionDefinition, method);

        AssistantFunction.AssistantFunctionBuilder builder = AssistantFunction.builder()
                .name(functionName)
                .description(functionDefinition.description());

        if (functionDefinition.parameters() != null && !functionDefinition.parameters().isEmpty()) {
            builder.parameters(buildParameters(functionDefinition.parameters()));
            builder.strict(false);
        } else if (functionDefinition.parameterClass() != Void.class) {
            builder.parameters(
                buildParameters(ParameterClassUtils.getParameterClassString(functionDefinition.parameterClass()))
            );
        } else {
            throw new IllegalStateException("One of parameters() or parameterClass() should not be null");
        }

        return builder.build();
    }

    private String determineFunctionName(FunctionDefinition functionDefinition, Method method) {
        String functionName = functionDefinition.name();
        if ("unset".equals(functionName)) {
            functionName = method.getDeclaringClass().getSimpleName() + "_" + method.getName();
        }
        return functionName;
    }

    private AssistantFunction createAssistantFunction(ServiceAssistant serviceAssistant) {
        String functionName = serviceAssistant.getAssistantFunctionId();

        String functionDescription = "Contact point to interact with  " + functionName
                + " agent. You should give instructions to this agent through calling this function.";

        List<String> instructions = serviceAssistant.getFunctions();
        StringBuilder sanitizedInstructions = new StringBuilder();

        for (String instruction : instructions) {
            sanitizedInstructions.append(instruction.replace("\"", "\\\"")).append("\\n");
        }

        String parameters = String.format(
            """
                {
                    "type": "object",
                    "properties": {
                        "instructions": {
                            "type": "string",
                            "description": "Natural language instructions to be interpreted by the service agent. Available functions: %s"
                        }
                    },
                    "required": ["instructions"],
                    "additionalProperties": false
                }
            """,
            sanitizedInstructions
        );


        return AssistantFunction.builder()
                .name(functionName)
                .description(functionDescription)
                .parameters(buildParameters(parameters))
                .build();
    }

    private String getAssistantFunctionName(Class<?> toolProvider) {
        return toolProvider.getSimpleName() + "_systemagent";
    }

    /*
     * This link is given a plan by the orchestrator to follow, it has knowledge to make all the tool calls on the system.
     * This is the link that talks to agents.
     *
     * It should give instructions to the agents to follow the plan given by the orchestrator.
     * Once it's done or thinks it cannot follow the plan, gives a response.
     */
}
