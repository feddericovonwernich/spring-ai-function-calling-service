package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.runs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants.Tool;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.threads.ThreadRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateThreadAndRunRequest {
    
    @JsonProperty("assistant_id")
    private String assistantId;
    
    private ThreadRequest thread;

    private String model;
    
    private String instructions;

    private List<Tool> tools;

    private Map<String, String> metadata;

}
