# Spring AI Function Calling Service

This library does something very similar to what https://docs.spring.io/spring-ai/reference/api/functions.html does.
More specifically https://docs.spring.io/spring-ai/reference/api/chat/functions/openai-chat-functions.html since I've 
only coded an Open AI implementation for now.

I didn't really like the fact that I need to implement `java.util.Function` classes in order to give an LLM the ability
to call functions in my system. 

This library allows to just annotate a spring bean with an `@AssistantToolProvider` annotation to mark it as a bean that 
should be scanned for tools, and annotate methods within that service with a `@FunctionDefinition` annotation.

If enabled, an Assistant will be created with all the function definitions registered, and a service of type 
`io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService` will be available on the 
Spring context. This service provides functions to process natural language requests.

## Getting started.

Getting started is pretty easy, you just need an Open IA API key, and the following configuration:

`application.yml`
```yaml
assistant:
    enabled: true
    openia:
        apikey: yourApiKey
```

These are regular spring properties, they can be set on an `application.properties` file or even better, through environment
variables, for example: `ASSISTANT_OPENIA_APIKEY`

Now you should have a bean of type `io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService`
in your application context. You can use `@Autowire` or any mechanism you want to get it.

For example, you could create an endpoint like the following to interact with it.

```java
@RestController
@RequestMapping("/ai")
public class NaturalLanguageRestController {
    
    @Autowired
    private AssistantService assistantService;
    
    @GetMapping
    public ResponseEntity<AssistantResponse> processUserRequest(@RequestParam String request) {
        return ResponseEntity.ok(assistantService.processRequest(request));
    }

    @GetMapping("/{threadId}")
    public ResponseEntity<String> processUserRequestOnThread(@RequestParam String request, @PathVariable String threadId) {
        return ResponseEntity.ok(assistantService.processRequest(request, threadId));
    }
    
}
```

### Registering functions with the AssistantService

For this to be useful, first you need to give the assistant the ability to call functions in your system. To do this, 
you'll use `@AssistantToolProvider` annotation to mark a service to be scanned for tools, and `@FunctionDefinition` 
annotations to make a service method available to the assistant.

Example:
```java
@AssistantToolProvider
@Service
public class GreetingService {

    @FunctionDefinition(
        description = "Generates a personalized greeting message.",
        parameters = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the person to greet."
                    },
                    "greeting": {
                        "type": "string",
                        "description": "Optional custom greeting message."
                    }
                },
                "required": ["name"]
            }
        """
    )
    public String generateGreeting(String name, String greeting) {
        if (greeting != null && !greeting.isEmpty()) {
            return greeting + ", " + name + "!";
        } else {
            return "Hello, " + name + "!";
        }
    }
    
}
```

You just need to give a short description of what the annotated method does, and a description of what the parameters 
for this function look like, and what they mean. Keep in mind that the assistant will provide a JSON object containing 
the value of the parameters, so you'll be better off using simple serializable objects.

Try to stay within below template:

```
{
    "type": "object",
    "properties": {
      params...
    },
    "required": ["someParam"]
}
```

If you want to play around with different formats, you may do it, but the service may not know how to convert the JSON 
parameters into the actual parameters. For that you may implement `@ToolParameterAware` and provide your own implementation.

## Configuration options.

### Attributes and Descriptions

| Configuration Value       | Default Value                                                                                                                                               | Description                                                         |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `assistant.name`          | DefaultFunctionCallingAssistant                                                                                                                             | Assigns a name to the assistant.                                    |
| `assistant.openia.model`  | GPT_3_5_TURBO                                                                                                                                               | Specifies the OpenAI model to be used by the assistant.             |
| `assistant.description`   | Service to interact with system through a text chat.                                                                                                        | Provides a description of the assistant's purpose.                  |
| `assistant.prompt`        | Simple Assistant. Must understand user input and decide which operation to perform on the system to fulfill user request. Ask user for missing information. | Sets the initial prompt for the assistant.                          |
| `assistant.resetOnStart`  | false                                                                                                                                                       | Determines whether the assistant should reset its state on startup. |
| `assistant.openia.apikey` | None                                                                                                                                                        | Specifies the API key for accessing OpenAI services.                |

> [!IMPORTANT]  
> Notice the prompt of the assistant can be modified to suit your needs.

## Custom parameter resolution.

TBD.