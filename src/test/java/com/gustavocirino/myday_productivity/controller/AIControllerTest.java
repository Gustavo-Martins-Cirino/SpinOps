package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.AIChatRequestDTO;
import com.gustavocirino.myday_productivity.dto.AIChatResponseDTO;
import com.gustavocirino.myday_productivity.service.ai.ProductivityAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AIController.class)
class AIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductivityAnalysisService analysisService;

    @Test
    void chat_returns200WithFriendlyMessage_whenServiceThrowsRuntimeException() throws Exception {
        when(analysisService.chat(any(AIChatRequestDTO.class)))
                .thenThrow(new RuntimeException("IA indisponível (teste)"));

        var body = objectMapper.writeValueAsString(new AIChatRequestDTO("oi"));

        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("IA indisponível (teste)"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void chat_returns200_whenServiceSucceeds() throws Exception {
        when(analysisService.chat(any(AIChatRequestDTO.class)))
                .thenReturn(new AIChatResponseDTO("ok", LocalDateTime.of(2026, 1, 3, 12, 0)));

        var body = objectMapper.writeValueAsString(new AIChatRequestDTO("oi"));

        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("ok"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
