package io.github.feddericovonwernich.spring_ai.example_application.controller;

import io.github.feddericovonwernich.spring_ai.function_calling_service.conditions.AssistantEnabledCondition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.conditions.OpenIAKeyPresentCondition;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantResponse;
import io.github.feddericovonwernich.spring_ai.function_calling_service.spi.AssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@Conditional({OpenIAKeyPresentCondition.class, AssistantEnabledCondition.class})
public class NaturalLanguageRestController {

    @Autowired
    private AssistantService assistantService;

    @GetMapping
    public ResponseEntity<AssistantResponse> processUserRequest(@RequestParam String request) {
        return ResponseEntity.ok(assistantService.processRequest(request));
    }

    @GetMapping("/{threadId}")
    public ResponseEntity<AssistantResponse> processUserRequestOnThread(@RequestParam String request, @PathVariable String threadId) {
        return ResponseEntity.ok(assistantService.processRequest(request));
    }

}
