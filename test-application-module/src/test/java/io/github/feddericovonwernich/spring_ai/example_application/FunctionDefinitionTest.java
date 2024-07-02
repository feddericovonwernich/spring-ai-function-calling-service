package io.github.feddericovonwernich.spring_ai.example_application;

import com.theokanning.openai.assistants.Tool;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.OpenIAServiceAutoConfiguration;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("true-with-key")
@TestPropertySource(locations = "classpath:application-true-with-key.yml")
@ImportAutoConfiguration(OpenIAServiceAutoConfiguration.class)
public class FunctionDefinitionTest {

    @Autowired
    private StandardOpenIAAssistantService standardOpenIAAssistantService;

    @Test
    public void testToolScan() {
        try {
            Method getToolsMethod = StandardOpenIAAssistantService.class.getDeclaredMethod("getTools");
            getToolsMethod.setAccessible(true);
            List<Tool> toolList = (List<Tool>) getToolsMethod.invoke(standardOpenIAAssistantService);

            assertEquals(6, toolList.size());

            // Verify automatically generated and manually set names are present.
            for (Tool tool : toolList) {
                if (!tool.getFunction().getName().equals("TestService_factorial_id")
                        && !tool.getFunction().getName().equals("SomeTestService_generateGreeting")
                            && !tool.getFunction().getName().equals("SomeTestService_concatenateList")
                                && !tool.getFunction().getName().equals("SomeTestService_printPersonInfo")
                                    && !tool.getFunction().getName().equals("ConcatListID")
                                            && !tool.getFunction().getName().equals("SomeOtherTestService_printPersonInfo")) {
                    fail();
                }
            }
        } catch (Exception e) {
            fail();
        }
    }

}
