
package com.careercopilot.resume;

import com.careercopilot.OllamaService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeAnalysisService resumeAnalysisService;
    private final OllamaService ollamaService;

    public ResumeController(
            ResumeAnalysisService resumeAnalysisService,
            OllamaService ollamaService) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.ollamaService = ollamaService;
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

            List<String> skills =
                    resumeAnalysisService.findSkills(text);

            String resumeForAI = text.substring(
                    0, Math.min(text.length(), 12000));

            String prompt = """
                    You are an expert career coach.
                    Analyze the following resume.

                    Provide these sections:
                    1. Resume Summary
                    2. Key Strengths
                    3. Areas for Improvement
                    4. Technical Skills Assessment
                    5. Recommended Skills to Learn
                    6. Suitable Entry-Level Job Roles

                    Give specific, practical feedback based only
                    on the information in the resume.
                    Do not invent qualifications or experience.
                    Use clear, simple language.

                    RESUME:
                    """ + resumeForAI;

            String aiAnalysis =
                    ollamaService.generateResponse(prompt);

            return ResponseEntity.ok(Map.of(
                    "fileName", file.getOriginalFilename(),
                    "extractedText", text,
                    "skills", skills,
                    "skillCount", skills.size(),
                    "aiAnalysis", aiAnalysis
            ));

        } catch (IOException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "Unable to read the PDF or get AI analysis."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return ResponseEntity.internalServerError().body(
                    Map.of("error", "AI analysis was interrupted."));
        }
    }
}
