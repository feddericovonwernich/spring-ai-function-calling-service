package io.github.feddericovonwernich.spring_ai.function_calling_service.conditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Configuration class that acts as a condition to check if the assistant feature is enabled.
 *
 * <p>This class implements the {@link Condition} interface to determine if a condition is
 * matched based on the application's environment {@link Environment}. It checks for the presence
 * and value of a specific property 'assistant.enabled' to decide if the condition for enabling
 * the assistant functionality is met.</p>
 *
 * <p>If the 'assistant.enabled' property is set to false or not defined, the assistant feature
 * will not be enabled, and an informational log will be printed.</p>
 *
 * @author Federico von Wernich
 */
@Configuration
public class AssistantEnabledCondition implements Condition {

    Logger log = LoggerFactory.getLogger(AssistantEnabledCondition.class);

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        boolean evaluation = Boolean.parseBoolean(env.getProperty("assistant.enabled", "false"));
        if (!evaluation) log.info("Assistant not enabled.");
        return evaluation;
    }

}