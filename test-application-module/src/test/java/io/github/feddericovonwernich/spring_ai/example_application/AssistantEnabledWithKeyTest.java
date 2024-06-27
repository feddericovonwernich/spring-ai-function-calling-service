package io.github.feddericovonwernich.spring_ai.example_application;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.OpenIAServiceAutoConfiguration;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("true-with-key")
@TestPropertySource(locations = "classpath:application-true-with-key.yml")
@ImportAutoConfiguration(OpenIAServiceAutoConfiguration.class)
class AssistantEnabledWithKeyTest {

    @Autowired
    StandardOpenIAAssistantService assistantService;

    @Test
    void contextLoads() {
    }

    @Test
    void testAssistantServiceBeanExists() {
        assertThat(assistantService).isNotNull();
    }

}
