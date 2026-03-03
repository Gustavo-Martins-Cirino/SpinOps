package com.gustavocirino.myday_productivity.model;

import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tb_tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private LocalDateTime createdAt;

    /** Momento em que a tarefa foi marcada como DONE. Preenchido por TaskService.markAsDone(). */
    private LocalDateTime completedAt;

    @Column(length = 20)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToMany
    @JoinTable(name = "task_tags", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    // New fields for Predictive Maintenance
    @Column(name = "equipment_name", length = 100)
    private String equipmentName;

    @Column(name = "equipment_type")
    private String equipmentType;

    @Column(name = "sensor_readings", columnDefinition = "TEXT")
    private String sensorReadings;

    // AI Analysis Results
    private LocalDateTime predictedFailureDate;

    private Double failureProbability;

    @Enumerated(EnumType.STRING)
    private com.gustavocirino.myday_productivity.model.enums.RiskLevel riskLevel;

    @Column(name = "ai_analysis_summary", columnDefinition = "TEXT")
    private String aiAnalysisSummary;

    public Task() {
        this.createdAt = LocalDateTime.now();
        this.status = TaskStatus.PENDING;
    }

    public Task(String title, String description, TaskPriority priority) {
        this();
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getColor() {
        return color;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String equipmentType) {
        this.equipmentType = equipmentType;
    }

    public String getSensorReadings() {
        return sensorReadings;
    }

    public void setSensorReadings(String sensorReadings) {
        this.sensorReadings = sensorReadings;
    }

    public LocalDateTime getPredictedFailureDate() {
        return predictedFailureDate;
    }

    public void setPredictedFailureDate(LocalDateTime predictedFailureDate) {
        this.predictedFailureDate = predictedFailureDate;
    }

    public Double getFailureProbability() {
        return failureProbability;
    }

    public void setFailureProbability(Double failureProbability) {
        this.failureProbability = failureProbability;
    }

    public com.gustavocirino.myday_productivity.model.enums.RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(com.gustavocirino.myday_productivity.model.enums.RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getAiAnalysisSummary() {
        return aiAnalysisSummary;
    }

    public void setAiAnalysisSummary(String aiAnalysisSummary) {
        this.aiAnalysisSummary = aiAnalysisSummary;
    }
}
