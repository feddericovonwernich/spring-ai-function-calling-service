package io.github.feddericovonwernich.spring_ai.function_calling_service.spi;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.assistants.Assistant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class ServiceAssistant {

    Assistant assistant;

    String assistantFunctionId;

    List<String> functions;

}
