package com.gustavocirino.myday_productivity.service.ai;

import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.enums.RiskLevel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;

@Service
public class PredictiveMaintenanceService {

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PredictiveMaintenanceService(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    public void analyzeMaintenanceOrder(Task task) {
        if (task.getSensorReadings() == null || task.getSensorReadings().isEmpty()) {
            return; // No data to analyze
        }

        String prompt = String.format("""
            You are SpinOps AI, an expert industrial diagnostic system.
            Analyze the following equipment sensor data:
            
            Equipment: %s (%s)
            Sensor Data: %s
            
            1. Determine the 'Risk Level' (SAFE, WATCH, CRITICAL).
            2. Predict the failure probability (0.0 to 1.0).
            3. Estimate days until failure (integer).
            4. Provide a technical diagnosis (max 20 words).
            
            Respond ONLY in JSON format:
            {
                "risk": "SAFE|WATCH|CRITICAL",
                "probability": 0.85,
                "days_until_failure": 5,
                "diagnosis": "Bearing wear detected."
            }
            """, task.getEquipmentName(), task.getEquipmentType(), task.getSensorReadings());

        try {
            String response = chatModel.generate(prompt);
            // Clean up markdown code blocks if present
            response = response.replace("```json", "").replace("```", "").trim();
            
            AIResponse aiResponse = objectMapper.readValue(response, AIResponse.class);
            
            task.setRiskLevel(RiskLevel.valueOf(aiResponse.risk));
            task.setFailureProbability(aiResponse.probability);
            if (aiResponse.days_until_failure != null && aiResponse.days_until_failure < 365) {
                task.setPredictedFailureDate(LocalDateTime.now().plusDays(aiResponse.days_until_failure));
            }
            task.setAiAnalysisSummary(aiResponse.diagnosis);
            
        } catch (Exception e) {
            // Fallback for demo if AI fails or key is missing
            System.err.println("AI Analysis Failed: " + e.getMessage());
            fallbackAnalysis(task);
        }
    }

    private void fallbackAnalysis(Task task) {
        String data = task.getSensorReadings().toLowerCase();
        if (data.contains("high") || data.contains("critical") || data.contains("fail")) {
            task.setRiskLevel(RiskLevel.CRITICAL);
            task.setFailureProbability(0.9);
            task.setAiAnalysisSummary("Critical anomalies detected (Fallback mode).");
        } else if (data.contains("warn") || data.contains("medium")) {
            task.setRiskLevel(RiskLevel.WATCH);
            task.setFailureProbability(0.5);
            task.setAiAnalysisSummary("Warning signs detected (Fallback mode).");
        } else {
            task.setRiskLevel(RiskLevel.SAFE);
            task.setFailureProbability(0.1);
            task.setAiAnalysisSummary("System nominal (Fallback mode).");
        }
    }

    // Inner class for JSON mapping
    private static class AIResponse {
        public String risk;
        public Double probability;
        public Integer days_until_failure;
        public String diagnosis;
    }
}
