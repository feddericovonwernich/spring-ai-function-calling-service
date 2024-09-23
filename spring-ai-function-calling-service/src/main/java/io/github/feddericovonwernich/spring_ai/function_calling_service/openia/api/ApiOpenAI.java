package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.common.DeleteResult;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.common.OpenAiResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.Message;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.MessageFile;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.MessageRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.messages.ModifyMessageRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.runs.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.threads.ThreadRequest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.threads.Thread;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.*;

import java.util.Map;

public interface ApiOpenAI {

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/assistants")
    Single<Assistant> createAssistant(@Body AssistantRequest request);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/assistants/{assistant_id}")
    Single<Assistant> retrieveAssistant(@Path("assistant_id") String assistantId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/assistants/{assistant_id}")
    Single<Assistant> modifyAssistant(@Path("assistant_id") String assistantId, @Body ModifyAssistantRequest request);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @DELETE("/v1/assistants/{assistant_id}")
    Single<DeleteResult> deleteAssistant(@Path("assistant_id") String assistantId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/assistants")
    Single<OpenAiResponse<Assistant>> listAssistants(@QueryMap Map<String, Object> filterRequest);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/assistants/{assistant_id}/files")
    Single<AssistantFile> createAssistantFile(@Path("assistant_id") String assistantId, @Body AssistantFileRequest fileRequest);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/assistants/{assistant_id}/files/{file_id}")
    Single<AssistantFile> retrieveAssistantFile(@Path("assistant_id") String assistantId, @Path("file_id") String fileId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @DELETE("/v1/assistants/{assistant_id}/files/{file_id}")
    Single<DeleteResult> deleteAssistantFile(@Path("assistant_id") String assistantId, @Path("file_id") String fileId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/assistants/{assistant_id}/files")
    Single<OpenAiResponse<AssistantFile>> listAssistantFiles(@Path("assistant_id") String assistantId, @QueryMap Map<String, Object> filterRequest);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/threads")
    Single<Thread> createThread(@Body ThreadRequest request);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}")
    Single<Thread> retrieveThread(@Path("thread_id") String threadId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/threads/{thread_id}")
    Single<Thread> modifyThread(@Path("thread_id") String threadId, @Body ThreadRequest request);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @DELETE("/v1/threads/{thread_id}")
    Single<DeleteResult> deleteThread(@Path("thread_id") String threadId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/threads/{thread_id}/messages")
    Single<Message> createMessage(@Path("thread_id") String threadId, @Body MessageRequest request);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}/messages/{message_id}")
    Single<Message> retrieveMessage(@Path("thread_id") String threadId, @Path("message_id") String messageId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @POST("/v1/threads/{thread_id}/messages/{message_id}")
    Single<Message> modifyMessage(@Path("thread_id") String threadId, @Path("message_id") String messageId, @Body ModifyMessageRequest request);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}/messages")
    Single<OpenAiResponse<Message>> listMessages(@Path("thread_id") String threadId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}/messages")
    Single<OpenAiResponse<Message>> listMessages(@Path("thread_id") String threadId, @QueryMap Map<String, Object> filterRequest);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}/messages/{message_id}/files/{file_id}")
    Single<MessageFile> retrieveMessageFile(@Path("thread_id") String threadId, @Path("message_id") String messageId, @Path("file_id") String fileId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}/messages/{message_id}/files")
    Single<OpenAiResponse<MessageFile>> listMessageFiles(@Path("thread_id") String threadId, @Path("message_id") String messageId);

    @Headers({"OpenAI-Beta: assistants=v2"})
    @GET("/v1/threads/{thread_id}/messages/{message_id}/files")
    Single<OpenAiResponse<MessageFile>> listMessageFiles(@Path("thread_id") String threadId, @Path("message_id") String messageId, @QueryMap Map<String, Object> filterRequest);

    @Headers("OpenAI-Beta: assistants=v2")
    @POST("/v1/threads/{thread_id}/runs")
    Single<Run> createRun(@Path("thread_id") String threadId, @Body RunCreateRequest runCreateRequest);

    @Headers("OpenAI-Beta: assistants=v2")
    @GET("/v1/threads/{thread_id}/runs/{run_id}")
    Single<Run> retrieveRun(@Path("thread_id") String threadId, @Path("run_id") String runId);

    @Headers("OpenAI-Beta: assistants=v2")
    @POST("/v1/threads/{thread_id}/runs/{run_id}")
    Single<Run> modifyRun(@Path("thread_id") String threadId, @Path("run_id") String runId, @Body Map<String, String> metadata);

    @Headers("OpenAI-Beta: assistants=v2")
    @GET("/v1/threads/{thread_id}/runs")
    Single<OpenAiResponse<Run>> listRuns(@Path("thread_id") String threadId, @QueryMap Map<String, String> listSearchParameters);


    @Headers("OpenAI-Beta: assistants=v2")
    @POST("/v1/threads/{thread_id}/runs/{run_id}/submit_tool_outputs")
    Single<Run> submitToolOutputs(@Path("thread_id") String threadId, @Path("run_id") String runId, @Body SubmitToolOutputsRequest submitToolOutputsRequest);


    @Headers("OpenAI-Beta: assistants=v2")
    @POST("/v1/threads/{thread_id}/runs/{run_id}/cancel")
    Single<Run> cancelRun(@Path("thread_id") String threadId, @Path("run_id") String runId);

    @Headers("OpenAI-Beta: assistants=v2")
    @POST("/v1/threads/runs")
    Single<Run> createThreadAndRun(@Body CreateThreadAndRunRequest createThreadAndRunRequest);

    @Headers("OpenAI-Beta: assistants=v2")
    @GET("/v1/threads/{thread_id}/runs/{run_id}/steps/{step_id}")
    Single<RunStep> retrieveRunStep(@Path("thread_id") String threadId, @Path("run_id") String runId, @Path("step_id") String stepId);

    @Headers("OpenAI-Beta: assistants=v2")
    @GET("/v1/threads/{thread_id}/runs/{run_id}/steps")
    Single<OpenAiResponse<RunStep>> listRunSteps(@Path("thread_id") String threadId, @Path("run_id") String runId, @QueryMap Map<String, String> listSearchParameters);

}
