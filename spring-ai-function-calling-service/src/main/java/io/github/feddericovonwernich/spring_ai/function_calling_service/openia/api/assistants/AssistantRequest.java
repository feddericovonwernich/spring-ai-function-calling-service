package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AssistantRequest {

    /**
     * ID of the model to use
     */
    @NonNull
    String model;

    /**
     * The name of the assistant. The maximum length is 256 characters.
     */
    String name;

    /**
     * The description of the assistant.
     */
    String description;

    /**
     * The system instructions that the assistant uses.
     */
    String instructions;

    /**
     * A list of tools enabled on the assistant.
     */
    List<Tool> tools;

    /**
     * A list of file IDs attached to this assistant.
     */
    @JsonProperty("file_ids")
    List<String> fileIds;

    /**
     * Set of 16 key-value pairs that can be attached to an object.
     * Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.
     */
    Map<String, String> metadata;

    /**
     * Sampling temperature to use, between 0 and 2.
     * Higher values like 0.8 will make the output more random,
     * while lower values like 0.2 will make it more focused and deterministic.
     */
    Double temperature;

    /**
     * An alternative to sampling with temperature, called nucleus sampling,
     * where the model considers the results of the tokens with top_p probability mass.
     * 0.1 means only the tokens comprising the top 10% probability mass are considered.
     */
    @JsonProperty("top_p")
    Double topP;

    /**
     * Specifies the format that the model must output.
     * Compatible with GPT-4o, GPT-4 Turbo, and all GPT-3.5 Turbo models since gpt-3.5-turbo-1106.
     */
    @JsonProperty("response_format")
    ResponseFormat responseFormat;
}
