package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.runs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallCodeInterpreterOutput {
    
    private String type;
    
    private String logs;
    
    private RunImage image;

}