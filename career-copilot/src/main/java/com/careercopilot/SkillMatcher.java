
package com.careercopilot;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SkillMatcher {

    private static final Map<String, String> ALIASES = createAliases();

    public Map<String, List<String>> matchSkills(
            List<String> resumeSkills,
            List<String> requiredSkills) {

        Set<String> normalizedResumeSkills = new HashSet<>();

        for (String skill : resumeSkills) {
            normalizedResumeSkills.add(canonical(skill));
        }

        List<String> matchingSkills = new ArrayList<>();
        List<String> partialSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        Set<String> processed = new HashSet<>();

        for (String skill : requiredSkills) {

            String normalizedSkill = canonical(skill);

            if (!processed.add(normalizedSkill)) {
                continue;
            }

            if (normalizedResumeSkills.contains(normalizedSkill)) {
                matchingSkills.add(skill);
            } else {
                missingSkills.add(skill);
            }
        }

        Map<String, List<String>> result = new LinkedHashMap<>();

        result.put("matchingSkills", matchingSkills);
        result.put("partialSkills", partialSkills);
        result.put("missingSkills", missingSkills);

        return result;
    }

    private String canonical(String skill) {

        String normalized = skill == null ? "" : skill.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.equals("dsa")
                || normalized.equals("data structures algorithms")
                || normalized.equals("data structures and algorithms dsa")) {
            return "data structures and algorithms";
        }

        if (normalized.equals("oop")
                || normalized.equals("o o p")
                || normalized.equals("object oriented programming oop")) {
            return "object oriented programming";
        }

        return ALIASES.getOrDefault(normalized, normalized);
    }

    private static Map<String, String> createAliases() {

        Map<String, String> aliases = new HashMap<>();

        aliases.put("springboot", "spring boot");
        aliases.put("reactjs", "react.js");

        aliases.put("rest api", "rest apis");
        aliases.put("restful api", "rest apis");
        aliases.put("restful apis", "rest apis");

        aliases.put("problem-solving", "problem solving");
        aliases.put("problem solving skills", "problem solving");

        return aliases;
    }
}
