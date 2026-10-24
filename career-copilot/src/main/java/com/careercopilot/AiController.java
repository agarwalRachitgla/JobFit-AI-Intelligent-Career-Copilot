
package com.careercopilot;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final OllamaService ollamaService;

    public AiController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> request) {

        String prompt = request.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Prompt cannot be empty"));
        }

        try {
            String answer = ollamaService.generateResponse(prompt);

            return ResponseEntity.ok(
                    Map.of("response", answer));

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error",
                            "Unable to get a response from Ollama. "
                                    + "Check that Ollama is running."
                    ));
        }
    }
}

