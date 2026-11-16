
package com.careercopilot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class SimilaritySearchController {

    private final SimilaritySearchService similaritySearchService;

    public SimilaritySearchController(
            SimilaritySearchService similaritySearchService) {
        this.similaritySearchService = similaritySearchService;
    }

    @GetMapping("/api/rag/search")
    public List<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK)
            throws IOException, InterruptedException {

        return similaritySearchService.search(query, topK);
    }
}
