
package com.careercopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/job-match")
public class JobMatchController {
    private static final Logger logger =
            LoggerFactory.getLogger(JobMatchController.class);

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;
    private final SkillMatcher skillMatcher;

    public JobMatchController(
            OllamaService ollamaService,
            SkillMatcher skillMatcher) {

        this.ollamaService = ollamaService;
        this.skillMatcher = skillMatcher;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeJobMatch(
            @RequestBody Map<String, String> request) {

        String resume = request.get("resume");
        String jobDescription = request.get("jobDescription");

        if (resume == null || resume.isBlank()
                || jobDescription == null
                || jobDescription.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "Resume and job description are required."));
        }

        try {
            // Step 1: Extract skills dynamically using Ollama
            String extractedSkills = ollamaService.extractSkills(
                    resume, jobDescription);

            // Step 2: Parse the JSON returned by Ollama
            JsonNode skillsJson =
                    objectMapper.readTree(extractedSkills);

            JsonNode resumeSkillsNode =
                    skillsJson.get("resumeSkills");
            JsonNode requiredSkillsNode =
                    skillsJson.get("requiredSkills");

            if (resumeSkillsNode == null
                    || !resumeSkillsNode.isArray()
                    || requiredSkillsNode == null
                    || !requiredSkillsNode.isArray()) {

                return ResponseEntity.internalServerError().body(
                        Map.of("error",
                                "Ollama returned an invalid skills format."));
            }

            List<String> resumeSkills =
                    readSkills(resumeSkillsNode);
            List<String> requiredSkills =
                    readSkills(requiredSkillsNode);

            // Step 3: Normalize skills for case-insensitive comparison

            Map<String, List<String>> matchResult =
                    skillMatcher.matchSkills(resumeSkills, requiredSkills);

            List<String> matchingSkills =
                    matchResult.get("matchingSkills");

            List<String> missingSkills =
                    matchResult.get("missingSkills");


            // Step 4: Return the result
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resumeSkills", resumeSkills);
            result.put("requiredSkills", requiredSkills);
            result.put("matchingSkills", matchingSkills);
            result.put("missingSkills", missingSkills);
            String suggestionsPrompt = """
        You are an AI career coach.

        Based only on the resume, job description, matching skills,
        and missing skills below, provide practical career advice.

        Return these sections:
        1. Skills to Learn
        2. Resume Improvement Suggestions
        3. Project Recommendations
        4. Interview Preparation Topics
        5. Action Plan

        Rules:
        - Do not claim the candidate already knows a missing skill.
        - Never invent experience, projects, or qualifications.
        - Suggest adding skills to the resume only when genuinely learned
          or supported by actual experience.
        - Give specific, beginner-friendly recommendations.

        RESUME:
        %s

        JOB DESCRIPTION:
        %s

        MATCHING SKILLS:
        %s

        MISSING SKILLS:
        %s
        """.formatted(
                    resume.substring(0, Math.min(resume.length(), 5000)),
                    jobDescription.substring(
                            0, Math.min(jobDescription.length(), 3000)),
                    matchingSkills.toString(),
                    missingSkills.toString()
            );

            String suggestions =
                    ollamaService.generateResponse(suggestionsPrompt);

            result.put("improvementSuggestions", suggestions);

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            logger.error("Error during job match analysis", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error",
                            "Unable to extract or parse skills. "
                                    + "Please try again."));
        } catch (InterruptedException e) {
            logger.error("Job match analysis interrupted", e);
            Thread.currentThread().interrupt();

            return ResponseEntity.internalServerError().body(
                    Map.of("error",
                            "Skill analysis was interrupted."));
        }
    }

    private List<String> readSkills(JsonNode skillsNode) {
        List<String> skills = new ArrayList<>();

        for (JsonNode skill : skillsNode) {
            if (skill.isTextual() && !skill.asText().isBlank()) {
                skills.add(skill.asText().trim());
            }
        }

        return skills;
    }
}
