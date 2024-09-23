package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants;

import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AssistantFunction {

    private String description;
    
    private String name;
    
    private Map<String, Object> parameters;

    @Builder.Default
    private Boolean strict = true;

}
