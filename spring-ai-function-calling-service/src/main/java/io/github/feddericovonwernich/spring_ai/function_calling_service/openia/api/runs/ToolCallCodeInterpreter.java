package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallCodeInterpreter {
    
    private String input;
    
    private List<ToolCallCodeInterpreterOutput> outputs;

}
