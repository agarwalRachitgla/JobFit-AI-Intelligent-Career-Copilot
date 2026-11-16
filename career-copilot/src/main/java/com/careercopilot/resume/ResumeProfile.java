
package com.careercopilot.resume;

import java.util.List;

public record ResumeProfile(
        List<String> technicalSkills,
        List<String> softSkills,
        List<Project> projects,
        List<String> education,
        List<String> experience,
        List<String> achievements
) {
    public record Project(
            String name,
            String description,
            List<String> technologies
    ) {}
}
