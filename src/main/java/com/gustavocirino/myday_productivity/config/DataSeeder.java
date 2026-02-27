package com.gustavocirino.myday_productivity.config;

import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.model.enums.RiskLevel;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataSeeder(UserRepository userRepository, TaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        User user = null;
        if (userRepository.count() == 0) {
            user = seedUserData();
        } else {
            user = userRepository.findAll().get(0);
        }

        if (taskRepository.count() == 0) {
            seedTasks(user);
        }
    }

    private User seedUserData() {
        User user = new User();
        user.setEmail("admin@spinops.com");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setVerified(true);
        return userRepository.save(user);
    }

    private void seedTasks(User user) {
        // Critical Task
        createTask(user, 
            "Manutenção Gerador G-01", 
            "Alta vibração detectada no rolamento principal.", 
            TaskPriority.HIGH, 
            TaskStatus.PENDING, 
            "Gerador Diesel 500kVA", 
            "High Power Generator",
            "Vibration: 8.5mm/s, Temp: 92°C, RPM: 1800",
            RiskLevel.CRITICAL,
            0.95,
            LocalDateTime.now().plusDays(2),
            "CRITICAL: Bearing failure imminent within 48h based on exponential vibration increase."
        );

        // Warning Task
        createTask(user, 
            "Inspeção Transformador T-03", 
            "Nível de óleo baixo e temperatura acima do normal.", 
            TaskPriority.MEDIUM, 
            TaskStatus.SCHEDULED, 
            "Transformador 13.8kV", 
            "Distribution Transformer",
            "Oil Level: 20%, Temp: 65°C",
            RiskLevel.WATCH,
            0.45,
            LocalDateTime.now().plusDays(15),
            "WARNING: Low oil level may cause overheating under peak load. Refill scheduled."
        );

         // Safe Task
         createTask(user, 
            "Rotina: Disjuntores Painel A", 
            "Limpeza e reaperto de conexões.", 
            TaskPriority.LOW, 
            TaskStatus.DONE, 
            "Painel de Distribuição A", 
            "Circuit Breaker Panel",
            "Temp: 35°C, Connections: OK",
            RiskLevel.SAFE,
            0.05,
            null,
            "SAFE: All parameters within normal operating range."
        );
        
        // More random tasks
        createTask(user, "Troca de Filtros HVAC", "Manutenção preventiva padrão.", TaskPriority.LOW, TaskStatus.PENDING, "Chiller C-02", "HVAC", "Diff Pressure: 15Pa", RiskLevel.SAFE, 0.1, null, "Routine maintenance due.");
        createTask(user, "Calibração Relé 50/51", "Aferição anual de proteção.", TaskPriority.HIGH, TaskStatus.PENDING, "Relé Pextron", "Protection Relay", "Last Cal: 360 days ago", RiskLevel.WATCH, 0.3, LocalDateTime.now().plusDays(30), "Calibration expiring soon.");
    }

    private void createTask(User user, String title, String description, TaskPriority priority, TaskStatus status, 
                            String equipmentName, String equipmentType, String sensorData, 
                            RiskLevel risk, Double failureProb, LocalDateTime failureDate, String aiAnalysis) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStatus(status);
        task.setUser(user);
        
        // SpinOps Fields
        task.setEquipmentName(equipmentName);
        task.setEquipmentType(equipmentType);
        task.setSensorReadings(sensorData);
        task.setRiskLevel(risk);
        task.setFailureProbability(failureProb);
        task.setPredictedFailureDate(failureDate);
        task.setAiAnalysisSummary(aiAnalysis);
        
        // task.setCreatedAt(LocalDateTime.now()); // Already set in constructor
        if (status == TaskStatus.SCHEDULED) {
            task.setStartTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));
            task.setEndTime(LocalDateTime.now().plusDays(1).withHour(12).withMinute(0));
        }

        taskRepository.save(task);
    }
}
