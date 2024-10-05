package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "open_ai_assistant")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpenAIAssistantReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String openAiAssistantId;

}
