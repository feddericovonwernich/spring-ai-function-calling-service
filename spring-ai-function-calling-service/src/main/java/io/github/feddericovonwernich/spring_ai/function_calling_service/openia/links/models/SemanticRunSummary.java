package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;

import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRun;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "semantic_run_summary")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SemanticRunSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne
    @JoinColumn(name = "assistant_chain_run_id", referencedColumnName = "id")
    private AssistantChainRun assistantChainRun;

    @ManyToOne
    @JoinColumn(name = "semantic_thread_id", referencedColumnName = "id")
    private SemanticThread semanticThread;

    @Column(name = "summary")
    private String summary;

}
