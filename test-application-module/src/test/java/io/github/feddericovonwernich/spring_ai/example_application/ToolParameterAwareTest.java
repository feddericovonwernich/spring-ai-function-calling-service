package io.github.feddericovonwernich.spring_ai.example_application;

import io.github.feddericovonwernich.spring_ai.example_application.models.Person;
import io.github.feddericovonwernich.spring_ai.example_application.services.FunctionDefinitionTestService;
import io.github.feddericovonwernich.spring_ai.example_application.services.ToolParameterAwareTestService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Assistant;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("true-with-key")
@TestPropertySource(locations = "classpath:application-true-with-key.yml")
public class ToolParameterAwareTest extends BasicApplicationIntegrationTest {

    @Autowired
    private AssistantService<Assistant> assistantService;

    @Autowired
    private ToolParameterAwareTestService toolParameterAwareTestService;

    @Test
    public void testToolCallObjectParam() {
        try {
            Method methodArgument = FunctionDefinitionTestService.class.getDeclaredMethod("printPersonInfo", Person.class);
            String parameters = """
                {"person":{"name":"Alice","age":30}}
            """;

            Method getArgumentsForMethod = StandardOpenIAAssistantService.class.getDeclaredMethod("getArgumentsForMethod", Object.class, Method.class, String.class, String.class);
            getArgumentsForMethod.setAccessible(true);
            List<Object> argumentsList = (List<Object>) getArgumentsForMethod.invoke(assistantService, toolParameterAwareTestService, methodArgument, "SomeOtherTestService_printPersonInfo", parameters);

            Assertions.assertEquals(1, argumentsList.size());
            Assertions.assertTrue(argumentsList.get(0) instanceof Person);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testToolCallListsParam() {
        try {
            Method methodArgument = FunctionDefinitionTestService.class.getDeclaredMethod("concatenateList", List.class);
            String parameters = """
                {"items":["a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z"]}
            """;

            Method getArgumentsForMethod = StandardOpenIAAssistantService.class.getDeclaredMethod("getArgumentsForMethod", Object.class, Method.class, String.class, String.class);
            getArgumentsForMethod.setAccessible(true);
            List<Object> argumentsList = (List<Object>) getArgumentsForMethod.invoke(assistantService, toolParameterAwareTestService, methodArgument, "ConcatListID", parameters);

            Assertions.assertEquals(1, argumentsList.size());
            Assertions.assertTrue(argumentsList.get(0) instanceof List);
        } catch (Exception e) {
            fail();
        }
    }
}
