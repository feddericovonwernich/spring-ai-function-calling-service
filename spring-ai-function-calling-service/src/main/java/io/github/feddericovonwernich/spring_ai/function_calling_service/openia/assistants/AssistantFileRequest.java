package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AssistantFileRequest {

    @JsonProperty("file_id")
    String fileId;

}
