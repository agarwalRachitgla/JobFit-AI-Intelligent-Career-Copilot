
package com.careercopilot.resume;

import com.careercopilot.OllamaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResumeAnalysisService {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        this.objectMapper = new ObjectMapper();
    }

    public List<String> findSkills(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return new ArrayList<>();
        }

        String prompt = """
                Extract technical skills explicitly mentioned in this resume.

                Include programming languages, frameworks, libraries, databases,
                developer tools, technologies, and relevant technical concepts.

                Rules:
                1. Only include skills supported by the resume text.
                2. Do not invent skills or infer unmentioned skills.
                3. Include new and uncommon technologies if mentioned.
                4. Avoid duplicate skills.
                5. Return only valid JSON in this format:
                {"skills": ["Java", "React.js", "Docker"]}

                Resume text:
                """ + resumeText.substring(0, Math.min(resumeText.length(), 10000));

        try {
            String response = ollamaService.generateResponse(prompt);
            JsonNode root = parseSkillsResponse(response);
            JsonNode skillsNode = root.get("skills");

            if (skillsNode == null || !skillsNode.isArray()) {
                throw new IllegalStateException(
                        "AI returned an invalid skills response."
                );
            }

            List<String> skills = new ArrayList<>();

            for (JsonNode skill : skillsNode) {
                if (skill.isTextual() && !skill.asText().isBlank()) {
                    String value = skill.asText().trim();

                    if (!skills.contains(value)) {
                        skills.add(value);
                    }
                }
            }

            return skills;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new IllegalStateException(
                    "Unable to extract skills using AI.", e
            );
        }
    }


    private JsonNode parseSkillsResponse(String response)
            throws IOException {

        int start = response.indexOf('{');

        if (start < 0) {
            throw new IOException("No JSON object found in Ollama response.");
        }

        // Try each closing brace, starting from the first one.
        for (int end = start + 1; end <= response.length(); end++) {
            if (response.charAt(end - 1) != '}') {
                continue;
            }

            String candidate = response.substring(start, end);

            try {
                JsonNode root = objectMapper.readTree(candidate);

                if (root != null
                        && root.has("skills")
                        && root.get("skills").isArray()) {
                    return root;
                }
            } catch (IOException ignored) {
                // Try the next closing brace.
            }
        }

        throw new IOException(
                "Ollama returned invalid JSON. Please retry the request."
        );
    }


}
