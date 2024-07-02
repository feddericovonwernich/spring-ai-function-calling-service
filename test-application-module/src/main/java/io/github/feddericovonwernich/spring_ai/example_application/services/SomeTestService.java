package io.github.feddericovonwernich.spring_ai.example_application.services;

import io.github.feddericovonwernich.spring_ai.example_application.models.Person;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.AssistantToolProvider;
import io.github.feddericovonwernich.spring_ai.function_calling_service.annotations.FunctionDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@AssistantToolProvider
@Service
public class SomeTestService {

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


    @FunctionDefinition(name = "TestService_factorial_id",
        description = "Calculates the factorial of a non-negative integer.",
        parameters = """
            {
                "type": "object",
                "properties": {
                    "number": {
                        "type": "integer",
                        "description": "The number to calculate factorial for."
                    }
                },
                "required": ["number"]
            }
        """
    )
    public int factorial(int number) {
        if (number == 0 || number == 1) {
            return 1;
        }
        int result = 1;
        for (int i = 2; i <= number; i++) {
            result *= i;
        }
        return result;
    }


    @FunctionDefinition(
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

}
