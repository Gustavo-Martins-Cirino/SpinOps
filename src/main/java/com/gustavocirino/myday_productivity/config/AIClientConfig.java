package com.gustavocirino.myday_productivity.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuração do Google Gemini AI usando LangChain4j.
 * 
 * Este módulo conecta o NeuroTask ao modelo Gemini-1.5-Flash para análises
 * cognitivas.
 * A API key deve ser configurada via variável de ambiente GEMINI_API_KEY.
 * 
 * Obtenha sua chave em: https://aistudio.google.com/app/apikey
 */
@Configuration
public class AIClientConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model.name}")
    private String modelName;

    @Value("${openai.base.url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${openai.temperature:0.7}")
    private Double temperature;

    @Value("${openai.max.tokens:2048}")
    private Integer maxTokens;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
