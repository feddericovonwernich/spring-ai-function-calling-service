# Spring AI Function Calling Service

This library does something very similar to what https://docs.spring.io/spring-ai/reference/api/functions.html does.
More specifically https://docs.spring.io/spring-ai/reference/api/chat/functions/openai-chat-functions.html since I've 
only coded an Open AI implementation for now.

I didn't really like the fact that I need to implement `java.util.Function` classes in order to give an LLM the ability
to call functions in my system. 

This library allows to just annotate a service with an `@AssistantToolProvider` annotation to mark it as a bean that 
should be scanned for tools, and annotate methods within that service with a `@FunctionDefinition` annotation.

If enabled, an Assistant will be created with all the function definitions registered, and a service of type 
`io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService` will be available on the 
Spring context. This service provides functions to process natural language requests.

## Getting started.

TBD

## Configuration options.

TBD

## Custom parameter resolution.

TBD