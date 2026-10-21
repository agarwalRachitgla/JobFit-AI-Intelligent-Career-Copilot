package com.careercopilot.resume;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ResumeAnalysisService {

    public List<String> findSkills(String resumeText) {
        String text = resumeText.toLowerCase(Locale.ROOT);

        List<String> allSkills = List.of(
                "java",
                "python",
                "sql",
                "spring boot",
                "react.js",
                "machine learning",
                "generative ai",
                "scikit-learn",
                "mysql",
                "rest apis",
                "data structures and algorithms",
                "pandas",
                "numpy"
        );

        List<String> foundSkills = new ArrayList<>();

        for (String skill : allSkills) {
            if (text.contains(skill.toLowerCase(Locale.ROOT))) {
                foundSkills.add(skill);
            }
        }

        return foundSkills;
    }
}

