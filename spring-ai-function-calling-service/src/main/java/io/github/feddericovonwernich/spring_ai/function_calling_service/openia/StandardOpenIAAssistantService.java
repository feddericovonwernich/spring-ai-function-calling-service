package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.common.OpenAiResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.Message;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.MessageContent;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.content.Text;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.MessageRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.threads.Thread;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.service.ServiceOpenAI;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.OpenAIAssistantReference;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.OpenAIAssistantReferenceRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.utils.OpenAiServiceUtils;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.*;
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
import java.util.function.Function;

import static io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.ResponseFormatEnum.JSON_SCHEMA;

/**
 * AssistantService OpenIA implementation.
 *
 * @author Federico von Wernich
 */
public class StandardOpenIAAssistantService implements AssistantService<Assistant> {

    private static final Logger log = LoggerFactory.getLogger(StandardOpenIAAssistantService.class);

    private final ApplicationContext appContext;
    private final ServiceOpenAI aiService;
    private final OpenAIAssistantReferenceRepository openAIAssistantReferenceRepository;

    private final ThreadLocal<Boolean> assistantFailed = new ThreadLocal<>();
    private final ThreadLocal<Boolean> rateLimitHit = new ThreadLocal<>();
    private final Map<String, Function<ToolCallFunction, String>> systemFunctions = new HashMap<>();

    private final Gson gson;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${assistant.resetOnStart:false}")
    private boolean resetOnStart;


    public StandardOpenIAAssistantService(ApplicationContext appContext, ServiceOpenAI aiService, OpenAIAssistantReferenceRepository openAIAssistantReferenceRepository) {
        this(appContext, aiService, openAIAssistantReferenceRepository, new Gson());
    }

