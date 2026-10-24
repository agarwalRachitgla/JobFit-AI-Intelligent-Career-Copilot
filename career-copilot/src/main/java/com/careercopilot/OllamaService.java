
package com.careercopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class OllamaService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public String generateResponse(String prompt)
            throws IOException, InterruptedException {

        Map<String, Object> requestBody = Map.of(
                "model", "llama3.2:1b",
                "prompt", prompt,
                "stream", false
        );

        String jsonBody =
                objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Ollama returned HTTP status: "
                            + response.statusCode()
            );
        }

        JsonNode jsonResponse =
                objectMapper.readTree(response.body());

        JsonNode answer = jsonResponse.get("response");

        if (answer == null || answer.isNull()) {
            throw new IOException(
                    "Ollama response did not contain an answer."
            );
        }

        return answer.asText();
    }

    // Extract skills dynamically from the resume and job description
    public String extractSkills(
            String resumeText,
            String jobDescription)
            throws IOException, InterruptedException {

        String prompt = """
                Extract technical skills and requirements from the
                resume and job description below.

                Return ONLY valid JSON in this exact structure:
                {
                  "resumeSkills": ["skill names found in resume"],
                  "requiredSkills": ["skills required by job description"]
                }

                Rules:
                - Include only skills explicitly mentioned in each text.
                - Do not assume one skill proves another skill.
                - Preserve the original meaning of each skill.
                - Do not add explanations outside the JSON.
                - If no skills are found, return an empty array.

                RESUME:
                %s

                JOB DESCRIPTION:
                %s
                """.formatted(
                resumeText.substring(
                        0, Math.min(resumeText.length(), 8000)),
                jobDescription.substring(
                        0, Math.min(jobDescription.length(), 5000))
        );

        return generateResponse(prompt);
    }
}
