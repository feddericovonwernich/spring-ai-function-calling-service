package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticThread;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Entity
@Table(name = "assistant_chain_run")
@Data
@Builder
public class AssistantChainRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RunStatus status;

    @ElementCollection
    @CollectionTable(name = "run_messages", joinColumns = @JoinColumn(name = "run_id"))
    @Column(name = "message")
    @Singular
    private List<String> messages;

    @ManyToOne
    @JoinColumn(name = "semantic_thread_id")
    private SemanticThread semanticThread; // TODO This class should not have knowledge about SemanticThread !! Need to find another solution, maybe an entity to link them.

}