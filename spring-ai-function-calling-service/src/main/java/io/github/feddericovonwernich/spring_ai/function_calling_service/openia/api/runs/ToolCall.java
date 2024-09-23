package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    
    private String id;
    
    private String type;
    
    @JsonProperty("code_interpreter")
    private ToolCallCodeInterpreter codeInterpreter;

    private Map<String, String> retrieval;

    private ToolCallFunction function;

}
