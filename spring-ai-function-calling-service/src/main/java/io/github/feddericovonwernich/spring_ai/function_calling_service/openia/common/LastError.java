package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastError {
    
    private String code;
    
    private String message;

}
