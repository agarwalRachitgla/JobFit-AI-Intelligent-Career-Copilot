
package com.careercopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<Double> generateEmbedding(String text)
            throws IOException, InterruptedException {

        Map<String, Object> requestBody = Map.of(
                "model", "embeddinggemma",
                "input", text
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Ollama embedding request failed: "
                            + response.body()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode vector = root.path("embeddings").path(0);

        if (!vector.isArray() || vector.isEmpty()) {
            throw new IOException(
                    "Ollama returned an empty embedding."
            );
        }

        List<Double> embedding = new ArrayList<>();

        for (JsonNode value : vector) {
            embedding.add(value.asDouble());
        }

        return embedding;
    }
}
