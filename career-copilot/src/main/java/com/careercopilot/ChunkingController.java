
package com.careercopilot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ChunkingController {

    private final TextChunkingService textChunkingService;

    public ChunkingController(
            TextChunkingService textChunkingService) {
        this.textChunkingService = textChunkingService;
    }

    @GetMapping("/api/rag/chunks/test")
    public Map<String, Object> testChunking(
            @RequestParam String text) {

        List<String> chunks =
                textChunkingService.splitIntoChunks(text);

        return Map.of(
                "originalLength", text.length(),
                "chunkCount", chunks.size(),
                "chunks", chunks
        );
    }
}

