package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.config;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

@AutoConfigurationPackage(basePackages = {
        "io.github.feddericovonwernich.spring_ai.function_calling_service.openia.links.models",
        "io.github.feddericovonwernich.spring_ai.function_calling_service.spi.chain"
})
public class RepositoriesConfiguration {
}
