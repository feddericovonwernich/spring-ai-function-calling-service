package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AssistantDefinition {

    private String name;

    private String description;

    private String prompt;

    private String model;

}
