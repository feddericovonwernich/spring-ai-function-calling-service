package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.runs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunImage {

    @JsonProperty("file_id")
    private String fileId;

}
