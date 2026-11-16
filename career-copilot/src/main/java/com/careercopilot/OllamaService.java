
package com.careercopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Map;

@Service
public class OllamaService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String MODEL_NAME = "llama3.2:1b";

    public OllamaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public String generateResponse(String prompt)
            throws IOException, InterruptedException {

        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                        "num_predict", 2000,
                        "temperature", 0.2
                )
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

        String result = answer.asText();

        return result
                .replace("\\##", "##")
                .replace("\\*", "*")
                .trim();
    }
        private String generateResponse(
            String prompt,
            int numPredict)
            throws IOException, InterruptedException {

            Map<String, Object> requestBody = Map.of(
                    "model", MODEL_NAME,
                    "prompt", prompt,
                    "stream", false,
                    "format", "json",
                    "options", Map.of(
                            "num_predict", numPredict,
                            "temperature", 0.1
                    )
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

        if (answer == null || answer.isNull()
                || answer.asText().isBlank()) {
            throw new IOException(
                    "Ollama returned an empty response."
            );
        }

        return answer.asText().trim()
                .replace("\\##", "##")
                .replace("\\*", "*");
    }

    // Extract skills dynamically from the resume and job description



    // Extract skills dynamically from the job description

    public String extractSkills(String resumeText, String jobDescription)
            throws IOException, InterruptedException {

        String prompt = """
        You are an expert technical recruiter and job description analyzer.

        Your task is to carefully analyze the COMPLETE JOB DESCRIPTION
        and extract all skills explicitly mentioned in it.

        Return ONLY a valid JSON object in this exact structure:

        {
          "requiredSkills": [],
          "preferredSkills": []
        }

        EXTRACTION RULES:

        1. Analyze the entire job description, not just its introduction.

        2. Extract all explicitly mentioned skills, including:
           - Programming languages
           - Frameworks and libraries
           - Databases
           - Development tools
           - Technologies and platforms
           - Technical concepts
           - Analytical skills
           - Soft skills
           - Communication and teamwork skills

        3. Put mandatory skills, essential qualifications,
           must-have skills and core job requirements
           into "requiredSkills".

        4. Put skills explicitly described as preferred,
           optional, bonus or nice-to-have into "preferredSkills".

        5. Skills mentioned under responsibilities should also
           be extracted when they describe a capability needed
           to perform the job.

        6. Do not ignore skills just because they appear
           in the middle or at the end of the description.

        7. Do not invent skills that are not supported
           by the job description.

        8. Do not extract skills from the resume.

        9. Do not put the same skill in both arrays.

        10. Return unique skill names.

        11. Use clear and consistent skill names.
            For example:
            "Problem Solving" -> "Problem-solving"
            "Power BI" -> "Power BI"
            "Springboot" -> "Spring Boot"

        12. If a category is empty, return an empty array.

        13. Return JSON only. Do not include explanations,
            Markdown or text outside the JSON object.

        JOB DESCRIPTION:
        %s
        """.formatted(jobDescription);

        // Step 1: Ask Ollama to analyze the complete JD.
        String response = generateResponse(prompt, 1000);

        // Step 2: Parse the JSON response.
        JsonNode root = objectMapper.readTree(response);

        if (!(root instanceof ObjectNode objectRoot)) {
            throw new IOException(
                    "Ollama returned an invalid JSON object for skills."
            );
        }

        // Step 3: Validate required skills.
        JsonNode requiredNode = objectRoot.get("requiredSkills");

        if (!(requiredNode instanceof ArrayNode)) {
            throw new IOException(
                    "Ollama returned an invalid requiredSkills array."
            );
        }

        // Step 4: Validate preferred skills.
        JsonNode preferredNode = objectRoot.get("preferredSkills");

        if (preferredNode != null && !preferredNode.isArray()) {
            throw new IOException(
                    "Ollama returned an invalid preferredSkills array."
            );
        }

        // Step 5: Clean and remove duplicate skill names.
        ArrayNode requiredSkills = cleanSkillArray(requiredNode);

        ArrayNode preferredSkills = cleanSkillArray(preferredNode);

        // Step 6: Store the cleaned arrays.
        objectRoot.set("requiredSkills", requiredSkills);
        objectRoot.set("preferredSkills", preferredSkills);

        // Step 7: Return structured JSON.
        return objectMapper.writeValueAsString(objectRoot);
    }


    // Remove blank and duplicate skill names.
    private ArrayNode cleanSkillArray(JsonNode skillsNode) {

        ArrayNode cleanedSkills = objectMapper.createArrayNode();

        if (skillsNode == null || !skillsNode.isArray()) {
            return cleanedSkills;
        }

        for (JsonNode skill : skillsNode) {

            if (!skill.isTextual()) {
                continue;
            }

            String skillName = skill.asText().trim();

            if (skillName.isBlank()) {
                continue;
            }

            boolean alreadyExists = false;

            for (JsonNode existingSkill : cleanedSkills) {

                if (existingSkill.asText()
                        .equalsIgnoreCase(skillName)) {

                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                cleanedSkills.add(skillName);
            }
        }

        return cleanedSkills;
    }




    public String generateRagResponse(
            String question,
            String retrievedContext)
            throws IOException, InterruptedException {

        String prompt = """
            You are an AI Career Copilot assistant.

            Answer the user's question using the retrieved context
            provided below.

            Rules:
            - Prioritize information from the retrieved context.
            - Do not invent facts about the user's resume or documents.
            - If the context does not contain enough information,
              clearly say so.
            - Give a clear and helpful answer.

            RETRIEVED CONTEXT:
            %s

            USER QUESTION:
            %s

            ANSWER:
            """.formatted(retrievedContext, question);

        return generateResponse(prompt);
    }

    public String generateResumeProfileResponse(String prompt)
            throws IOException, InterruptedException {

        Map<String, Object> requestBody = Map.of(
                "model", "llama3.2:1b",
                "prompt", prompt,
                "stream", false,
                "format", getResumeProfileSchema(),
                "options", Map.of(
                        "num_predict", 4000,
                        "temperature", 0.1
                )
        );

        String requestJson =
                objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Ollama API error: " + response.body()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());

        String generatedResponse =
                root.path("response").asText("").trim();

        if (generatedResponse.isBlank()) {
            throw new IOException(
                    "Ollama returned an empty resume profile."
            );
        }

        if ("length".equals(root.path("done_reason").asText())) {
            throw new IOException(
                    "Resume analysis exceeded the output token limit."
            );
        }

        return generatedResponse;
    }

    private Map<String, Object> getResumeProfileSchema() {

        Map<String, Object> projectSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "technologies", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of(
                        "name",
                        "description",
                        "technologies"
                ),
                "additionalProperties", false
        );

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "technicalSkills", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "softSkills", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "projects", Map.of(
                                "type", "array",
                                "items", projectSchema
                        ),
                        "education", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "experience", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "achievements", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of(
                        "technicalSkills",
                        "softSkills",
                        "projects",
                        "education",
                        "experience",
                        "achievements"
                ),
                "additionalProperties", false
        );
    }



}
