package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.ToolParameterAware;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.assistants.*;
import com.theokanning.openai.assistants.AssistantRequest.AssistantRequestBuilder;
import com.theokanning.openai.messages.Message;
import com.theokanning.openai.messages.MessageContent;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.messages.content.Text;
import com.theokanning.openai.runs.*;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.threads.Thread;
import com.theokanning.openai.threads.ThreadRequest;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.SpringProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.theokanning.openai.utils.TikTokensUtil.ModelEnum.valueOf;

/**
 * AssistantService OpenIA implementation.
 *
 * @author Federico von Wernich
 */
public class StandardOpenIAAssistantService implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(StandardOpenIAAssistantService.class);

    @Value("${assistant.name:DefaultFunctionCallingAssistant}")
    private String assistantName;

    @Value("${assistant.openia.model:GPT_3_5_TURBO}")
    private String assistantModel;

    @Value("${assistant.description:Service to interact with system through a text chat.}")
    private String assistantDescription;

    @Value("${assistant.prompt:Simple Assistant. Must understand user input and decide which operation to perform on the system to fulfill user request. Ask user for missing information.}")
    private String assistantPrompt;

    @Value("${assistant.resetOnStart:false}")
    private boolean resetAssistantOnStartup;


    private final ApplicationContext appContext;

    private final OpenAiService service;

    private Assistant assistant;

    private final ThreadLocal<Boolean> assistantFailed = new ThreadLocal<>();
    private final Gson gson = new Gson();

    public StandardOpenIAAssistantService(ApplicationContext appContext, OpenAiService service) {
        this.appContext = appContext;
        this.service = service;
    }

    @PostConstruct
    private void init() {
        if (resetAssistantOnStartup) {
            if (assistant == null) {
                assistant = fetchAssistants();
                if (assistant != null) {
                    service.deleteAssistant(assistant.getId());
                    log.info("Deleted assistant with id: " + assistant.getId());
                    assistant = null;
                }
            }
        }
    }

    private Assistant getAssistant() {
        if (assistant == null) {
            assistant = fetchAssistants();
            if (assistant == null) {
                assistant = createAssistant();
            }
        }
        return assistant;
    }

    @Override
    public AssistantResponse processRequest(String userInput) {
        if (getAssistant() == null) {
            throw new RuntimeException("Unable to get an assistant.");
        }
        // Create the thread to execute the user request.
        Thread thread = createThread();
        String assistantResponse = processRequestOnThread(userInput, thread);
        return new AssistantResponse(thread.getId(), assistantResponse);
    }

    @Override
    public String processRequest(String userInput, String threadId) {
        if (getAssistant() == null) {
            throw new RuntimeException("Unable to get an assistant.");
        }
        // Get the thread where it was executing.
        Thread thread = getThread(threadId);
        if (thread != null) {
            return processRequestOnThread(userInput, thread);
        } else {
            return "Non-existent thread. Use a valid thread id.";
        }
    }

    private String processRequestOnThread(String userInput, Thread thread) {
        // Append user request to thread.
        createMessageOnThread(userInput, thread);

        // Assign the thread to the assistant.
        Run run = createRunForThread(thread);
        Run retrievedRun = service.retrieveRun(thread.getId(), run.getId());
        retrievedRun = waitForRun(thread, run, retrievedRun);

        processActions(retrievedRun, thread, run);

        if (assistantFailed.get() != null && assistantFailed.get()) {
            return "Request was sent, but assistant failed to process it.";
        }

        // Get the response.
        OpenAiResponse<Message> response = service.listMessages(thread.getId());

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
            return service.retrieveThread(threadId);
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
            retrievedRun = service.submitToolOutputs(retrievedRun.getThreadId(), retrievedRun.getId(), submitToolOutputsRequest);
            retrievedRun = waitForRun(thread, run, retrievedRun);
            processActions(retrievedRun, thread, run);
        }
        if (retrievedRun.getStatus().equals("failed")) {
            log.error(retrievedRun.getLastError().getMessage());
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

    private Run createRunForThread(Thread thread) {
        RunCreateRequest runCreateRequest = RunCreateRequest.builder()
                .assistantId(getAssistant().getId())
                .build();
        return service.createRun(thread.getId(), runCreateRequest);
    }

    private void createMessageOnThread(String userInput, Thread thread) {
        MessageRequest messageRequest = MessageRequest.builder()
                .content(userInput)
                .build();
        service.createMessage(thread.getId(), messageRequest);
    }

    private Thread createThread() {
        ThreadRequest threadRequest = ThreadRequest.builder().build();
        return service.createThread(threadRequest);
    }

    @Nonnull
    private Run waitForRun(Thread thread, Run run, Run retrievedRun) {
        while (!(retrievedRun.getStatus().equals("completed"))
                && !(retrievedRun.getStatus().equals("failed"))
                && !(retrievedRun.getStatus().equals("requires_action"))) {
            retrievedRun = service.retrieveRun(thread.getId(), run.getId());
        }
        return retrievedRun;
    }

    private String executeFunctionCall(ToolCallFunction function) {
        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);
        AtomicReference<String> functionResponse = new AtomicReference<>("");

        beans.values().forEach(bean -> {
            Class<?> beanClass = getBeanClass(bean);
            Method[] methods = beanClass.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isSynthetic()) continue;
                FunctionDefinition functionDefinition = method.getDeclaredAnnotation(FunctionDefinition.class);
                if (functionDefinition != null) {
                    String functionDefinitionName = determineFunctionName(functionDefinition, method);
                    if (functionDefinitionName.equals(function.getName())) {
                        log.debug("Function arguments: " + function.getArguments());
                        try {
                            List<Object> arguments
                                    = getArgumentsForMethod(bean, method, function.getName(), function.getArguments());
                            if (arguments == null) return; // Skip execution if argument parsing failed
                            Object result = method.invoke(bean, arguments.toArray());
                            log.debug("Execution result: " + result);
                            functionResponse.set(result != null ? result.toString() : "null");
                        } catch (Exception  e) {
                            log.error("Error during function execution: {}", e.getMessage(), e);
                            if (e instanceof InvocationTargetException targetException) {
                                functionResponse.set(targetException.getTargetException().getMessage());
                            } else  {
                                functionResponse.set(e.getMessage());
                            }
                        }
                    }
                }
            }
        });

        return functionResponse.get();
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
        OpenAiResponse<Assistant> response = service.listAssistants(new ListSearchParameters());
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

    private Assistant createAssistant() {
        List<Tool> toolList = getTools();

        try {
            AssistantRequestBuilder requestBuilder = AssistantRequest.builder()
                    .model(valueOf(assistantModel).getName())
                    .description(assistantDescription)
                    .name(assistantName)
                    .instructions(assistantPrompt)
                    .tools(toolList);

            Assistant assistant = service.createAssistant(requestBuilder.build());
            log.info("Created assistant successfully wit ID: " + assistant.getId());
            return assistant;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Illegal model: " + assistantModel);
        }
    }

    private List<Tool> getTools() {
        List<Tool> toolList = new ArrayList<>();
        List<AssistantFunction> functions = new ArrayList<>();
        Map<String, Object> beans = appContext.getBeansWithAnnotation(AssistantToolProvider.class);

        for (Object bean : beans.values()) {
            Class<?> beanClass = getBeanClass(bean);
            Method[] methods = beanClass.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isSynthetic()) continue;

                FunctionDefinition functionDefinition = method.getDeclaredAnnotation(FunctionDefinition.class);
                if (functionDefinition != null) {
                    AssistantFunction assistantFunction = createAssistantFunction(functionDefinition, method);
                    if (assistantFunction != null) {
                        log.info("Loading function: " + assistantFunction.getName());
                        functions.add(assistantFunction);
                    }
                }
            }
        }

        functions.forEach(assistantFunction ->
                toolList.add(new Tool(AssistantToolsEnum.FUNCTION, assistantFunction))
        );

        return toolList;
    }

    private AssistantFunction createAssistantFunction(FunctionDefinition functionDefinition, Method method) {
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
