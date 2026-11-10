package com.roadguard.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "road_hazards")
public class RoadHazard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private HazardType type;

    private Double latitude;
    private Double longitude;

    private String address;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    private HazardStatus status = HazardStatus.ACTIVE;

    private Integer reportCount = 1;

    private Double aiConfidence;

    private Boolean verified = false;

    private Instant createdAt = Instant.now();

    public RoadHazard() {
    }

    public RoadHazard(HazardType type, Double latitude, Double longitude,
                      Severity severity, Double aiConfidence) {
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.severity = severity;
        this.aiConfidence = aiConfidence;
        this.status = HazardStatus.ACTIVE;
        this.reportCount = 1;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public HazardType getType() { return type; }
    public void setType(HazardType type) { this.type = type; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public HazardStatus getStatus() { return status; }
    public void setStatus(HazardStatus status) { this.status = status; }

    public Integer getReportCount() { return reportCount; }
    public void setReportCount(Integer reportCount) { this.reportCount = reportCount; }

    public Double getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(Double aiConfidence) { this.aiConfidence = aiConfidence; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}