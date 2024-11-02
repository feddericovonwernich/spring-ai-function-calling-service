package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orchestrator_thread")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    @ElementCollection
    @CollectionTable(name = "user_messages", joinColumns = @JoinColumn(name = "orchestrator_thread_id"))
    @Column(name = "message", columnDefinition = "LONGTEXT")
    @Singular
    List<String> userMessages;

    public void addMessage(String message) {
        /*
         * TODO This also follows the pattern described in AssistantChainRun. Odd.
         */
        List<String> currentList = new ArrayList<>(userMessages);
        currentList.add(message);
        userMessages = currentList;
    }

}
