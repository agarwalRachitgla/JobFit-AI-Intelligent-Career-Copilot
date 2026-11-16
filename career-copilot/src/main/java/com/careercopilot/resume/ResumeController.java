
package com.careercopilot.resume;

import com.careercopilot.OllamaService;
import com.careercopilot.RagService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeAnalysisService resumeAnalysisService;
    private final OllamaService ollamaService;
    private final RagService ragService;
    private static final Logger logger =
            LoggerFactory.getLogger(ResumeController.class);

    public ResumeController(
            ResumeAnalysisService resumeAnalysisService,
            OllamaService ollamaService,
            RagService ragService) {

        this.resumeAnalysisService = resumeAnalysisService;
        this.ollamaService = ollamaService;
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadResume(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Please upload a PDF file."));
        }

        if (file.getOriginalFilename() == null ||
                !file.getOriginalFilename()
                        .toLowerCase()
                        .endsWith(".pdf")) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", "Only PDF files are supported."));
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error",
                                "No readable text found in the PDF."));
            }

            // Process resume text for RAG.
            List<Map<String, Object>> ragChunks =
                    ragService.processText(
                            text, file.getOriginalFilename());

            // Extract separate skill categories.
            // Extract resume profile and skills using AI.
            ResumeProfile resumeProfile =
                    resumeAnalysisService.analyzeResume(text);

            List<String> technicalSkills =
                    resumeProfile.technicalSkills();

            List<String> softSkills =
                    resumeProfile.softSkills();


            String resumeForAI = text.substring(
                    0, Math.min(text.length(), 12000));



            String prompt = """
        You are an expert career coach reviewing a fresher's resume.

        Analyze the resume strictly using the information provided below.

        IMPORTANT RULES:

        1. Use only information explicitly supported by the resume
           and the extracted skill lists.

        2. Never invent projects, achievements, work experience,
           qualifications, technical expertise, or performance metrics.

        3. The extracted skills lists contain skills identified
           by AI-based resume analysis. Treat them as extracted,
           not independently verified skills.

        4. Never describe an extracted skill as missing or recommend
           an existing skill as something the candidate needs to learn.

        5. Clearly distinguish existing skills from recommended skills.

        6. If achievements or work experience are not explicitly
           mentioned, state that they are not clearly mentioned.
           Do not assume.

        7. Recommend entry-level roles based on the candidate's
           actual skills, projects, education, and experience.
           Prioritize Java Developer and Software Developer roles
           only when supported by the resume.

        8. Do not claim proficiency, expertise, or professional
           experience unless the resume provides clear evidence.

        9. Keep feedback specific, factual, concise, and easy to understand.

        10. Avoid generic advice that could apply to every candidate.
            Connect every recommendation to the resume or target role.

        EXTRACTED TECHNICAL SKILLS:
        %s

        EXTRACTED SOFT SKILLS:
        %s

        RESUME TEXT:
        %s

        Provide exactly these six sections:

        1. Resume Summary
        2. Key Strengths
        3. Areas for Improvement
        4. Technical Skills Assessment
        5. Recommended Skills to Learn
        6. Suitable Entry-Level Job Roles

        SECTION-SPECIFIC RULES:

        Resume Summary:
        - Summarize the candidate's actual education, skills,
          projects, and experience.
        - Do not exaggerate the candidate's experience or proficiency.

        Key Strengths:
        - Highlight relevant technical skills, soft skills,
          and actual project experience.
        - Avoid repeating the same strength in different wording.

        Areas for Improvement:
        - Identify genuine gaps in resume presentation or
          skills relevant to the candidate's target roles.
        - Do not label an existing skill as a gap.
        - Do not invent weaknesses.

        Technical Skills Assessment:
        - List existing technical skills from the extracted list.
        - Do not add skills that are not present in the extracted list.
        - Group related skills where appropriate.

        Recommended Skills to Learn:
        - Recommend only skills absent from both extracted skill lists.
        - Prioritize skills relevant to the candidate's projects
          and suitable entry-level roles.
        - Do not repeat the same recommendation in another section.
        - Avoid unrelated technologies and generic skill lists.
        - If no meaningful recommendation exists, say so.

        Suitable Entry-Level Job Roles:
        - Recommend realistic fresher-level roles based on
          the actual resume.
        - Briefly explain why each role is relevant.
        - Do not recommend roles requiring experience that
          the candidate does not demonstrate.

        OUTPUT RULES:
        - Return all six sections exactly once.
        - Do not repeat recommendations across sections.
        - Use clear headings and concise bullet points.
        - Do not include introductory or concluding text
          outside the six sections.
        """.formatted(
                    technicalSkills,
                    softSkills,
                    resumeForAI
            );


            String aiAnalysis = ollamaService.generateResponse(prompt);


            return ResponseEntity.ok(Map.of(
                    "fileName", file.getOriginalFilename(),
                    "extractedText", text,
                    "technicalSkills", technicalSkills,
                    "technicalSkillCount", technicalSkills.size(),
                    "softSkills", softSkills,
                    "softSkillCount", softSkills.size(),
                    "aiAnalysis", aiAnalysis,
                    "ragChunks", ragChunks
            ));

        } catch (IOException e) {

            logger.error("Resume upload or AI analysis failed", e);

            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "Unable to read the PDF or get AI analysis."));
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error("AI resume analysis was interrupted", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error", "AI analysis was interrupted."));
        }
    }
}
