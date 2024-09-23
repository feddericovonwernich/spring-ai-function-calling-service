package io.github.feddericovonwernich.spring_ai.function_calling_service.openia.api.assistants;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class AssistantFunctionUtils {

    private static final Gson gson = new Gson();

    public static Map<String, Object> buildParameters(String parameters) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(parameters, type);
    }

}
