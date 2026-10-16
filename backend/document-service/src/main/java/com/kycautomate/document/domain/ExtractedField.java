package com.kycautomate.document.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "extracted_fields")
public class ExtractedField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "field_value", length = 1000)
    private String fieldValue;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    protected ExtractedField() {}

    public ExtractedField(Document document, String fieldName, String fieldValue, Double confidenceScore) {
        this.document = document;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.confidenceScore = confidenceScore;
    }

    public Long getId() { return id; }
    public Document getDocument() { return document; }
    public String getFieldName() { return fieldName; }
    public String getFieldValue() { return fieldValue; }
    public Double getConfidenceScore() { return confidenceScore; }
}
