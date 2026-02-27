package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.*;
import com.gustavocirino.myday_productivity.service.ai.ProductivityAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controller para endpoints de IA (Google Gemini).
 * 
 * Endpoints:
 * - POST /api/ai/analyze - Análise de produtividade com IA
 * - POST /api/ai/chat - Chat interativo com coach de produtividade
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Coach", description = "Análises e chat com Google Gemini AI")
public class AIController {

    private final ProductivityAnalysisService analysisService;

    @Operation(summary = "Análise de produtividade com IA", description = "Gera análise cognitiva usando Google Gemini. Tipos: productivity (saúde geral), patterns (padrões comportamentais), recommendations (dicas acionáveis)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Análise gerada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Tipo de análise inválido"),
            @ApiResponse(responseCode = "500", description = "Erro na API do Gemini")
    })
    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AIAnalysisResponseDTO> analyzeProductivity(
            @Valid @RequestBody AIAnalysisRequestDTO request) {

        log.info("📊 POST /api/ai/analyze - Tipo: {}", request.analysisType());

        AIAnalysisResponseDTO response = analysisService.analyzeProductivity(request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Chat com coach de produtividade", description = "Conversa interativa com IA especializada em produtividade cognitiva e time-blocking")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta gerada com sucesso"),
            @ApiResponse(responseCode = "500", description = "Erro na API do Gemini")
    })
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AIChatResponseDTO> chat(
            @Valid @RequestBody AIChatRequestDTO request) {

        log.info("💬 POST /api/ai/chat - Mensagem: {}", request.message());

        try {
            AIChatResponseDTO response = analysisService.chat(request);
            log.info("✅ Chat respondido com sucesso");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // Não deixa o frontend "quebrar" com 500 em caso de falha de IA.
            // A própria mensagem já vem amigável do service.
            log.warn("⚠️ IA indisponível no chat: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new AIChatResponseDTO(e.getMessage(), LocalDateTime.now()));
        }
    }
}
