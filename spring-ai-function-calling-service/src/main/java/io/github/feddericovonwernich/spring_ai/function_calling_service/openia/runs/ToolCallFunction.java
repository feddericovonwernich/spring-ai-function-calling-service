package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.runs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallFunction {
    
    private String name;
    
    private String arguments;
    
    private String output;

}
