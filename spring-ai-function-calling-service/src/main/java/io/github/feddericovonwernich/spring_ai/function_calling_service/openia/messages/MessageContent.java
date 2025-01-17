package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.content.ImageFile;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.messages.content.Text;
import lombok.Data;


/**
 * Represents the content of a message
 * <p>
 * https://platform.openai.com/docs/api-reference/messages/object
 */
@Data
public class MessageContent {
    /**
     * The content type, either "text" or "image_file"
     */
    String type;

    /**
     * Text content of the message. Only present if type == text
     */
    Text text;

    /**
     * The image content of a message. Only present if type == image_file
     */
    @JsonProperty("image_file")
    ImageFile imageFile;
}
