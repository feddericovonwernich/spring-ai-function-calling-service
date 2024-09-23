package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

@Entity
@Table(name = "orchestrator_thread")
@Data
@Builder
public class OrchestratorThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "semantic_thread_id", referencedColumnName = "id")
    private SemanticThread semanticThread;

    @Column(nullable = false)
    private String openAiThreadId;

    @Column(nullable = false)
    private OrchestratorThreadStatus status;

}
