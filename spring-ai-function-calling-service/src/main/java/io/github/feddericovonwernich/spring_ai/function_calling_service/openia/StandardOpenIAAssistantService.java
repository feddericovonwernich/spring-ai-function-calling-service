package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.common.ListSearchParameters;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.common.OpenAiHttpException;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.common.OpenAiResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.Message;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.MessageContent;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.content.Text;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.MessageRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.threads.Thread;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.runs.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.service.ServiceOpenAI;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.threads.ThreadRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.ServiceAssistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.ToolParameterAware;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.SpringProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AssistantService OpenIA implementation.
 *
 * @author Federico von Wernich
 */
public class StandardOpenIAAssistantService implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(StandardOpenIAAssistantService.class);

    @Value("${assistant.openia.name:DefaultFunctionCallingAssistant}")
    private String assistantName;

    @Value("${assistant.openia.model:gpt-3.5-turbo}")
    private String assistantModel;

    @Value("${assistant.openia.description:Service to interact with system through a text chat.}")
    private String assistantDescription;

    // TODO Update this prompt.
    @Value("${assistant.openia.prompt:Simple Assistant. Must understand user input and decide which operation to perform on the system to fulfill user request. Ask user for missing information.}")
    private String assistantPrompt;

    @Value("${assistant.openia.agent.prompt:Simple Assistant. Must understand user input and decide which operation to perform on the system to fulfill user request. Ask user for missing information.}")
    private String agentAssistantPrompt;

    @Value("${assistant.openia.agent.model:gpt-3.5-turbo}")
    private String agentAssistantModel;

    @Value("${assistant.resetOnStart:false}")
    private boolean resetAssistantOnStartup;


    private final ApplicationContext appContext;

    private final ServiceOpenAI aiService;

    private Assistant orchestatorAssistant;

    private List<ServiceAssistant> serviceAssistants;

    private final ThreadLocal<Boolean> assistantFailed = new ThreadLocal<>();
    private final Gson gson = new Gson();

    public StandardOpenIAAssistantService(ApplicationContext appContext, ServiceOpenAI aiService) {
        this.appContext = appContext;
        this.aiService = aiService;
    }

    @PostConstruct
    private void init() {
        if (resetAssistantOnStartup) {
            if (orchestatorAssistant == null) {
                orchestatorAssistant = fetchAssistants();
                if (orchestatorAssistant != null) {
                    aiService.deleteAssistant(orchestatorAssistant.getId());
                    log.info("Deleted assistant with id: " + orchestatorAssistant.getId());
                    orchestatorAssistant = null;
                }
            }
            if (serviceAssistants == null || serviceAssistants.isEmpty()) {
                serviceAssistants = fetchServiceAssistants();
                if (!serviceAssistants.isEmpty()) {
                    for (ServiceAssistant serviceAssistant : serviceAssistants) {
                        aiService.deleteAssistant(serviceAssistant.getAssistant().getId());
                        log.info("Deleted assistant with id: " + serviceAssistant.getAssistant().getId());
                    }
                    serviceAssistants = null;
                }
            }
        }
    }

    private Assistant getOrchestatorAssistant() {
        if (orchestatorAssistant == null) {
            orchestatorAssistant = fetchAssistants();
            if (orchestatorAssistant == null) {
                orchestatorAssistant = createOrchestatorAssistant();
            }
        }
        return orchestatorAssistant;
    }

    private List<ServiceAssistant> getServiceAssistants() {
        if (serviceAssistants == null || serviceAssistants.isEmpty()) {
            serviceAssistants = fetchServiceAssistants();
            if (serviceAssistants.isEmpty()) {
                serviceAssistants = createServiceAssistants();
            }
        }
        return serviceAssistants;
    }

    private List<ServiceAssistant> fetchServiceAssistants() {
        OpenAiResponse<Assistant> response = aiService.listAssistants(new ListSearchParameters());
        List<Assistant> assistantList = response.getData();
        List<Assistant> filteredList = new ArrayList<>();

        for (Assistant serviceAssistant : assistantList) {
            if (serviceAssistant.getName().endsWith("_serviceAgent")) {
                log.info("ServiceAssistant found: " + serviceAssistant.getName());
                filteredList.add(serviceAssistant);
            }
        }

        if (filteredList.isEmpty()) {
            log.info("No ServiceAssistant found with the suffix '_serviceAgent'.");
        }

        return createServiceAssistants(filteredList);
    }


    @Override
    public AssistantResponse processRequest(String userInput) {
        if (getOrchestatorAssistant() == null) {
            throw new RuntimeException("Unable to get an assistant.");
        }
        // Create the thread to execute the user request.
        Thread thread = createThread();
        String assistantResponse = processRequestOnThread(userInput, thread, getOrchestatorAssistant());
        return new AssistantResponse(thread.getId(), assistantResponse);
    }

    @Override
    public String processRequest(String userInput, String threadId) {
        if (getOrchestatorAssistant() == null) {
            throw new RuntimeException("Unable to get an assistant.");
        }
        // Get the thread where it was executing.
        Thread thread = getThread(threadId);
        if (thread != null) {
            return processRequestOnThread(userInput, thread, getOrchestatorAssistant());
        } else {
            return "Non-existent thread. Use a valid thread id.";
        }
    }

    private String processRequestOnThread(String userInput, Thread thread, Assistant assistant) {
        // Append user request to thread.
        createMessageOnThread(userInput, thread);

        // Assign the thread to the assistant.
        Run run = createRunForThread(thread, assistant);
        Run retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        retrievedRun = waitForRun(thread, run, retrievedRun);

        processActions(retrievedRun, thread, run);

        if (assistantFailed.get() != null && assistantFailed.get()) {
            return "Request was sent, but assistant failed to process it.";
        }

        // Get the response.
        OpenAiResponse<Message> response = aiService.listMessages(thread.getId());

        // Before extracting the message text, ensure that the response and its content are not null
        Optional<String> messageText = response.getData().stream()
                .findFirst()
                .map(Message::getContent)
                .flatMap(content -> content.stream().findFirst())
                .map(MessageContent::getText)
                .map(Text::getValue);

        return messageText.orElse("Error: Couldn't process request.");
    }

    private Thread getThread(String threadId) {
        try {
            return aiService.retrieveThread(threadId);
        } catch (OpenAiHttpException ex) {
            if (ex.statusCode == 404) {
                return null;
            } else {
                throw new RuntimeException("Error while retrieving thread.", ex);
            }
        }
    }

    private void processActions(Run retrievedRun, Thread thread, Run run) {
        if (retrievedRun.getStatus().equals("requires_action")) {
            List<SubmitToolOutputRequestItem> toolOutputRequestItems = getSubmitToolOutputRequestItems(retrievedRun);
            SubmitToolOutputsRequest submitToolOutputsRequest = SubmitToolOutputsRequest.builder()
                    .toolOutputs(toolOutputRequestItems)
                    .build();
            retrievedRun = aiService.submitToolOutputs(retrievedRun.getThreadId(), retrievedRun.getId(), submitToolOutputsRequest);
            retrievedRun = waitForRun(thread, run, retrievedRun);
            processActions(retrievedRun, thread, run);
        }
        if (retrievedRun.getStatus().equals("failed")) {
            log.error(retrievedRun.getLastError().getMessage());
            if (retrievedRun.getLastError().getCode().equals("rate_limit_exceeded")) {
                // TODO Need to retry with exponential backoff.
            }
            assistantFailed.set(true);
        }
    }

    @Nonnull
    private List<SubmitToolOutputRequestItem> getSubmitToolOutputRequestItems(Run retrievedRun) {
        RequiredAction requiredAction = retrievedRun.getRequiredAction();
        List<ToolCall> toolCalls = requiredAction.getSubmitToolOutputs().getToolCalls();
        List<SubmitToolOutputRequestItem> toolOutputRequestItems = new ArrayList<>();
        toolCalls.forEach(toolCall -> {
            String functionCallResponse = executeFunctionCall(toolCall.getFunction());
            SubmitToolOutputRequestItem toolOutputRequestItem = SubmitToolOutputRequestItem.builder()
                    .toolCallId(toolCall.getId())
                    .output(functionCallResponse)
                    .build();
            toolOutputRequestItems.add(toolOutputRequestItem);
        });
        return toolOutputRequestItems;
    }

    private Run createRunForThread(Thread thread, Assistant assistant) {
        RunCreateRequest runCreateRequest = RunCreateRequest.builder()
                .assistantId(assistant.getId())
                .build();
        return aiService.createRun(thread.getId(), runCreateRequest);
    }

    private void createMessageOnThread(String userInput, Thread thread) {
        MessageRequest messageRequest = MessageRequest.builder()
                .content(userInput)
                .build();
        aiService.createMessage(thread.getId(), messageRequest);
    }

    private Thread createThread() {
        ThreadRequest threadRequest = ThreadRequest.builder().build();
        return aiService.createThread(threadRequest);
    }

    @Nonnull
    private Run waitForRun(Thread thread, Run run, Run retrievedRun) {
        while (!(retrievedRun.getStatus().equals("completed"))
                && !(retrievedRun.getStatus().equals("failed"))
                && !(retrievedRun.getStatus().equals("requires_action"))) {
            retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        }
        return retrievedRun;
    }

    private String executeFunctionCall(ToolCallFunction function) {

        if (function.getName().toLowerCase().contains("_serviceagent")) {

            log.debug("Function call name: " + function.getName());
            log.debug("Function call arguments: " + function.getArguments());

            for (ServiceAssistant serviceAssistant : serviceAssistants) {
                // TODO This approach looks brittle, should be more consistent.
                if (function.getName().contains(serviceAssistant.getAssistantFunctionId())) {
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode rootNode = objectMapper.readTree(function.getArguments());
                        JsonNode descriptionNode = rootNode.path("instructions");

                        String instructions = descriptionNode.asText();
                        String agentResponse = processRequestOnThread(instructions, createThread(), serviceAssistant.getAssistant());
                        log.debug("Agent: " + serviceAssistant.getAssistantFunctionId() + " | agentResponse: " + agentResponse);
                        return agentResponse;
                    } catch (JsonProcessingException e) {
                        return e.getMessage();
                    }
                }
            }
        }

        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);
        AtomicReference<String> functionResponse = new AtomicReference<>("");

        beans.values().forEach(bean -> {
            Class<?> beanClass = getBeanClass(bean);
            invokeMatchingFunction(function, bean, beanClass, functionResponse);
        });

        return functionResponse.get();
    }

    private void invokeMatchingFunction(ToolCallFunction function, Object bean, Class<?> beanClass, AtomicReference<String> functionResponse) {
        Method[] methods = beanClass.getDeclaredMethods();

        for (Method method : methods) {
            if (method.isSynthetic()) continue;

            FunctionDefinition functionDefinition = method.getDeclaredAnnotation(FunctionDefinition.class);
            if (functionDefinition != null && isMatchingFunction(functionDefinition, method, function)) {
                executeFunction(bean, method, function, functionResponse);
            }
        }
    }

    private boolean isMatchingFunction(FunctionDefinition functionDefinition, Method method, ToolCallFunction function) {
        String functionDefinitionName = determineFunctionName(functionDefinition, method);
        return functionDefinitionName.equals(function.getName());
    }

    private void executeFunction(Object bean, Method method, ToolCallFunction function, AtomicReference<String> functionResponse) {
        log.debug("Function Name: " + function.getName() + " | Function arguments: " + function.getArguments());

        try {
            List<Object> arguments = getArgumentsForMethod(bean, method, function.getName(), function.getArguments());
            log.debug("Deserialized arguments: " + arguments);

            if (arguments == null) return; // Skip execution if argument parsing failed

            Object result = method.invoke(bean, arguments.toArray());
            log.debug("Execution result: " + result);

            functionResponse.set(result != null ? result.toString() : "null");
        } catch (Exception e) {
            handleExecutionException(e, functionResponse);
        }
    }

    private void handleExecutionException(Exception e, AtomicReference<String> functionResponse) {
        log.error("Error during function execution: {}", e.getMessage(), e);

        if (e instanceof InvocationTargetException targetException) {
            functionResponse.set(targetException.getTargetException().getMessage());
        } else {
            functionResponse.set(e.getMessage());
        }
    }

    private List<Object> getArgumentsForMethod(Object bean, Method method, String functionName, String functionArguments) {
        List<Object> arguments = new ArrayList<>();

        if (bean instanceof ToolParameterAware toolParamAwareBean) {
            return toolParamAwareBean.getParametersForFunction(functionName, functionArguments);
        }

        List<Class<?>> parameterTypesList = Arrays.asList(method.getParameterTypes());
        if (parameterTypesList.isEmpty()) {
            return Collections.emptyList();
        }

        JsonObject jsonObject = gson.fromJson(functionArguments, JsonObject.class);
        if (jsonObject.asMap().keySet().size() != parameterTypesList.size()) {
            String errorMessage = String.format("Parameter count mismatch: expected %d, but got %d", parameterTypesList.size(), jsonObject.asMap().keySet().size());
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }


        int index = 0;
        for (String key : jsonObject.asMap().keySet()) {
            Class<?> argumentType = parameterTypesList.get(index);
            JsonElement jsonElement = jsonObject.get(key);
            String stringParameter;

            if (jsonElement.isJsonObject()) {
                stringParameter = jsonElement.getAsJsonObject().toString();
            } else if (jsonElement.isJsonPrimitive()) {
                stringParameter = jsonElement.getAsJsonPrimitive().toString();
            } else if (jsonElement.isJsonArray()) {
                stringParameter = jsonElement.getAsJsonArray().toString();
            } else {
                throw new UnsupportedOperationException("JsonType not implemented: " + jsonElement);
            }

            arguments.add(gson.fromJson(stringParameter, argumentType));
            index++;
        }

        return arguments;
    }

    private Assistant fetchAssistants() {
        OpenAiResponse<Assistant> response = aiService.listAssistants(new ListSearchParameters());
        List<Assistant> assistantList = response.getData();
        for (Assistant assistant : assistantList) {
            if (assistant.getName().equals(assistantName)) {
                log.info("Assistant found for name: " + assistantName);
                return assistant;
            }
        }
        log.info("Assistant not found.");
        return null;
    }

    private Assistant createOrchestatorAssistant() {
        List<Tool> toolList = getTools(getServiceAssistants());

        try {
            AssistantRequest assistantRequest = AssistantRequest.builder()
                .model(assistantModel)
                .description(assistantDescription)
                .name(assistantName)
                .instructions(assistantPrompt)
                .tools(toolList)
                .build();

            Assistant assistant = aiService.createAssistant(assistantRequest);
            log.info("Created assistant successfully wit ID: " + assistant.getId());
            log.debug("Assistant: " + assistant);
            return assistant;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Illegal model: " + assistantModel);
        }
    }


    private List<ServiceAssistant> createServiceAssistants() {
        List<ServiceAssistant> assistantList = new ArrayList<>();
        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);
        for (Object bean : beans.values()) {
            List<Tool> toolList = getToolsForProvider(bean);
            String serviceName = bean.getClass().getSuperclass().getSimpleName();
            String assistantName = serviceName + "_serviceAgent";
            try {
                AssistantRequest assistantRequest = AssistantRequest.builder()
                        .model(agentAssistantModel)
                        .description("Specialized assistant to interact with " + serviceName)
                        .name(assistantName)
                        .instructions(agentAssistantPrompt)
                        .tools(toolList)
                        .build();

                Assistant assistant = aiService.createAssistant(assistantRequest);
                log.info("Created assistant successfully wit ID: " + assistant.getId());
                log.debug("Assistant: " + assistant);
                assistantList.add(new ServiceAssistant(assistant, assistantName, getAssistantFunctionNames(bean)));
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Illegal model: " + assistantModel);
            }
        }

        return assistantList;
    }

    private List<ServiceAssistant> createServiceAssistants(List<Assistant> assistants) {
        List<ServiceAssistant> assistantList = new ArrayList<>();
        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);
        for (Object bean : beans.values()) {
            String assistantName = bean.getClass().getSuperclass().getSimpleName() + "_serviceAgent";
            try {
                Assistant assistant;
                for (Assistant agent : assistants) { // TODO Ugh, should not be looping here.
                    if (agent.getName().equals(assistantName)) {
                        assistant = agent;
                        log.debug("Creating ServiceAssistant instance: " + assistant);
                        assistantList.add(new ServiceAssistant(assistant, assistantName, getAssistantFunctionNames(bean)));
                        break;
                    }
                }
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Illegal model: " + assistantModel);
            }
        }

        return assistantList;
    }

    public List<String> getAssistantFunctionNames(Object bean) {
        List<String> functionNames = new ArrayList<>();

        if (bean == null) {
            return functionNames;
        }

        Method[] methods = bean.getClass().getDeclaredMethods();

        for (Method method : methods) {
            if (method.isSynthetic()) continue;
            if (method.isAnnotationPresent(FunctionDefinition.class)) {
                functionNames.add(method.getName());
            }
        }

        return functionNames;
    }

    private List<Tool> getTools(List<ServiceAssistant> assistants) {
        List<Tool> toolList = new ArrayList<>();
        for (ServiceAssistant serviceAssistant : assistants) {
            AssistantFunction assistantFunction = createAssistantFunction(serviceAssistant);
            log.info("Loading function: " + assistantFunction.getName());
            toolList.add(new Tool(AssistantToolsEnum.FUNCTION, assistantFunction));
        }
        return toolList;
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

        String parameters = "{\n" +
                "    \"type\": \"object\",\n" +
                "    \"properties\": {\n" +
                "        \"instructions\": {\n" +
                "            \"type\": \"string\",\n" +
                "            \"description\": \"Natural language instructions to be interpreted by the service agent. Available functions:" + sanitizedInstructions+ "\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"required\": [\"instructions\"]\n" +
                "}";

        try {
            return AssistantFunction.builder()
                    .name(functionName)
                    .description(functionDescription)
                    .parameters(buildParameters(parameters))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Error while creating assistant function for {}: {}", functionName, e.getMessage(), e);
            throw new RuntimeException(e); // TODO Handle this better.
        }
    }

    private List<Tool> getToolsForProvider(Object bean) {
        List<Tool> toolList = new ArrayList<>();
        List<AssistantFunction> functions = new ArrayList<>();

        Class<?> beanClass = getBeanClass(bean);
        if (beanClass.getAnnotation(AssistantToolProvider.class) == null) {
            throw new IllegalArgumentException("class must be annotated with AssistantToolProvider");
        }

        Method[] methods = beanClass.getDeclaredMethods();

        for (Method method : methods) {
            if (method.isSynthetic()) continue;

            FunctionDefinition functionDefinition = method.getDeclaredAnnotation(FunctionDefinition.class);
            if (functionDefinition != null) {
                AssistantFunction assistantFunction = createAssistantProviderFunction(functionDefinition, method);
                if (assistantFunction != null) {
                    log.info("Loading function: " + assistantFunction.getName());
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

        try {
            return AssistantFunction.builder()
                .name(functionName)
                .description(functionDefinition.description())
                .parameters(buildParameters(functionDefinition.parameters()))
                .build();
        } catch (JsonProcessingException e) {
            log.error("Error while creating assistant function for {}: {}", functionName, e.getMessage(), e);
            return null; // or throw a more specific exception if needed
        }
    }

    private String determineFunctionName(FunctionDefinition functionDefinition, Method method) {
        String functionName = functionDefinition.name();
        if ("unset".equals(functionName)) {
            functionName = method.getDeclaringClass().getSimpleName() + "_" + method.getName();
        }
        return functionName;
    }


    private Class<?> getBeanClass(Object bean) {
        Class<?> beanClass;
        if (isSpringProxy(bean.getClass())) {
            beanClass = bean.getClass().getSuperclass();
        } else {
            beanClass = bean.getClass();
        }
        return beanClass;
    }

    private boolean isSpringProxy(Class<?> bean) {
        for (AnnotatedType annotatedType : Arrays.stream(bean.getAnnotatedInterfaces()).toList()) {
            if (annotatedType.getType().equals(SpringProxy.class)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> buildParameters(String parameters) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(parameters, new TypeReference<>() {});
    }

}
