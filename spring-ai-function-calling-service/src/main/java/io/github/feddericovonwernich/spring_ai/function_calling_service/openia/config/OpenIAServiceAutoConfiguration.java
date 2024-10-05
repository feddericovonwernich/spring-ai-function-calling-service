package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.config;

import io.github.feddericovonwernich.spring_ai.function_calling_service.conditions.AssistantEnabledCondition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.conditions.OpenIAKeyPresentCondition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Assistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.service.ServiceOpenAI;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.ExecutorLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.OrchestratorLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.SemanticContextCheckLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.SummarizatorLink;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.OpenAIAssistantReferenceRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.OrchestratorThreadRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticRunSummaryRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models.SemanticThreadRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantChainImpl;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.async.*;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChain;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain.AssistantChainRunRepository;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.function_definitions.FunctionDefinitionsService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.function_definitions.FunctionDefinitionsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
@AutoConfigurationPackage(basePackages = {
        "io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models",
        "io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain"
})
@Conditional({OpenIAKeyPresentCondition.class, AssistantEnabledCondition.class})
public class OpenIAServiceAutoConfiguration {

    @Value("${assistant.openia.apikey}")
    private String openIaApiKey;

    @Autowired
    private SemanticThreadRepository semanticThreadRepository;

    @Autowired
    private AssistantChainRunRepository assistantChainRunRepository;

    @Autowired
    private SemanticRunSummaryRepository semanticRunSummaryRepository;

    @Autowired
    private OrchestratorThreadRepository orchestratorThreadRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public ServiceOpenAI serviceOpenAI() {
        return new ServiceOpenAI(openIaApiKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public AssistantService<Assistant> assistantService(ApplicationContext applicationContext,
                                                        ServiceOpenAI serviceOpenAI,
                                                        OpenAIAssistantReferenceRepository openAIAssistantReferenceRepository) {
        return new StandardOpenIAAssistantService(applicationContext, serviceOpenAI, openAIAssistantReferenceRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AssistantServiceRequestQueue assistantServiceRequestQueue() {
        return new InMemoryAssistantServiceRequestQueue(assistantChainRunRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AssistantServiceResponseQueue assistantServiceResponseQueue() {
        return new InMemoryAssistantServiceResponseQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    public FunctionDefinitionsService functionDefinitionsService(ApplicationContext applicationContext) {
        return new FunctionDefinitionsServiceImpl(applicationContext);
    }

    @Bean
    public SemanticContextCheckLink semanticContextCheckLink(AssistantService<Assistant> assistantService) {
        return new SemanticContextCheckLink(assistantChainRunRepository, semanticThreadRepository,
                semanticRunSummaryRepository, assistantService);
    }

    // TODO If I try to wire FunctionDefinitionsService bean here through the method arguments, it fails to start up, says something about a circular reference, why?
    @Bean
    public OrchestratorLink orchestratorLink(AssistantService<Assistant> assistantService, ServiceOpenAI serviceOpenAI) {
        return new OrchestratorLink(assistantService, functionDefinitionsService(applicationContext), orchestratorThreadRepository, serviceOpenAI);
    }

    @Bean
    public ExecutorLink executorLink(AssistantService<Assistant> assistantService, FunctionDefinitionsService functionDefinitionsService) {
        return new ExecutorLink(assistantService, functionDefinitionsService);
    }

    @Bean
    public SummarizatorLink summarizatorLink(AssistantService<Assistant> assistantService) {
        return new SummarizatorLink(assistantService, semanticRunSummaryRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AssistantChain assistantChain(SemanticContextCheckLink semanticContextCheckLink,
                                         OrchestratorLink orchestratorLink,
                                         ExecutorLink executorLink,
                                         SummarizatorLink summarizatorLink) {
        AssistantChain assistantChain = new AssistantChainImpl(assistantChainRunRepository);
        assistantChain.addLink(semanticContextCheckLink);
        assistantChain.addLink(orchestratorLink);
        assistantChain.addLink(executorLink);
        assistantChain.addLink(summarizatorLink);
        return assistantChain;
    }

    @Bean
    @ConditionalOnMissingBean
    public AssistantServiceRequestConsumer assistantServiceRequestConsumer(AssistantServiceRequestQueue assistantServiceRequestQueue,
                                                                           AssistantServiceResponseQueue assistantServiceResponseQueue,
                                                                           AssistantChain assistantChain) {
        // TODO Max threads should be configurable.
        return new AssistantServiceRequestConsumer(assistantServiceRequestQueue,
                assistantServiceResponseQueue, assistantChain, 1);
    }

}
