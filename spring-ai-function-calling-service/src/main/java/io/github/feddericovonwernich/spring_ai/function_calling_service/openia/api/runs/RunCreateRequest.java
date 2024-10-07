package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RunCreateRequest {

    String assistantId;

    String model;
    
    String instructions;
    
    List<Tool> tools;
    
    Map<String, String> metadata;

    String toolChoice;

}