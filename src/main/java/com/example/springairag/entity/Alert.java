package com.example.springairag.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID; 

@Entity
@Table(name = "cc_alerts_alert")
public class Alert {

    @Id
    private UUID id;

    private String message;

    private String severity;

    @Column(name = "detection_class")
    private String detectionClass;

    private Double confidence;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    // 🔥 pgvector
    @Column(columnDefinition = "vector(384)")
    private float[] embedding;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDetectionClass() {
        return detectionClass;
    }

    public void setDetectionClass(String detectionClass) {
        this.detectionClass = detectionClass;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    // getters & setters
}