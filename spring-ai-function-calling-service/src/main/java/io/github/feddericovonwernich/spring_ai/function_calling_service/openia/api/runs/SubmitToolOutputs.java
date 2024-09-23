package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitToolOutputs {
    
    @JsonProperty("tool_calls")
    List<ToolCall> toolCalls;

}
