package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import lombok.Data;
import lombok.Getter;

@Data
public class AssistantResponse {

    private String threadId;

    private String response;

    public AssistantResponse(String threadId, String response) {
        this.threadId = threadId;
        this.response = response;
    }

}
