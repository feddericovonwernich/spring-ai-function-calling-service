package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SemanticRunSummaryRepository extends JpaRepository<SemanticRunSummary, Long> {
}
