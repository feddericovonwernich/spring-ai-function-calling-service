package io.github.feddericovonwernich.spring_ai.example_application.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.feddericovonwernich.spring_ai.example_application.models.Person;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.ToolParameterAware;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AssistantToolProvider
public class SomeOtherTestService implements ToolParameterAware {

    private final Gson gson = new Gson();

    @FunctionDefinition(
            description = "Prints information about a person.",
            parameters = """
        {
            "type": "object",
            "properties": {
                "person": {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string",
                            "description": "Name of the person."
                        },
                        "age": {
                            "type": "integer",
                            "description": "Age of the person."
                        }
                    },
                    "required": ["name", "age"]
                }
            }
        }
    """
    )
    public void printPersonInfo(Person person) {
        System.out.println("Person Information: " + person);
    }

    @FunctionDefinition(
            name = "ConcatListID",
            description = "Concatenates elements of a list into a single string.",
            parameters = """
        {
            "type": "object",
            "properties": {
                "items": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    },
                    "description": "List of strings to concatenate."
                }
            },
            "required": ["items"]
        }
    """
    )
    public String concatenateList(List<String> items) {
        return String.join(", ", items);
    }

    @Override
    public List<Object> getParametersForFunction(String functionName, String parametersString) {
        JsonObject jsonObject = JsonParser.parseString(parametersString).getAsJsonObject();

        if (functionName.equals("ConcatListID")) {
            List<String> items = gson.fromJson(jsonObject.getAsJsonArray("items"), List.class);
            return List.of(items);
        } else if (functionName.equals("SomeOtherTestService_printPersonInfo")) {
            Person person = gson.fromJson(jsonObject.getAsJsonObject("person"), Person.class);
            return List.of(person);
        } else {
            throw new IllegalArgumentException("Unknown function name");
        }
    }

}
