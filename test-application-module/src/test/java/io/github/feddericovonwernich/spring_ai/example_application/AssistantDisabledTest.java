package io.github.feddericovonwernich.spring_ai.example_application;

import io.github.feddericovonwernich.spring_ai.example_application.BasicApplicationIntegrationTest;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Assistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("false")
@TestPropertySource(locations = "classpath:application-false.yml")
class AssistantDisabledTest extends BasicApplicationIntegrationTest {

    @Autowired(required = false)
    AssistantService<Assistant> assistantService;

    @Test
    void contextLoads() {
    }

    @Test
    void testAssistantServiceBeanExists() {
        assertThat(assistantService).isNull();
    }

}
