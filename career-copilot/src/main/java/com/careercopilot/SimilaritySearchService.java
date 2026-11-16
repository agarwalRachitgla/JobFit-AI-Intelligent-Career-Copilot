
package com.careercopilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class SimilaritySearchService {

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ObjectMapper objectMapper;

    public SimilaritySearchService(
            EmbeddingService embeddingService,
            DocumentChunkRepository documentChunkRepository,
            ObjectMapper objectMapper) {

        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.objectMapper = objectMapper;
    }

    // Existing method: searches all documents for compatibility
    public List<Map<String, Object>> search(String query, int topK)
            throws IOException, InterruptedException {

        List<Double> queryEmbedding =
                embeddingService.generateEmbedding(query);

        List<DocumentChunk> documents =
                documentChunkRepository.findAll();

        return rankDocuments(queryEmbedding, documents, topK);
    }

    // New method: searches only the selected document
    public List<Map<String, Object>> search(
            String query, int topK, String documentName)
            throws IOException, InterruptedException {

        List<Double> queryEmbedding =
                embeddingService.generateEmbedding(query);

        List<DocumentChunk> documents =
                documentChunkRepository.findByDocumentName(documentName);

        return rankDocuments(queryEmbedding, documents, topK);
    }

    private List<Map<String, Object>> rankDocuments(
            List<Double> queryEmbedding,
            List<DocumentChunk> documents,
            int topK) throws IOException {

        List<Map<String, Object>> results = new ArrayList<>();

        for (DocumentChunk document : documents) {

            if (document.getEmbedding() == null
                    || document.getEmbedding().isBlank()) {
                continue;
            }

            List<Double> storedEmbedding =
                    objectMapper.readValue(
                            document.getEmbedding(),
                            new TypeReference<List<Double>>() {}
                    );

            double score = cosineSimilarity(
                    queryEmbedding, storedEmbedding);

            results.add(Map.of(
                    "id", document.getId(),
                    "text", document.getContent(),
                    "documentName", document.getDocumentName() == null
                            ? "" : document.getDocumentName(),
                    "score", score
            ));
        }

        results.sort(
                Comparator.comparingDouble(
                        item -> -((Number) item.get("score")).doubleValue()
                )
        );

        int limit = Math.max(0, Math.min(topK, results.size()));

        return new ArrayList<>(results.subList(0, limit));
    }

    private double cosineSimilarity(
            List<Double> vectorA,
            List<Double> vectorB) {

        if (vectorA.size() != vectorB.size()
                || vectorA.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {

            double a = vectorA.get(i);
            double b = vectorB.get(i);

            dotProduct += a * b;
            magnitudeA += a * a;
            magnitudeB += b * b;
        }

        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }

        return dotProduct /
                (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }
}
