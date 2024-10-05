package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrchestratorThreadRepository extends JpaRepository<OrchestratorThread, Long> {

    Optional<OrchestratorThread> findBySemanticThreadAndStatusNot(SemanticThread semanticThread, OrchestratorThreadStatus status);

}
