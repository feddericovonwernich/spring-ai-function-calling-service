package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.ApiOpenAI;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.common.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.Message;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.MessageFile;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.MessageRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.ModifyMessageRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.runs.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.threads.ThreadRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.threads.Thread;
import io.reactivex.rxjava3.core.Single;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceOpenAI {

    private static final Logger log = LoggerFactory.getLogger(ServiceOpenAI.class);

    private static final String BASE_URL = "https://api.openai.com/";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper mapper = defaultObjectMapper();

    private final ApiOpenAI api;
    private final ExecutorService executorService;

    /**
     * Creates a new OpenAiService that wraps ApiOpenAI
     *
     * @param token OpenAi token string "sk-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
     */
    public ServiceOpenAI(final String token) {
        this(token, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new OpenAiService that wraps ApiOpenAI
     *
     * @param token   OpenAi token string "sk-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
     * @param timeout http read timeout, Duration.ZERO means no timeout
     */
    public ServiceOpenAI(final String token, final Duration timeout) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(token, timeout);
        Retrofit retrofit = defaultRetrofit(client, mapper);

        this.api = retrofit.create(ApiOpenAI.class);
        this.executorService = client.dispatcher().executorService();
    }

    /**
     * Creates a new OpenAiService that wraps ApiOpenAI.
     * Use this if you need more customization, but use OpenAiService(api, executorService) if you use streaming and
     * want to shut down instantly
     *
     * @param api ApiOpenAI instance to use for all methods
     */
    public ServiceOpenAI(final ApiOpenAI api) {
        this.api = api;
        this.executorService = null;
    }

    /**
     * Creates a new OpenAiService that wraps ApiOpenAI.
     * The ExecutorService must be the one you get from the client you created the api with
     * otherwise shutdownExecutor() won't work.
     * <p>
     * Use this if you need more customization.
     *
     * @param api             ApiOpenAI instance to use for all methods
     * @param executorService the ExecutorService from client.dispatcher().executorService()
     */
    public ServiceOpenAI(final ApiOpenAI api, final ExecutorService executorService) {
        this.api = api;
        this.executorService = executorService;
    }


    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }

    public static OkHttpClient defaultClient(String token, Duration timeout) {
        return new OkHttpClient.Builder()
                .addInterceptor(new AuthenticationInterceptor(token))
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public static Retrofit defaultRetrofit(OkHttpClient client, ObjectMapper mapper) {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build();
    }

    // TODO Make this configurable.
    private static final int MAX_RETRIES = 3;

    /**
     * Calls the Open AI api, returns the response, and parses error messages if the request fails
     */
    public static <T> T execute(Single<T> apiCall) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                return apiCall.blockingGet();
            } catch (HttpException e) {
                handleHttpException(e);
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SocketTimeoutException) {
                    log.error("SocketTimeoutException, retrying...");
                    attempt++;
                    if (attempt >= MAX_RETRIES) {
                        throw new RuntimeException("Maximum retry limit reached", e);
                    }
                } else if (cause instanceof HttpException) {
                    handleHttpException((HttpException) cause);
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Unexpected error");
    }

    private static void handleHttpException(HttpException e) throws HttpException {
        try {
            if (e.response() == null || e.response().errorBody() == null) {
                throw e;
            }
            String errorBody = e.response().errorBody().string();
            OpenAiError error = mapper.readValue(errorBody, OpenAiError.class);
            throw new OpenAiHttpException(error, e, e.code());
        } catch (IOException ex) {
            // couldn't parse OpenAI error
            throw e;
        }
    }

    // Methods

    public Assistant createAssistant(AssistantRequest request) {
        return execute(api.createAssistant(request));
    }

    public Assistant retrieveAssistant(String assistantId) {
        return execute(api.retrieveAssistant(assistantId));
    }

    public Assistant modifyAssistant(String assistantId, ModifyAssistantRequest request) {
        return execute(api.modifyAssistant(assistantId, request));
    }

    public DeleteResult deleteAssistant(String assistantId) {
        return execute(api.deleteAssistant(assistantId));
    }

    public OpenAiResponse<Assistant> listAssistants(ListSearchParameters params) {
        Map<String, Object> queryParameters = mapper.convertValue(params, new TypeReference<Map<String, Object>>() {
        });
        return execute(api.listAssistants(queryParameters));
    }

    public AssistantFile createAssistantFile(String assistantId, AssistantFileRequest fileRequest) {
        return execute(api.createAssistantFile(assistantId, fileRequest));
    }

    public AssistantFile retrieveAssistantFile(String assistantId, String fileId) {
        return execute(api.retrieveAssistantFile(assistantId, fileId));
    }

    public DeleteResult deleteAssistantFile(String assistantId, String fileId) {
        return execute(api.deleteAssistantFile(assistantId, fileId));
    }

    public OpenAiResponse<AssistantFile> listAssistantFiles(String assistantId, ListSearchParameters params) {
        Map<String, Object> queryParameters = mapper.convertValue(params, new TypeReference<Map<String, Object>>() {
        });
        return execute(api.listAssistantFiles(assistantId, queryParameters));
    }

    public Thread createThread(ThreadRequest request) {
        return execute(api.createThread(request));
    }

    public Thread retrieveThread(String threadId) {
        return execute(api.retrieveThread(threadId));
    }

    public Thread modifyThread(String threadId, ThreadRequest request) {
        return execute(api.modifyThread(threadId, request));
    }

    public DeleteResult deleteThread(String threadId) {
        return execute(api.deleteThread(threadId));
    }

    public Message createMessage(String threadId, MessageRequest request) {
        return execute(api.createMessage(threadId, request));
    }

    public Message retrieveMessage(String threadId, String messageId) {
        return execute(api.retrieveMessage(threadId, messageId));
    }

    public Message modifyMessage(String threadId, String messageId, ModifyMessageRequest request) {
        return execute(api.modifyMessage(threadId, messageId, request));
    }

    public OpenAiResponse<Message> listMessages(String threadId) {
        return execute(api.listMessages(threadId));
    }

    public OpenAiResponse<Message> listMessages(String threadId, ListSearchParameters params) {
        Map<String, Object> queryParameters = mapper.convertValue(params, new TypeReference<Map<String, Object>>() {
        });
        return execute(api.listMessages(threadId, queryParameters));
    }

    public MessageFile retrieveMessageFile(String threadId, String messageId, String fileId) {
        return execute(api.retrieveMessageFile(threadId, messageId, fileId));
    }

    public OpenAiResponse<MessageFile> listMessageFiles(String threadId, String messageId) {
        return execute(api.listMessageFiles(threadId, messageId));
    }

    public OpenAiResponse<MessageFile> listMessageFiles(String threadId, String messageId, ListSearchParameters params) {
        Map<String, Object> queryParameters = mapper.convertValue(params, new TypeReference<Map<String, Object>>() {
        });
        return execute(api.listMessageFiles(threadId, messageId, queryParameters));
    }

    public Run createRun(String threadId, RunCreateRequest runCreateRequest) {
        return execute(api.createRun(threadId, runCreateRequest));
    }

    public Run retrieveRun(String threadId, String runId) {
        return execute(api.retrieveRun(threadId, runId));
    }

    public Run modifyRun(String threadId, String runId, Map<String, String> metadata) {
        return execute(api.modifyRun(threadId, runId, metadata));
    }

    public OpenAiResponse<Run> listRuns(String threadId, ListSearchParameters listSearchParameters) {
        Map<String, String> search = new HashMap<>();
        if (listSearchParameters != null) {
            ObjectMapper mapper = defaultObjectMapper();
            search = mapper.convertValue(listSearchParameters, Map.class);
        }
        return execute(api.listRuns(threadId, search));
    }

    public Run submitToolOutputs(String threadId, String runId, SubmitToolOutputsRequest submitToolOutputsRequest) {
        return execute(api.submitToolOutputs(threadId, runId, submitToolOutputsRequest));
    }

    public Run cancelRun(String threadId, String runId) {
        return execute(api.cancelRun(threadId, runId));
    }

    public Run createThreadAndRun(CreateThreadAndRunRequest createThreadAndRunRequest) {
        return execute(api.createThreadAndRun(createThreadAndRunRequest));
    }

    public RunStep retrieveRunStep(String threadId, String runId, String stepId) {
        return execute(api.retrieveRunStep(threadId, runId, stepId));
    }

}