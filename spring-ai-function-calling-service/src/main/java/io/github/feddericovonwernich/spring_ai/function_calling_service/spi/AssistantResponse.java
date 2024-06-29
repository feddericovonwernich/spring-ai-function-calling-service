package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

public class AssistantResponse {

    private String threadId;

    private String response;

    public AssistantResponse(String threadId, String response) {
        this.threadId = threadId;
        this.response = response;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

}
