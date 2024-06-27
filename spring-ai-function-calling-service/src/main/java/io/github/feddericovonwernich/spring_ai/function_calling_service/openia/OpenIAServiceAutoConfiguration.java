package io.github.feddericovonwernich.spring_ai.function_calling_service.openia;

import io.github.feddericovonwernich.spring_ai.function_calling_service.conditions.AssistantEnabledCondition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.conditions.OpenIAKeyPresentCondition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Auto-configuration class for integrating OpenAI services.
 * <p>This class is conditionally loaded when both the OpenIA API key is present
 * and the assistant functionality is enabled within the application context.</p>
 * <p>It defines beans for interacting with OpenAI services, enabling the use of AI functions
 * such as text completion and other language model tasks within the Spring application.</p>
 *
 * @see OpenIAKeyPresentCondition
 * @see AssistantEnabledCondition
 */
@AutoConfiguration
@Conditional({OpenIAKeyPresentCondition.class, AssistantEnabledCondition.class})
public class OpenIAServiceAutoConfiguration {

    @Value("${assistant.openia.apikey}")
    private String openIaApiKey;

    @Bean
    public OpenAiService openAiService() {
        return new OpenAiService(openIaApiKey);
    }

    @Bean
    public AssistantService assistantService(ApplicationContext appContext) {
        return new StandardOpenIAAssistantService(appContext, openAiService());
    }

}
