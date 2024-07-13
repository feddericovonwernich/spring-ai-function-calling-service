package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.common;

import lombok.Data;

/**
 * A response when deleting an object
 */
@Data
public class DeleteResult {
    /**
     * The id of the object.
     */
    String id;

    /**
     * The type of object deleted, for example "file" or "model"
     */
    String object;

    /**
     * True if successfully deleted
     */
    boolean deleted;
}
