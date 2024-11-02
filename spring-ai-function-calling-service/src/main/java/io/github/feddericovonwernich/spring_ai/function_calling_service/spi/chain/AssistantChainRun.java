package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticThread;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assistant_chain_run")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
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
    @Column(name = "message", columnDefinition = "LONGTEXT")
    @Singular
    private List<String> messages = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "semantic_thread_id")
    private SemanticThread semanticThread; // TODO This class should not have knowledge about SemanticThread !! Need to find another solution, maybe an entity to link them.

    @OneToOne(fetch = FetchType.EAGER)
    private AssistantChainRun retryFor;

    public void addMessage(String message) {
        /*
         * TODO Alright this is a little weird, even if AssistantChainRunImpl.runThroughChain is annotated with transactional,
         *  Spring context won't let me add elements to the list of messages directly, this is a workaround, but original behavior might be a bug
         */
        List<String> currentList = new ArrayList<>(messages);
        currentList.add(message);
        messages = currentList;
    }

}