
package com.careercopilot.resume;

import org.springframework.stereotype.Service;

import com.careercopilot.OllamaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ResumeAnalysisService {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisService(
            OllamaService ollamaService,
            ObjectMapper objectMapper) {
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
    }

    public ResumeProfile analyzeResume(String resumeText)
            throws IOException, InterruptedException {

        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException(
                    "Resume text cannot be empty.");
        }

        String prompt = """
        You are an accurate resume information extraction system.

        Analyze ONLY the supplied resume.

        Extract the following information:

        1. Technical skills
        2. Soft skills
        3. Projects
        4. Education
        5. Work experience and internships
        6. Achievements and certifications

        Return ONLY one valid JSON object in this format:

        {
          "technicalSkills": [],
          "softSkills": [],
          "projects": [
            {
              "name": "Project name",
              "description": "Description from resume",
              "technologies": ["Java", "MySQL"]
            }
          ],
          "education": [],
          "experience": [],
          "achievements": []
        }

                
        SKILL EXTRACTION AND CLASSIFICATION RULES:
                
        1. Technical Skills:
        - Extract programming languages, frameworks, libraries,
        databases, developer tools, platforms and technical concepts.
        - Include data analysis tools and technologies such as
        Python, SQL, Excel, Power BI, Pandas and NumPy.
        - Include technical methods such as EDA and Data Cleaning.
        - Preserve the actual skill names used in the resume.
        2. Soft Skills:
        - Extract communication, teamwork, leadership,
        analytical thinking, problem-solving, time management,
        daptability and other interpersonal abilities.
        - Never include soft skills in technicalSkills.
        3. Certifications and Organizations:
        - Extract certification names separately in achievements.
        - Never classify organization names, institutions,
        training programs or certification providers as technical skills.
        - Do not classify Oracle, Nasscom Foundation or ThingQbator
        as technical skills merely because their names appear in the resume.
                
        4. Classification:
        - Classify each item according to its actual meaning and context.
        - Do not classify every keyword as a skill.
        - Do not treat Excel features such as PivotTables, formulas,
        lookups and reporting as separate technologies when they
        are simply capabilities within Excel.
        - Do not combine unrelated skills into one entry.
        - Do not duplicate skills across categories.
        5. Accuracy:
        - Extract only information explicitly supported by the resume.
        - Do not infer skills from job requirements.
        - Never invent technologies or qualifications.
        - Preserve the original meaning and wording.
                

        STRICT RULES:

        - Extract only explicitly available information.
        - Never invent project names or descriptions.
        - Never assume technologies used in a project.
        - Do not treat suggested or planned projects as completed projects.
        - Do not convert job requirements into candidate experience.
        - Do not invent achievements, percentages or metrics.
        - If a section has no information, return an empty array.
        - Preserve the actual meaning of the resume.
        - Return valid JSON without Markdown or code fences.
        
                OUTPUT LENGTH RULES:
                - Return only information explicitly supported by the resume.
                - List each skill by its name only. Do not explain skills.
                - Keep each project description within 1-2 short sentences.
                - List technologies as individual names.
                - Keep education, experience, and achievements concise.
                - Do not repeat information across different fields.
                - Do not generate assumptions or additional information.
                - Return all six required JSON fields, using empty arrays when information is unavailable.
                - Ensure the complete JSON response is generated.

        RESUME:
        %s
        """.formatted(
                resumeText.substring(
                        0, Math.min(resumeText.length(), 7000))
        );

        String response =
                ollamaService.generateResumeProfileResponse(prompt);

        if (response == null || response.isBlank()) {
            throw new IOException(
                    "Ollama returned an empty resume analysis.");
        }

        JsonNode root = objectMapper.readTree(response);

        if (!root.isObject()) {
            throw new IOException(
                    "Resume analysis must be a JSON object.");
        }
        if (!root.isObject()) {
            throw new IOException(
                    "Resume analysis must be a JSON object.");
        }

        List<ResumeProfile.Project> projects =
                new ArrayList<>();

        JsonNode projectsNode = root.path("projects");

        if (projectsNode.isArray()) {
            for (JsonNode project : projectsNode) {

                if (!project.isObject()) {
                    continue;
                }

                String name = project.path("name").asText("");
                String description =
                        project.path("description").asText("");

                List<String> technologies =
                        readStringList(project.path("technologies"));

                if (!name.isBlank() || !description.isBlank()) {
                    projects.add(
                            new ResumeProfile.Project(
                                    name,
                                    description,
                                    technologies
                            )
                    );
                }
            }
        }

        List<String> technicalSkills =
                normalizeSkills(
                        readStringList(root.path("technicalSkills"))
                );

        List<String> softSkills =
                normalizeSkills(
                        readStringList(root.path("softSkills"))
                );

        return new ResumeProfile(
                technicalSkills,
                softSkills,
                projects,
                readStringList(root.path("education")),
                readStringList(root.path("experience")),
                readStringList(root.path("achievements"))
        );
    }

    private List<String> readStringList(JsonNode node) {

        List<String> values = new ArrayList<>();

        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    values.add(item.asText().trim());
                }
            }
        }

        return values;
    }

    private List<String> normalizeSkills(List<String> aiSkills) {

        Map<String, String> uniqueSkills = new LinkedHashMap<>();

        if (aiSkills == null) {
            return new ArrayList<>();
        }

        for (String skill : aiSkills) {

            if (skill == null || skill.isBlank()) {
                continue;
            }

            String cleanedSkill = skill.trim();
            String normalizedSkill = normalize(cleanedSkill);

            if (normalizedSkill.isBlank()) {
                continue;
            }

            uniqueSkills.putIfAbsent(
                    normalizedSkill,
                    cleanedSkill
            );
        }

        return new ArrayList<>(uniqueSkills.values());
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
