package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.feddericovonwernich.spring_ai.function_calling_service.ParameterClassUtils;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.ParameterClass;
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
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.SpringProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants.ResponseFormatEnum.JSON_SCHEMA;

/**
 * AssistantService OpenIA implementation.
 *
 * @author Federico von Wernich
 */
public class StandardOpenIAAssistantService implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(StandardOpenIAAssistantService.class);

    @Value("${assistant.openia.name:DefaultFunctionCallingAssistant}")
    private String assistantName;

    @Value("${assistant.openia.model:gpt-4o-2024-08-06}")
    private String assistantModel;

    @Value("${assistant.openia.description:Service to interact with system through a text chat.}")
    private String assistantDescription;

    // TODO Re-check this prompt.
    @Value("${assistant.openia.prompt:You are the orchestrator assistant. Your task is to gather enough information from the user to be able to hand it off to one of the agent assistants through a function call and perform the action the user wants to do.}")
    private String assistantPrompt;

    // TODO Re-check this prompt.
    @Value("${assistant.openia.agent.prompt:Entity agent assistant. Must understand receive orchestrator input and decide which operation to perform on the system to fulfill user request. Check the given information against the tools schema and instruct the orchestrator to ask the user for more information if needed. Do not guess any IDs, if you need any ID to reference another object, ask for it.}")
    private String agentAssistantPrompt;

    @Value("${assistant.openia.agent.model:gpt-4o-mini-2024-07-18}")
    private String agentAssistantModel;

    // TODO Re-check this prompt.
    @Value("${assistant.openia.identifier.prompt:Entity recognition from a subset of entities. Your task is to identify if the user request is talking about any of the possible entities defined in the schema. If the user request is not talking about any of the entities declared in the schema, your response must be null string.}")
    private String domainObjectIdentifierPrompt;

    @Value("${assistant.openia.identifier.model:gpt-4o-mini-2024-07-18}")
    private String domainObjectIdentifierAssistantModel;

    @Value("${assistant.openia.identifier.name:DefaultIdentifierAssistant}")
    private String domainObjectIdentifierAssistantName;

    @Value("#{'${assistant.scan.packages:}'.split(',')}")
    private List<String> scanPackages = new ArrayList<>();

    @Value("${assistant.resetOnStart:false}")
    private boolean resetAssistantOnStartup;

    private final ApplicationContext appContext;

    private final ServiceOpenAI aiService;

    private Assistant domainObjectIdentifierAssistant;

    private Assistant orchestatorAssistant;

    private List<ServiceAssistant> serviceAssistants;

    private Set<Class<?>> parameterClasses;

    private final ThreadLocal<Boolean> assistantFailed = new ThreadLocal<>();
    private final Gson gson;

    private final ConcurrentHashMap<String, List<String>> threadContextEntities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> agentThreadsForThreadIdMap = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentThreadIdTL = new ThreadLocal<>();


    public StandardOpenIAAssistantService(ApplicationContext appContext, ServiceOpenAI aiService) {
        this(appContext, aiService, new Gson());
    }

    public StandardOpenIAAssistantService(ApplicationContext appContext, ServiceOpenAI aiService, Gson gson) {
        this.appContext = appContext;
        this.aiService = aiService;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {
        this.parameterClasses = scanForAnnotatedClasses(scanPackages, ParameterClass.class);
        if (resetAssistantOnStartup) {
            if (orchestatorAssistant == null) {
                orchestatorAssistant = fetchAssistant(assistantName);
                if (orchestatorAssistant != null) {
                    aiService.deleteAssistant(orchestatorAssistant.getId());
                    log.info("Deleted assistant with id: {} | name: {} ", orchestatorAssistant.getId(),
                            orchestatorAssistant.getName());
                    orchestatorAssistant = null;
                }
            }
            if (serviceAssistants == null || serviceAssistants.isEmpty()) {
                serviceAssistants = fetchServiceAssistants();
                if (!serviceAssistants.isEmpty()) {
                    for (ServiceAssistant serviceAssistant : serviceAssistants) {
                        aiService.deleteAssistant(serviceAssistant.getAssistant().getId());
                        log.info("Deleted assistant with id: {} | name: {} ", serviceAssistant.getAssistant().getId(),
                                serviceAssistant.getAssistant().getName());
                    }
                    serviceAssistants = null;
                }
            }
            if (domainObjectIdentifierAssistant == null) {
                domainObjectIdentifierAssistant = fetchAssistant(domainObjectIdentifierAssistantName);
                if (domainObjectIdentifierAssistant != null) {
                    aiService.deleteAssistant(domainObjectIdentifierAssistant.getId());
                    log.info("Deleted assistant with id: {} | name: {} ", domainObjectIdentifierAssistant.getId(),
                            domainObjectIdentifierAssistant.getName());
                    domainObjectIdentifierAssistant = null;
                }
            }
        }
    }

    private Assistant getOrchestatorAssistant() {
        if (orchestatorAssistant == null) {
            orchestatorAssistant = fetchAssistant(assistantName);
            if (orchestatorAssistant == null) {
                orchestatorAssistant = createOrchestatorAssistant();
            }
        }
        return orchestatorAssistant;
    }

    private Assistant getIdentifierAssistant() {
        if (domainObjectIdentifierAssistant == null) {
            domainObjectIdentifierAssistant = fetchAssistant(domainObjectIdentifierAssistantName);
            if (domainObjectIdentifierAssistant == null) {
                domainObjectIdentifierAssistant = createDomainObjectIdentifierAssistant();
            }
        }
        return domainObjectIdentifierAssistant;
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
        String assistantResponse = processRequest(userInput, thread.getId());
        return new AssistantResponse(thread.getId(), assistantResponse);
    }

    @Override
    public String processRequest(String userInput, String threadId) {

        currentThreadIdTL.set(threadId);

        try {

            // TODO Need to handle failed requests, like socketTimeout many times or 500 error.

            if (getIdentifierAssistant() == null) {

                // TODO Maybe it's not that bad and we can continue execution without this assistant.

                throw new RuntimeException("Unable to get identifier assistant");
            }
            if (getOrchestatorAssistant() == null) {
                throw new RuntimeException("Unable to get an assistant.");
            }

            // Get the thread where it was executing.
            Thread thread = getThread(threadId);
            if (thread != null) {
                List<String> entities = getEntitiesFromUserRequest(userInput);

                /*
                 * Here I'm just appending the definitions to the thread, so the orchestrator assistant has context.
                 */
                List<String> entitiesDefinition = getJsonDefinitions(entities);

                entitiesDefinition.forEach(definition -> {

                    // Parse the name of the object in properties
                    String objectName = parseObjectName(definition);

                    // Check if the definition is already in the thread
                    if (!threadContainsDefinition(thread, objectName)) {

                        // Parse keys within the definition's properties
                        List<String> parsedKeys = parseDefinitionKeys(definition);

                        List<String> threadEntities = threadContextEntities.computeIfAbsent(threadId, k -> new ArrayList<>());
                        threadEntities.add(objectName);
                        threadEntities.addAll(parsedKeys);

                        String message = "Entity definition for context: " + definition;
                        log.debug("Appending message | " + message);
                        createMessageOnThread(message, thread);
                    }
                });

                // TODO Right now, we avoid adding a definition that was previously added to the same thread while the program is running
                //  Note that this lives in memory, so restarting the application would trigger adding the context again.
                //  Possible improvements:
                //   - Count tokens so we know when definitions are about to leave the context.
                //   - Keep a record of how many times an entity was mentioned and try to keep most mentioned ones up to date.

                try {
                    String orchestratorResponseString = processRequestOnThread(userInput, thread, getOrchestatorAssistant());

                    // TODO Could have a global ObjectMapper
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(orchestratorResponseString);

                    // Extract the answer
                    String answer = rootNode.path("answer")
                            .asText();

                    log.debug("Orchestrator answer: " + answer);

                    // Extract the reasoning steps and log them
                    String reasoningSteps = rootNode.path("reasoning_steps")
                            .asText();

                    log.debug("Reasoning steps: " + reasoningSteps);

                    return answer;
                } catch (AssistantFailedException a) {
                    return a.getMessage();
                } catch (Exception e) {
                    log.error("Failed to parse orchestrator response", e);
                    return "Error processing the request. Error: " + e.getMessage();
                }
            } else {
                return "Non-existent thread. Use a valid thread id.";
            }
        } finally {
            currentThreadIdTL.remove();
        }
    }

    // Utility method to check if a definition is already in the thread
    private boolean threadContainsDefinition(Thread thread, String objectName) {
        List<String> threadEntities = threadContextEntities.get(thread.getId());
        if (threadEntities != null && !threadEntities.isEmpty()) {
            boolean contains =  threadEntities.contains(objectName);
            log.debug("thread with ID: {} contains entity {}: {}", thread.getId(), objectName, contains);
            return contains;
        }
        log.debug("threadEntities null or empty for thread {}", thread.getId());
        return false;
    }

    private final ObjectMapper objectMapper = new ObjectMapper(); // TODO Could be global

    // Utility method to parse the object name from the definition
    private String parseObjectName(String definition) {
        try {
            // Parse the JSON string into a JsonNode
            JsonNode rootNode = objectMapper.readTree(definition);

            // Assuming the object name is the first key under "properties"
            JsonNode propertiesNode = rootNode.get("properties");
            if (propertiesNode != null && propertiesNode.fieldNames().hasNext()) {
                return propertiesNode.fieldNames().next(); // Return the first key
            }

        } catch (IOException e) {
            log.error("Error parsing object name from definition", e);
        }
        return null;
    }

    // Utility method to parse the keys from the definition's properties
    private List<String> parseDefinitionKeys(String definition) {
        List<String> keys = new ArrayList<>();
        try {
            // Parse the JSON string into a JsonNode
            JsonNode rootNode = objectMapper.readTree(definition);

            // Get the "properties" node
            JsonNode propertiesNode = rootNode.get("definitions");
            if (propertiesNode != null) {
                Iterator<String> fieldNames = propertiesNode.fieldNames();
                while (fieldNames.hasNext()) {
                    keys.add(fieldNames.next()); // Add each key under "properties"
                }
            }

        } catch (IOException e) {
            log.error("Error parsing definition keys from definition", e);
        }
        return keys;
    }

    private List<String> getJsonDefinitions(List<String> entities) {
        List<String> definitionsList = new ArrayList<>();
        entities.forEach(entity -> {
            parameterClasses.forEach(parameterClass -> {
                if (parameterClass.getSimpleName().equalsIgnoreCase(entity)) {
                    definitionsList.add(ParameterClassUtils.getParameterClassString(parameterClass));
                }
            });
        });
        return definitionsList;
    }

    private List<String> getEntitiesFromUserRequest(String userInput) {

        // TODO For now we'll just create one thread every time we want to do entity recognition.
        //  Look for something like a chat completion using an assistant.

        log.debug("About to perform entity recognition on user message: {}", userInput);

        Thread thread = createThread();
        createMessageOnThread(userInput, thread);
        Run run = createRunForThread(thread, getIdentifierAssistant());
        Run retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        waitForRun(thread, run, retrievedRun);

        // Get the response.
        OpenAiResponse<Message> response = aiService.listMessages(thread.getId());

        // Before extracting the message text, ensure that the response and its content are not null
        Optional<String> messageText = response.getData().stream()
                .findFirst()
                .map(Message::getContent)
                .flatMap(content -> content.stream().findFirst())
                .map(MessageContent::getText)
                .map(Text::getValue);

        return messageText.map(text -> {
            try {
                log.debug("Identifier assistant response: " + text);

                // TODO Could use a global object mapper.
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(text);

                // Extract and return identified_entities as a List of Strings
                return StreamSupport.stream(rootNode.path("identified_entities").spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to parse identifier response", e);
                return Collections.<String>emptyList();
            }
        }).orElse(Collections.emptyList());

    }

    private String processRequestOnThread(String userInput, Thread thread, Assistant assistant)  throws AssistantFailedException {
        // Append user request to thread.
        createMessageOnThread(userInput, thread);

        // Assign the thread to the assistant.
        Run run = createRunForThread(thread, assistant);
        Run retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        retrievedRun = waitForRun(thread, run, retrievedRun);

        processActions(retrievedRun, thread, run);

        if (assistantFailed.get() != null && assistantFailed.get()) {
            String errorMessage = "Request was sent, but assistant failed to process it. Assistant Id: " + assistant.getId();
            assistantFailed.remove();
            log.error(errorMessage);
            throw new AssistantFailedException(errorMessage);
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
        // TODO Make these configurable.
        int maxRetries = 5;
        int retryCount = 0;
        long backoffTime = 5000L; // Initial backoff time in milliseconds

        while (true) {
            if (retrievedRun.getStatus().equals("requires_action")) {
                List<SubmitToolOutputRequestItem> toolOutputRequestItems = getSubmitToolOutputRequestItems(retrievedRun);
                SubmitToolOutputsRequest submitToolOutputsRequest = SubmitToolOutputsRequest.builder()
                        .toolOutputs(toolOutputRequestItems)
                        .build();
                retrievedRun = aiService.submitToolOutputs(retrievedRun.getThreadId(), retrievedRun.getId(),
                        submitToolOutputsRequest);
                retrievedRun = waitForRun(thread, run, retrievedRun);
                processActions(retrievedRun, thread, run);
            }

            if (retrievedRun.getStatus().equals("failed")) {
                log.error("Run failed. Message: {}", retrievedRun.getLastError().getMessage());
                log.warn(retrievedRun.getLastError().getCode());

                if (retrievedRun.getLastError().getCode().equals("rate_limit_exceeded") ||
                        retrievedRun.getLastError().getCode().equals("server_error")) {

                    String errorType = retrievedRun.getLastError().getCode().equals("rate_limit_exceeded") ? "Rate limit exceeded" : "Server error";

                    if (retryCount < maxRetries) {
                        try {
                            log.warn("{} encountered. Retrying in {} ms...", errorType, backoffTime);
                            java.lang.Thread.sleep(backoffTime);
                            backoffTime *= 2; // Exponentially increase the backoff time
                            retryCount++;
                            continue; // Retry the loop
                        } catch (InterruptedException e) {
                            log.error("Backoff interrupted", e);
                            java.lang.Thread.currentThread().interrupt();
                        }
                    } else {
                        log.error("Max retries reached due to {}. Giving up.", errorType);
                    }
                }

                assistantFailed.set(true);
                break; // Exit the loop if failed with no retry or other errors
            }

            break; // Exit the loop if no further action is required or after retries
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
            log.debug("Thread ID: {}, Run status: {}", thread.getId(), retrievedRun.getStatus());
            retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        }
        return retrievedRun;
    }

    private String executeFunctionCall(ToolCallFunction function) {
        if (function.getName().toLowerCase().contains("_serviceagent")) {

            log.debug("Calling service Agent: " + function.getName());
            log.debug("Agent call arguments: " + function.getArguments());

            for (ServiceAssistant serviceAssistant : serviceAssistants) {

                // TODO This approach looks brittle, should be more consistent.

                if (function.getName().contains(serviceAssistant.getAssistantFunctionId())) {
                    JsonElement rootElement = JsonParser.parseString(function.getArguments());
                    JsonObject rootObject = rootElement.getAsJsonObject();
                    JsonElement descriptionElement = rootObject.get("instructions");

                    String instructions = descriptionElement.getAsString();

                    // TODO Here too I'm creating a new thread for each assistant call,
                    //  could look into optimizing or make different assistants use the same thread.

                    String agentResponse = null;
                    try {

                        // TODO OW_TODO Here we are maintaining threads for each agent on memory.
                        //  These should be persisted to database.

                        // Get or create the agents thread map for this thread.
                        Map<String, String> agentThreadsMap = agentThreadsForThreadIdMap.computeIfAbsent(currentThreadIdTL.get(),
                                k -> new HashMap<>());

                        // Get or create the thread for the current assistant on this thread.
                        String agentThreadId = agentThreadsMap.computeIfAbsent(serviceAssistant.getAssistant().getId(),
                                k -> {
                            Thread agentThread = createThread();
                            log.debug("Created thread {} for assistant {} on main thread {}.",
                                    agentThread.getId(),
                                    serviceAssistant.getAssistant().getId(),
                                    currentThreadIdTL.get());
                            return agentThread.getId();
                        });

                        Thread agentThread = getThread(agentThreadId);

                        agentResponse = processRequestOnThread(instructions, agentThread, serviceAssistant.getAssistant());
                    } catch (AssistantFailedException e) {
                        log.error("Agent assistant failed: " + e.getMessage());
                        agentResponse = e.getMessage();
                    }
                    log.debug("Agent: " + serviceAssistant.getAssistantFunctionId() + " | agentResponse: " + agentResponse);
                    return agentResponse;
                }
            }
        }

        log.debug("Calling function: {}", function.getName());
        log.debug("Function arguments: {}", function.getArguments());

        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);
        AtomicReference<String> functionResponse = new AtomicReference<>("");

        beans.values().forEach(bean -> {
            Class<?> beanClass = getBeanClass(bean);
            invokeMatchingFunction(function, bean, beanClass, functionResponse);
        });

        log.debug("Function response: {}", functionResponse.get());

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
            String errorMessage = String.format("Parameter count mismatch: expected %d, but got %d",
                    parameterTypesList.size(), jsonObject.asMap().keySet().size());
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

    private Assistant fetchAssistant(String assistantName) {
        // TODO OWL_TODO Probably need to cache this list.

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

        // TODO We can re-use one of these objects.
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            AssistantJsonSchema orchestratorJsonSchema = AssistantJsonSchema.builder()
                    .strict(true)
                    .name("orchestrator_response_schema")
                    .schema(objectMapper.readTree(ORCHESTRATOR_RESPONSE_SCHEMA))
                    .build();

            AssistantRequest assistantRequest = AssistantRequest.builder()
                .model(assistantModel)
                .description(assistantDescription)
                .name(assistantName)
                .instructions(assistantPrompt)
                .tools(toolList)
                .responseFormat(new ResponseFormat(JSON_SCHEMA, orchestratorJsonSchema))
                .temperature(0.00)
                .build();

            log.debug("Orchestrator prompt: {}", assistantPrompt);

            Assistant assistant = aiService.createAssistant(assistantRequest);
            log.info("Created assistant successfully wit ID: " + assistant.getId());
            return assistant;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Illegal model: " + assistantModel);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse schemaString: " + ORCHESTRATOR_RESPONSE_SCHEMA);
        }
    }

    static String IDENTIFIER_RESPONSE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "identified_entities": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "enum": %s
                  }
                }
              },
              "required": ["identified_entities"],
              "additionalProperties": false
            }
            """;

    static String ORCHESTRATOR_RESPONSE_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "reasoning_steps": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        },
                        "description": "The reasoning steps leading to the final conclusion."
                    },
                    "answer": {
                        "type": "string",
                        "description": "The final answer, taking into account the reasoning steps. Either asking the user for more information, or communicating the outcome of an action."
                    }
                },
                "required": ["reasoning_steps", "answer"],
                "additionalProperties": false
            }
            """;

    public static String getIdentifierJsonSchema(List<String> knownValues) {
        // Create a StringJoiner to join the enum values with commas
        StringJoiner enumValues = new StringJoiner(", ", "[", "]");
        for (String value : knownValues) {
            enumValues.add("\"" + value + "\"");
        }

        // One of the values should always be null to allow for the assistant to say there weren't any entities.
        enumValues.add("\"null\"");

        // Build the JSON schema string
        return IDENTIFIER_RESPONSE_SCHEMA.formatted(enumValues.toString());
    }

    private Assistant createDomainObjectIdentifierAssistant() {
        String schemaString = getIdentifierJsonSchema(
                parameterClasses.stream()
                        .map(Class::getSimpleName)
                        .collect(Collectors.toList())
        );

        // TODO We can re-use one of these objects.
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            AssistantJsonSchema identifierJsonSchema = AssistantJsonSchema.builder()
                    .strict(true)
                    .name("identifier_response_schema")
                    .schema(objectMapper.readTree(schemaString))
                    .build();

            AssistantRequest assistantRequest = AssistantRequest.builder()
                    .model(domainObjectIdentifierAssistantModel)
                    .description("Assistant to do entity recognition in a users prompt.")
                    .name(domainObjectIdentifierAssistantName)
                    .instructions(domainObjectIdentifierPrompt)
                    .responseFormat(new ResponseFormat(JSON_SCHEMA, identifierJsonSchema))
                    .temperature(0.00)
                    .build();

            log.debug("Identifier Assistant prompt: {}" , domainObjectIdentifierPrompt);

            Assistant assistant = aiService.createAssistant(assistantRequest);
            log.info("Created identifier assistant successfully wit ID: " + assistant.getId());
            return assistant;
        } catch (IllegalArgumentException ex) {
            // TODO This error should be more generic, we don't know if that's the cause of the exception.
            throw new RuntimeException("Illegal model: " + domainObjectIdentifierAssistantModel);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse schemaString: " + schemaString);
        }
    }

    private Set<Class<?>> scanForAnnotatedClasses(List<String> packageNames, Class<? extends Annotation> annotation) {
        Set<Class<?>> allAnnotatedClasses = new HashSet<>();

        for (String packageName : packageNames) {
            log.debug("Scanning packageName: " + packageName);
            Reflections reflections = new Reflections(
                    new ConfigurationBuilder().forPackage(packageName).addScanners(Scanners.TypesAnnotated)
            );
            Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(annotation);
            log.debug("Found annotated classes: " + annotatedClasses);
            allAnnotatedClasses.addAll(annotatedClasses);
        }

        // Iterate over the set and process each class with ParameterClassUtils.getParameterClassString(clazz)
        Iterator<Class<?>> iterator = allAnnotatedClasses.iterator();
        while (iterator.hasNext()) {
            Class<?> clazz = iterator.next();
            try {
                // Attempt to generate the parameter class string
                String parameterClassString = ParameterClassUtils.getParameterClassString(clazz);
                log.debug("Generated parameter class string for class: " + clazz.getName());
            } catch (StackOverflowError e) {
                // Log the error and remove the class from the set
                log.error("StackOverflowError encountered for class: " + clazz.getName() + ". Removing from the set.");
                iterator.remove();
            }
        }

        return allAnnotatedClasses;
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
                        .temperature(0.00)
                        .tools(toolList)
                        .build();

                log.debug("AgentAssistant prompt: {}", agentAssistantPrompt);
                log.debug("Tool list: {}", toolList);

                Assistant assistant = aiService.createAssistant(assistantRequest);
                log.info("Created assistant successfully wit ID: " + assistant.getId());
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

        // TODO Can probably add a description for each function. We do have the descriptions.

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
        AssistantFunction.AssistantFunctionBuilder builder = AssistantFunction.builder()
            .name(functionName)
            .description(functionDefinition.description());
        if (functionDefinition.parameters() != null && !functionDefinition.parameters().isEmpty()) {
            builder.parameters(buildParameters(functionDefinition.parameters()));
            builder.strict(false);
        } else if (functionDefinition.parameterClass() != Void.class) {
            builder.parameters(
                    buildParameters(
                            ParameterClassUtils.getParameterClassString(functionDefinition.parameterClass())
                    )
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

    private Map<String, Object> buildParameters(String parameters) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(parameters, type);
    }

}
