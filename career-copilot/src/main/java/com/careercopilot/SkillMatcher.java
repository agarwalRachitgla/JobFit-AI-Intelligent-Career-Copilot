
package com.careercopilot;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SkillMatcher {

    public Map<String, List<String>> matchSkills(
            List<String> resumeSkills,
            List<String> requiredSkills) {

        Map<String, String> resumeSkillMap = new HashMap<>();

        for (String skill : resumeSkills) {
            resumeSkillMap.put(normalize(skill), skill);
        }

        List<String> matchingSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        for (String skill : requiredSkills) {
            String normalizedSkill = normalize(skill);

            if (resumeSkillMap.containsKey(normalizedSkill)) {
                matchingSkills.add(
                        resumeSkillMap.get(normalizedSkill)
                );
            } else {
                missingSkills.add(skill);
            }
        }

        Map<String, List<String>> result = new HashMap<>();
        result.put("matchingSkills", matchingSkills);
        result.put("missingSkills", missingSkills);

        return result;
    }

    private String normalize(String skill) {
        return skill.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
