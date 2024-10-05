package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "semantic_thread")
@Builder
@Getter
@ToString@AllArgsConstructor
@NoArgsConstructor
public class SemanticThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

}