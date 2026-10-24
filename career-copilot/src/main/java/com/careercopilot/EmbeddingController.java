
package com.careercopilot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/api/embeddings/test")
    public Map<String, Object> testEmbedding(
            @RequestParam(defaultValue = "Java Spring Boot") String text)
            throws IOException, InterruptedException {

        List<Double> embedding =
                embeddingService.generateEmbedding(text);

        return Map.of(
                "inputText", text,
                "vectorDimensions", embedding.size(),
                "firstFiveValues",
                embedding.subList(0, Math.min(5, embedding.size()))
        );
    }
}

