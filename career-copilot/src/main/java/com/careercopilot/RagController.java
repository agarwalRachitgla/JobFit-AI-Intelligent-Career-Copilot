
package com.careercopilot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/api/rag/process")
    public List<Map<String, Object>> processText(
            @RequestBody Map<String, String> request)
            throws IOException, InterruptedException {

        String text = request.get("text");
        String documentName = request.get("documentName");

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Text cannot be empty");
        }

        if (documentName == null || documentName.isBlank()) {
            documentName = "manual-text";
        }

        return ragService.processText(text, documentName);
    }

    @GetMapping("/api/rag/ask")
    public String askQuestion(
            @RequestParam String question,
            @RequestParam String documentName)
            throws IOException, InterruptedException {

        return ragService.askQuestion(question, documentName);
    }
}