    public StandardOpenIAAssistantService(ApplicationContext appContext, ServiceOpenAI aiService, OpenAIAssistantReferenceRepository openAIAssistantReferenceRepository, Gson gson) {
        this.appContext = appContext;
        this.aiService = aiService;
        this.openAIAssistantReferenceRepository = openAIAssistantReferenceRepository;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {
        if (resetOnStart) {
            log.debug("Reset on start is true, cleaning assistants.");
            List<OpenAIAssistantReference> openAIAssistantReferenceList = openAIAssistantReferenceRepository.findAll();
            for (OpenAIAssistantReference openAIAssistantReference : openAIAssistantReferenceList) {
                log.debug("Deleting assistant: {}", openAIAssistantReference);
                try {
                    aiService.deleteAssistant(openAIAssistantReference.getOpenAiAssistantId());
                } catch (Exception e) {
                    log.error("Error while deleting assistant: {}", openAIAssistantReference, e);
                }
                openAIAssistantReferenceRepository.delete(openAIAssistantReference);
            }
        }
    }

    @Override
    public void setSystemFunction(Function<ToolCallFunction, String> function, String functionId) {
        systemFunctions.put(functionId, function);
    }

    @Override
    public Assistant getOrCreateAssistant(AssistantDefinition definition) {
        Assistant assistant;
        Optional<OpenAIAssistantReference> openAIAssistantReferenceOptional
                = openAIAssistantReferenceRepository.findByName(definition.getName());
        if (openAIAssistantReferenceOptional.isEmpty()) {
            assistant = createAssistant(definition);
            OpenAIAssistantReference openAIAssistantReference = OpenAIAssistantReference.builder()
                    .name(definition.getName())
                    .openAiAssistantId(assistant.getId())
                    .build();
            openAIAssistantReferenceRepository.save(openAIAssistantReference);
            log.debug("Saved assistant reference: {}", openAIAssistantReference);
        } else {
            assistant = fetchAssistant(openAIAssistantReferenceOptional.get().getOpenAiAssistantId());
            if (assistant == null) {
                assistant = createAssistant(definition);
            }

        }

        return assistant;
    }

    // TODO Look into making temperature configurable.
    @Override
    public Assistant createAssistant(AssistantDefinition definition) {
        try {
            log.debug("Creating from definition: {}", definition );

            AssistantRequest.AssistantRequestBuilder assistantRequestBuilder = AssistantRequest.builder()
                    .model(definition.getModel())
                    .description(definition.getDescription())
                    .name(definition.getName())
                    .instructions(definition.getPrompt())
                    .temperature(0.00);

            AssistantJsonSchema responseSchema;
            if (definition instanceof OpenAIAssistantDefinition openAIAssistantDefinition) {
                // If there's a schema, we add it.
                if (openAIAssistantDefinition.getSchema() != null) {
                    responseSchema = AssistantJsonSchema.builder()
                            .strict(true)
                            .name(definition.getName() + "_schema")
                            .schema(objectMapper.readTree(openAIAssistantDefinition.getSchema()))
                            .build();

                    assistantRequestBuilder.responseFormat(new ResponseFormat(JSON_SCHEMA, responseSchema));
                }
                // If there's tools, we add them.
                if (openAIAssistantDefinition.getToolList() != null && !openAIAssistantDefinition.getToolList().isEmpty()) {
                    assistantRequestBuilder.tools(openAIAssistantDefinition.getToolList());
                }
            }

            AssistantRequest assistantRequest = assistantRequestBuilder.build();

            Assistant assistant = aiService.createAssistant(assistantRequest);
            log.info("Created assistant successfully wit ID: " + assistant.getId());
            return assistant;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Error while creating assistant.", ex);
        }
    }

    @Override
    public String processRequest(String prompt, Assistant assistant) throws AssistantFailedException {
        return processRequest(prompt, assistant, null);
    }

    @Override
    public String processRequest(String prompt, Assistant assistant, Map<String, ?> context) throws AssistantFailedException {
        log.debug("Assistant: {}, prompt: {}", assistant.getId(), prompt);
        Thread thread = null;
        if (context != null) {
            thread = (Thread) context.get("thread");
        }
        if (thread == null) {
            thread = OpenAiServiceUtils.createNewThread(aiService);
        }

        String assistantResponse = processRequestOnThread(prompt, thread, assistant, false);
        log.debug("Assistant {} Response: {}", assistant.getName(), assistantResponse);
        return assistantResponse;
    }

    @Override
    public String processRequestForceToolCall(String prompt, Assistant assistant, Map<String, ?> context) throws AssistantFailedException {
        Thread thread = null;
        if (context != null) {
            thread = (Thread) context.get("thread");
        }
        if (thread == null) {
            thread = OpenAiServiceUtils.createNewThread(aiService);
        }

        String assistantResponse = processRequestOnThread(prompt, thread, assistant, true);
        log.debug("Assistant {} Response: {}", assistant.getName(), assistantResponse);
        return assistantResponse;
    }

    private String processRequestOnThread(String userInput, Thread thread, Assistant assistant, boolean requireToolCall)  throws AssistantFailedException {
        // Append user request to thread.
        createMessageOnThread(userInput, thread);

        // Assign the thread to the assistant.
        Run run = createRunForThread(thread, assistant, requireToolCall);
        Run retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        retrievedRun = waitForRun(thread, run, retrievedRun);
        processActions(retrievedRun, thread, run);


        if (rateLimitHit.get() != null && rateLimitHit.get()) {
            String errorMessage = "RateLimitHit for: " + assistant.getId();
            rateLimitHit.remove();
            log.error(errorMessage);
            throw new AssistantFailedException(errorMessage, true);
        }

        if (assistantFailed.get() != null && assistantFailed.get()) {
            String errorMessage = "Request was sent, but assistant failed to process it. Assistant Id: " + assistant.getId();
            assistantFailed.remove();
            log.error(errorMessage);
            throw new AssistantFailedException(errorMessage, false);
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

    private void processActions(Run retrievedRun, Thread thread, Run run) throws AssistantFailedException {

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

                log.warn("{} encountered.", errorType);

                if (errorType.equals("Rate limit exceeded")) {
                    rateLimitHit.set(true);
                } else {
                    assistantFailed.set(true);
                }
            }
        }
    }

    @Nonnull
    private List<SubmitToolOutputRequestItem> getSubmitToolOutputRequestItems(Run retrievedRun) throws AssistantFailedException {
        RequiredAction requiredAction = retrievedRun.getRequiredAction();
        List<ToolCall> toolCalls = requiredAction.getSubmitToolOutputs().getToolCalls();
        List<SubmitToolOutputRequestItem> toolOutputRequestItems = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            String functionCallResponse = executeFunctionCall(toolCall.getFunction());
            SubmitToolOutputRequestItem toolOutputRequestItem = SubmitToolOutputRequestItem.builder()
                    .toolCallId(toolCall.getId())
                    .output(functionCallResponse)
                    .build();
            toolOutputRequestItems.add(toolOutputRequestItem);
        }
        return toolOutputRequestItems;
    }

