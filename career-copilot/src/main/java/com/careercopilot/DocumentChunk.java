
package com.careercopilot;

import jakarta.persistence.*;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 255)
    private String documentName;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "LONGTEXT")
    private String embedding;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, String embedding, String documentName) {
        this.content = content;
        this.embedding = embedding;
        this.documentName = documentName;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }
    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }
}
