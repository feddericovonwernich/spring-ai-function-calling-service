package io.github.feddericovonwernich.spring_ai.example_application;

import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.StandardOpenIAAssistantService;
import io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("true-with-key")
@TestPropertySource(locations = "classpath:application-true-with-key.yml")
public class FunctionDefinitionTest {

    @Autowired
    private StandardOpenIAAssistantService standardOpenIAAssistantService;

    //@Test
    public void testToolScan() {
        try {
            Method getToolsMethod = StandardOpenIAAssistantService.class.getDeclaredMethod("getTools");
            getToolsMethod.setAccessible(true);
            List<Tool> toolList = (List<Tool>) getToolsMethod.invoke(standardOpenIAAssistantService);

            assertEquals(6, toolList.size());

            // Verify automatically generated and manually set names are present.
            for (Tool tool : toolList) {
                if (!tool.getFunction().getName().equals("TestService_factorial_id")
                        && !tool.getFunction().getName().equals("FunctionDefinitionTestService_generateGreeting")
                            && !tool.getFunction().getName().equals("FunctionDefinitionTestService_concatenateList")
                                && !tool.getFunction().getName().equals("FunctionDefinitionTestService_printPersonInfo")
                                    && !tool.getFunction().getName().equals("ConcatListID")
                                            && !tool.getFunction().getName().equals("ToolParameterAwareTestService_printPersonInfo")) {
                    fail();
                }
            }
        } catch (Exception e) {
            fail();
        }
    }

}