    private Run createRunForThread(Thread thread, Assistant assistant, boolean requireToolCall) {
        RunCreateRequest.RunCreateRequestBuilder runCreateRequestBuilder = RunCreateRequest.builder()
                .assistantId(assistant.getId());
        if (requireToolCall) {
            runCreateRequestBuilder.toolChoice("required");
        }
        return aiService.createRun(thread.getId(), runCreateRequestBuilder.build());
    }

    private void createMessageOnThread(String userInput, Thread thread) {
        MessageRequest messageRequest = MessageRequest.builder()
                .content(userInput)
                .build();
        aiService.createMessage(thread.getId(), messageRequest);
    }

    @Nonnull
    private Run waitForRun(Thread thread, Run run, Run retrievedRun) {
        while (!(retrievedRun.getStatus().equals("completed"))
                && !(retrievedRun.getStatus().equals("failed"))
                && !(retrievedRun.getStatus().equals("requires_action"))) {
            log.trace("Thread ID: {}, Run status: {}", thread.getId(), retrievedRun.getStatus());
            retrievedRun = aiService.retrieveRun(thread.getId(), run.getId());
        }
        return retrievedRun;
    }

    private String executeFunctionCall(ToolCallFunction function) throws AssistantFailedException {

        // Path for annotated classes function calls.

        log.debug("Calling function: {}", function.getName());
        log.debug("Function arguments: {}", function.getArguments());

        // Path if we call a ServiceAgent, I need to do something like this.
        String lowerCaseFunctionName = function.getName().toLowerCase();

        if (lowerCaseFunctionName.contains("_systemagent")) {
            for (String functionId : systemFunctions.keySet()) {
                if (functionId.equalsIgnoreCase(lowerCaseFunctionName)) {
                    try {
                        String response = systemFunctions.get(functionId).apply(function);
                        log.debug("Agent: " + functionId + " | systemAgentResponse: " + response);
                        return response;
                    } catch (SystemFunctionWrappedException e) {
                        log.error("Error while running function: {}", functionId, e.getAssistantFailedException());
                        throw e.getAssistantFailedException();
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

        log.debug("Function response: {}", functionResponse.get()); // TODO Response should not be empty here, it makes the assistant hallucinate.

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
            functionResponse.set("ERROR: Do not re-attempt! Message: "
                    + targetException.getTargetException().getMessage());
        } else {
            functionResponse.set("ERROR: Do not re-attempt! Message: "
                    + e.getMessage());
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

    private Assistant fetchAssistant(String assistantId) {
        // TODO I'm not sure what errors this will throw, looks like it could be handled better.
        try {
            return aiService.retrieveAssistant(assistantId);
        } catch (Exception e) {
            log.error("Error while fetching assistant:", e);
            return null;
        }
    }

    private String determineFunctionName(FunctionDefinition functionDefinition, Method method) {
        String functionName = functionDefinition.name();
        if ("unset".equals(functionName)) {
            functionName = method.getDeclaringClass().getSimpleName() + "_" + method.getName();
        }
        return functionName;
    }


    // TODO Isn't this duplicated?
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

}
