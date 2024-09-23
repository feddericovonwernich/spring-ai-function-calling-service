package io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssistantChainRunRepository extends JpaRepository<AssistantChainRun, Long> {
}
