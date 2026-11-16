
package com.careercopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private final SimilaritySearchService similaritySearchService;
    private final OllamaService ollamaService;
    private final TextChunkingService textChunkingService;
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ObjectMapper objectMapper;

    public RagService(
            TextChunkingService textChunkingService,
            EmbeddingService embeddingService,
            DocumentChunkRepository documentChunkRepository,
            ObjectMapper objectMapper,
            SimilaritySearchService similaritySearchService,
            OllamaService ollamaService) {

        this.textChunkingService = textChunkingService;
        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.objectMapper = objectMapper;
        this.similaritySearchService = similaritySearchService;
        this.ollamaService = ollamaService;
    }

    // Existing method: keeps compatibility with the current API
    @Transactional
    public List<Map<String, Object>> processText(String text)
            throws IOException, InterruptedException {

        return processText(text, "manual-text");
    }

    // New method: saves chunks with their document name
    @Transactional
    public List<Map<String, Object>> processText(
            String text, String documentName)
            throws IOException, InterruptedException {
        documentChunkRepository.deleteByDocumentName(documentName);

        List<String> chunks =
                textChunkingService.splitIntoChunks(text);

        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {

            String chunk = chunks.get(i);

            List<Double> embedding =
                    embeddingService.generateEmbedding(chunk);

            String embeddingJson =
                    objectMapper.writeValueAsString(embedding);

            DocumentChunk documentChunk =
                    new DocumentChunk(
                            chunk, embeddingJson, documentName);

            DocumentChunk savedChunk =
                    documentChunkRepository.save(documentChunk);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", savedChunk.getId());
            item.put("chunkIndex", i);
            item.put("text", chunk);
            item.put("documentName", documentName);
            item.put("vectorDimensions", embedding.size());

            results.add(item);
        }

        return results;
    }


    public String askQuestion(String question, String documentName)
            throws IOException, InterruptedException {

        List<Map<String, Object>> matches =
                similaritySearchService.search(question, 3, documentName);

        StringBuilder context = new StringBuilder();

        for (Map<String, Object> match : matches) {
            context.append(match.get("text")).append("\n\n");
        }

        if (context.isEmpty()) {
            return "No relevant chunks found for document: " + documentName;
        }

        return ollamaService.generateRagResponse(
                question,
                context.toString()
        );
    }

}
