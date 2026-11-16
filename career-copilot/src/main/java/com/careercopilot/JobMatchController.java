package com.careercopilot;

import com.careercopilot.resume.ResumeAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.careercopilot.resume.ResumeProfile;

@RestController
@RequestMapping("/api/job-match")
public class JobMatchController {

    private static final Logger logger =
            LoggerFactory.getLogger(JobMatchController.class);

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;
    private final SkillMatcher skillMatcher;
    private final ResumeAnalysisService resumeAnalysisService;

    public JobMatchController(
            OllamaService ollamaService,
            SkillMatcher skillMatcher,
            ResumeAnalysisService resumeAnalysisService) {

        this.ollamaService = ollamaService;
        this.skillMatcher = skillMatcher;
        this.resumeAnalysisService = resumeAnalysisService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeJobMatch(
            @RequestBody Map<String, String> request) {

        String resume = request.get("resume");
        String jobDescription = request.get("jobDescription");

        if (resume == null || resume.isBlank()
                || jobDescription == null || jobDescription.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "Resume and job description are required."));
        }

        try {
            // Step 1: Extract skills from the job description.
            String extractedSkills =
                    ollamaService.extractSkills(resume, jobDescription);

            if (extractedSkills == null || extractedSkills.isBlank()) {
                logger.error("Ollama returned an empty skills response.");

                return ResponseEntity.internalServerError().body(
                        Map.of("error",
                                "Ollama returned an empty response. Please retry."));
            }

            logger.debug("Raw Ollama skills response: {}", extractedSkills);

            // Step 2: Parse the skills JSON.
            JsonNode skillsJson = parseSkillsJson(extractedSkills);
            JsonNode requiredSkillsNode = skillsJson.get("requiredSkills");

            if (requiredSkillsNode == null
                    || !requiredSkillsNode.isArray()) {

                logger.error(
                        "Ollama response is missing the requiredSkills array.");

                return ResponseEntity.internalServerError().body(
                        Map.of("error",
                                "Invalid required skills format from AI. Please retry."));
            }

            // Step 3: Analyze the complete resume.
            ResumeProfile resumeProfile =
                    resumeAnalysisService.analyzeResume(resume);

            // Extract technical skills & soft skills for matching.
            List<String> resumeSkills = new ArrayList<>(resumeProfile.technicalSkills());
            resumeSkills.addAll(resumeProfile.softSkills());

            List<String> requiredSkills = readSkills(requiredSkillsNode);

            logger.info("Extracted resume skills: {}", resumeSkills);
            logger.info("Required skills detected: {}", requiredSkills);

            if (requiredSkills.isEmpty()) {
                logger.warn("Ollama extracted no required skills.");

                return ResponseEntity.badRequest().body(
                        Map.of("error",
                                "No job requirements were detected. Please check the job description and retry."));
            }

            // Step 4: Match resume skills against job requirements.
            Map<String, List<String>> matchResult =
                    skillMatcher.matchSkills(resumeSkills, requiredSkills);

            List<String> matchingSkills =
                    matchResult.getOrDefault(
                            "matchingSkills", List.of());

            List<String> missingSkills =
                    matchResult.getOrDefault(
                            "missingSkills", List.of());

            logger.info("Matching skills: {}", matchingSkills);
            logger.info("Missing skills: {}", missingSkills);
            String resumeProfileJson =
                    objectMapper.writeValueAsString(resumeProfile);
            logger.info("===== JOB MATCH DEBUG =====");
            logger.info("Required Skills: {}", requiredSkills);
            logger.info("Matching Skills: {}", matchingSkills);
            logger.info("Missing Skills: {}", missingSkills);

            logger.info("Actual Resume Projects: {}",
                    resumeProfile.projects().stream()
                            .map(ResumeProfile.Project::name)
                            .toList());

            logger.info("Resume Profile JSON: {}", resumeProfileJson);

            // Step 5: Generate job-specific recommendations.



            // Step 5: Generate deterministic job-specific recommendations.
            String suggestions = buildRecommendations(
                    resumeProfile,
                    requiredSkills,
                    matchingSkills,
                    missingSkills
            );


            // Step 6: Return the existing frontend field names.
            Map<String, Object> result = new LinkedHashMap<>();

            result.put("resumeSkills", resumeSkills);
            result.put("requiredSkills", requiredSkills);
            result.put("matchingSkills", matchingSkills);
            result.put("missingSkills", missingSkills);
            result.put("improvementSuggestions", suggestions);

            return ResponseEntity.ok(result);

        } catch (JsonProcessingException e) {
            logger.error(
                    "Ollama returned invalid or incomplete JSON during skill extraction.",
                    e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error",
                            "AI returned invalid or incomplete skill data. Check the backend console and retry."));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Job match analysis was interrupted.", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Skill analysis was interrupted."));

        } catch (IOException e) {
            logger.error("I/O error during job match analysis.", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error",
                            "Unable to process the AI response. Please retry."));

        } catch (Exception e) {
            logger.error("Unexpected error during job matching.", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Unexpected error during job matching."));
        }
    }

    private JsonNode parseSkillsJson(String response)
            throws JsonProcessingException {

        String cleaned = response.trim();

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst(
                    "^```(?:json)?\\s*", "");

            cleaned = cleaned.replaceFirst(
                    "\\s*```\\s*$", "");
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start < 0 || end < start) {
            throw new JsonProcessingException(
                    "No complete JSON object found in Ollama response.") {};
        }

        return objectMapper.readTree(
                cleaned.substring(start, end + 1));
    }

    private List<String> readSkills(JsonNode skillsNode) {
        Set<String> uniqueSkills = new LinkedHashSet<>();

        for (JsonNode skill : skillsNode) {
            if (skill.isTextual() && !skill.asText().isBlank()) {
                uniqueSkills.add(skill.asText().trim());
            }
        }

        return new ArrayList<>(uniqueSkills);
    }
    private String buildRecommendations(
            ResumeProfile profile,
            List<String> requiredSkills,
            List<String> matchingSkills,
            List<String> missingSkills) {

        StringBuilder result = new StringBuilder();

        // 1. Skills to Learn
        result.append("## Skills to Learn\n\n");

        if (missingSkills.isEmpty()) {
            result.append("No skill gaps were identified from this job description.\n");
        } else {
            for (String skill : missingSkills) {
                result.append("- **")
                        .append(skill)
                        .append("**: ")
                        .append(getLearningGuidance(skill))
                        .append("\n");
            }
        }

        // 2. Resume Improvement Suggestions
        result.append("\n## Resume Improvement Suggestions\n\n");

        if (!matchingSkills.isEmpty()) {
            result.append("- Highlight your verified matching skills: ")
                    .append(String.join(", ",
                            matchingSkills.subList(
                                    0, Math.min(6, matchingSkills.size()))))
                    .append(" in your resume's technical skills section.\n");
        }

        if (!profile.projects().isEmpty()) {
            ResumeProfile.Project project = profile.projects().get(0);

            result.append("- Give your **")
                    .append(project.name())
                    .append("** project a clear description. Explain its purpose, ")
                    .append("your contribution, and how you used ")
                    .append(String.join(", ", project.technologies()))
                    .append(". Add only features you have actually implemented.\n");
        } else {
            result.append("- Add a project section with genuine project names, ")
                    .append("your contributions, and technologies used.\n");
        }

        result.append("- Include measurable results only when you can verify ")
                .append("them. Do not add unverified achievements or experience.\n");

        // 3. Project Recommendations
        result.append("\n## Project Recommendations\n\n");

        if (profile.projects().isEmpty()) {
            result.append("Project details are unavailable in the extracted resume profile.\n");
        } else {
            for (ResumeProfile.Project project : profile.projects()) {

                result.append("### ")
                        .append(project.name())
                        .append("\n\n");

                result.append("- **Existing project:** ")
                        .append(project.description())
                        .append("\n");

                result.append("- **Suggested improvement:** Extend this project ")
                        .append("with a job-relevant feature using the skills ")
                        .append("listed in the job description.\n");

                result.append("- **Technologies already listed in the project:** ")
                        .append(String.join(", ", project.technologies()))
                        .append("\n");

                if (containsSkill(missingSkills, "Microservices")) {
                    result.append("- **Practical improvement:** Explore separating ")
                            .append("a suitable backend responsibility into an ")
                            .append("independent Spring Boot service. Define its ")
                            .append("responsibility and communication through REST APIs. ")
                            .append("Implement this only as a new enhancement, not as ")
                            .append("an existing project feature.\n");
                } else {
                    result.append("- **Practical improvement:** Strengthen an existing ")
                            .append("project feature related to the job's required skills. ")
                            .append("Document the implementation and test its behaviour.\n");
                }

                result.append("- **Why it helps:** It gives you practical evidence ")
                        .append("of applying job-relevant backend development concepts.\n");
            }
        }

        // 4. Interview Preparation Topics
        result.append("\n## Interview Preparation Topics\n\n");

        List<String> interviewTopics =
                getPriorityInterviewTopics(requiredSkills);

        if (interviewTopics.isEmpty()) {
            result.append("No specific interview topics could be selected from the job requirements.\n");
        } else {
            for (String topic : interviewTopics) {
                result.append("- **")
                        .append(topic)
                        .append("**: ")
                        .append(getInterviewSubtopics(topic))
                        .append("\n");
            }
        }

        // 5. Action Plan
        result.append("\n## Action Plan\n\n");

        result.append("### Week 1 (Days 1–7)\n");
        if (!missingSkills.isEmpty()) {
            result.append("- Start with missing skills: ")
                    .append(String.join(", ", missingSkills))
                    .append(".\n");
            result.append("- Study their fundamental concepts and architecture.\n");
        } else {
            result.append("- Revise the core skills required by the job.\n");
        }
        result.append("- Revise Java and OOP fundamentals from the job requirements.\n\n");

        result.append("### Week 2 (Days 8–14)\n");
        result.append("- Practise the missing skills through small, hands-on exercises.\n");
        result.append("- Revise Spring Boot, REST APIs and Hibernate concepts ")
                .append("where they appear in the job requirements.\n\n");

        result.append("### Week 3 (Days 15–21)\n");
        if (!profile.projects().isEmpty()) {
            result.append("- Work on a job-relevant improvement to your ")
                    .append(profile.projects().get(0).name())
                    .append(" project.\n");
        } else {
            result.append("- Practise job-relevant concepts through a small practical exercise.\n");
        }
        result.append("- Practise coding and problem-solving questions based on the JD.\n\n");

        result.append("### Week 4 (Days 22–30)\n");
        result.append("- Revise all required skills and the concepts you practised.\n");
        result.append("- Practise technical interview questions and Java coding problems.\n");
        result.append("- Update your resume with completed, verifiable project work.\n");
        result.append("- Take mock interviews and review weak areas.\n");

        return result.toString().trim();
    }
    private String getLearningGuidance(String skill) {

        String normalized = skill.toLowerCase();

        if (normalized.contains("microservice")) {
            return "Study service boundaries, independent services, "
                    + "service-to-service communication, and REST-based APIs. "
                    + "Practise by designing two small Spring Boot services "
                    + "with clearly separated responsibilities.";
        }

        if (normalized.contains("docker")) {
            return "Study images, containers, Dockerfiles, and basic "
                    + "container lifecycle commands. Practise containerising "
                    + "a small application.";
        }

        return "Study the core concepts of this skill and complete "
                + "a small practical exercise related to the job requirements.";
    }


    private List<String> getPriorityInterviewTopics(
            List<String> requiredSkills) {

        List<String> priorityOrder = List.of(
                "Microservices",
                "Java",
                "Spring Boot",
                "REST APIs",
                "Object-Oriented Programming (OOP)",
                "Data Structures and Algorithms (DSA)",
                "MySQL",
                "Hibernate",
                "Git",
                "Problem-solving"
        );

        List<String> topics = new ArrayList<>();

        for (String priority : priorityOrder) {
            for (String required : requiredSkills) {

                if (isSameSkill(priority, required)
                        && !topics.contains(required)) {

                    topics.add(required);
                    break;
                }
            }

            if (topics.size() == 5) {
                break;
            }
        }

        // If the JD contains skills outside the priority list,
        // fill remaining slots from its actual required skills.
        for (String required : requiredSkills) {
            if (topics.size() == 5) {
                break;
            }

            if (!topics.contains(required)) {
                topics.add(required);
            }
        }

        return topics;
    }


    private String getInterviewSubtopics(String skill) {

        String normalized = skill.toLowerCase();

        if (normalized.contains("microservice")) {
            return "service architecture, service communication, "
                    + "service boundaries and REST APIs.";
        }

        if (normalized.equals("java")) {
            return "OOP fundamentals, strings, collections, "
                    + "exception handling and multithreading.";
        }

        if (normalized.contains("object-oriented") || normalized.equals("oop")) {
            return "encapsulation, inheritance, polymorphism and abstraction.";
        }

        if (normalized.contains("data structures")
                || normalized.equals("dsa")) {
            return "arrays, strings, linked lists, searching, sorting "
                    + "and time complexity.";
        }

        if (normalized.contains("spring boot")) {
            return "dependency injection, annotations, controllers, "
                    + "service layers and Spring Boot application flow.";
        }

        if (normalized.contains("rest")) {
            return "HTTP methods, status codes, request-response handling "
                    + "and REST API design.";
        }

        if (normalized.contains("mysql")) {
            return "joins, subqueries, grouping, indexes and query writing.";
        }

        if (normalized.contains("hibernate")) {
            return "ORM, entity mapping, relationships and CRUD operations.";
        }

        if (normalized.equals("git")) {
            return "commits, branches, merging and resolving conflicts.";
        }

        if (normalized.contains("problem")) {
            return "logical reasoning, problem breakdown and coding practice.";
        }

        return "fundamental concepts, practical examples and common interview questions.";
    }


    private boolean containsSkill(
            List<String> skills,
            String target) {

        return skills.stream()
                .anyMatch(skill -> isSameSkill(skill, target));
    }


    private boolean isSameSkill(String first, String second) {

        String a = first.toLowerCase().trim();
        String b = second.toLowerCase().trim();

        if (a.equals(b)) {
            return true;
        }

        if ((a.contains("microservice") && b.contains("microservice"))
                || (a.contains("object-oriented") && b.equals("oop"))
                || (b.contains("object-oriented") && a.equals("oop"))
                || (a.contains("data structures") && b.equals("dsa"))
                || (b.contains("data structures") && a.equals("dsa"))) {
            return true;
        }

        return false;
    }

}