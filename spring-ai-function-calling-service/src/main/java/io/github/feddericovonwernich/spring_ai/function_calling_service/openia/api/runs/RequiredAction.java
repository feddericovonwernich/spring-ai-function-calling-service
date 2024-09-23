package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequiredAction {
    
    private String type;

    @JsonProperty("submit_tool_outputs")
    private SubmitToolOutputs submitToolOutputs;

}
