package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.TaskResponseDTO;
import com.gustavocirino.myday_productivity.exception.GlobalExceptionHandler;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerEncodingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void createTask_withMalformedJson_returns400Not500() throws Exception {
        String malformed = "{\"title\":\"horário\"";

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Token", "test-token")
                .content(malformed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição Inválida"));
    }

    @Test
    void createTask_withAccents_validJson_returns201() throws Exception {
        User fakeUser = new User();
        fakeUser.setId(1L);
        when(userRepository.findByAuthToken("test-token"))
                .thenReturn(Optional.of(fakeUser));

        when(taskService.createTask(any(), any())).thenReturn(
                new TaskResponseDTO(
                        1L,
                        "horário",
                        null,
                        TaskPriority.HIGH,
                        TaskStatus.PENDING,
                        null,
                        null,
                        LocalDateTime.now(),
                        "#34a853"));

        String json = "{\"title\":\"horário\",\"priority\":\"HIGH\"}";

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Token", "test-token")
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("horário"));
    }
}
