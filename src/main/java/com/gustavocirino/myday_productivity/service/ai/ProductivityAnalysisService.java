package com.gustavocirino.myday_productivity.service.ai;

import com.gustavocirino.myday_productivity.dto.*;
import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de análise de produtividade com IA usando Google Gemini.
 * 
 * Este serviço implementa o "coach cognitivo" do NeuroTask, analisando padrões
 * de trabalho, identificando gargalos e fornecendo insights personalizados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductivityAnalysisService {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ChatLanguageModel chatModel;
    private final TaskRepository taskRepository;

    @Value("${openai.model.name}")
    private String modelName;

    /**
     * Analisa a produtividade do usuário usando IA.
     */
    public AIAnalysisResponseDTO analyzeProductivity(AIAnalysisRequestDTO request) {
        log.info("🤖 Iniciando análise de produtividade: {}", request.analysisType());

        // Busca todas as tarefas para análise
        List<Task> allTasks = taskRepository.findAll();

        // Calcula métricas base
        long totalTasks = allTasks.size();
        long completedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long lateTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();
        long scheduledTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.SCHEDULED).count();

        double completionRate = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 0.0;

        // Monta o contexto para a IA
        String context = buildAnalysisContext(allTasks, totalTasks, completedTasks, lateTasks, scheduledTasks,
                completionRate);

        // Gera o prompt baseado no tipo de análise
        String prompt = switch (request.analysisType()) {
            case "productivity" -> buildProductivityPrompt(context);
            case "patterns" -> buildPatternsPrompt(context);
            case "recommendations" -> buildRecommendationsPrompt(context);
            default -> buildGeneralPrompt(context);
        };

        try {
            // Consulta a IA
            String aiResponse = chatModel.generate(prompt);

            log.info("✅ Análise concluída. Score: {}", (int) completionRate);

            // Parse da resposta (formato esperado: Summary|||Insight1;Insight2|||Rec1;Rec2)
            return parseAIResponse(aiResponse, (int) completionRate);
        } catch (Exception e) {
            String rawMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido na chamada da IA.";
            String simpleName = e.getClass().getSimpleName();

            boolean isOpenAiHttp = "OpenAiHttpException".equals(simpleName)
                    || simpleName.contains("OpenAiHttp");
            boolean isBalanceIssue = rawMessage.toLowerCase().contains("insufficient balance")
                    || rawMessage.contains("402");

            if (isOpenAiHttp && isBalanceIssue) {
                log.warn("⚠️ IA de análise indisponível por saldo insuficiente: {} - {}", simpleName, rawMessage);

                String fallbackSummary = "No momento, meu módulo de análise avançada está descansando. " +
                        "Mas você ainda pode agendar manualmente e acompanhar suas tarefas na linha do tempo.";

                return new AIAnalysisResponseDTO(
                        fallbackSummary,
                        List.of(),
                        List.of(),
                        (int) completionRate,
                        LocalDateTime.now());
            }

            log.error("❌ Erro ao gerar análise de produtividade: tipo={}, mensagem={}",
                    e.getClass().getSimpleName(), rawMessage, e);
            throw new RuntimeException("Erro ao gerar análise de produtividade: " + rawMessage, e);
        }
    }

    /**
     * Chat interativo com a IA sobre produtividade.
     */
    public AIChatResponseDTO chat(AIChatRequestDTO request) {
        log.info("💬 Chat com IA: {}", request.message());

        try {
            // Contexto geral das tarefas (todas as tarefas do sistema)
            List<Task> allTasks = taskRepository.findAll();
            String taskContext = sanitizeForPrompt(buildTaskSummary(allTasks));

            // Contexto de tarefas de HOJE e AMANHÃ, alinhado com o fuso configurado
            // (America/Sao_Paulo)
            LocalDate today = LocalDate.now(APP_ZONE);
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfTomorrow = today.plusDays(1).atTime(LocalTime.MAX);
            List<Task> contextTasks = taskRepository.findAllByStartTimeBetween(startOfDay, endOfTomorrow);
            String todayContext = sanitizeForPrompt(buildContextSummary(contextTasks, today));

            // Carimbo temporal explícito para o modelo não confundir "hoje" / "amanhã" /
            // "agora"
            LocalDateTime now = LocalDateTime.now(APP_ZONE);
            String dayOfWeek = today.getDayOfWeek().toString();

            String prompt = """
                    DATA_E_HORA_ATUAL: %s
                    DIA_DA_SEMANA: %s

                    CONTEXTO DO SISTEMA (NÃO MOSTRAR PARA O USUÁRIO):
                    - Resumo geral de OSs abertas: %s
                    - OSs agendadas para hoje e amanhã: %s

                    Você é a SpinIA, uma assistente de engenharia elétrica especializada em manutenção preditiva, qualidade de energia e despacho de Ordens de Serviço (OS), desenvolvida pela Spin Engenharia.
                    O usuário é um técnico em campo que irá relatar um problema ou solicitar uma OS.
                    Seja técnica, objetiva e precisa. Responda sempre em português brasileiro.

                    SUA FUNÇÃO É:
                    1. Extrair a intenção do relato e agendar a manutenção.
                    2. Categorizar a tarefa automaticamente em UMA das seguintes tags:
                       [Alta Tensão, Termografia, Eficiência Energética, Falha Crítica, Inspeção de Rotina]
                    3. Se o relato indicar perigo iminente (ex: superaquecimento, faísca, arco elétrico, fumaça, ruído anômalo, cheiro de queimado, oscilação crítica de tensão), classifique OBRIGATORIAMENTE como "Falha Crítica" e defina a data/hora sugerida para o momento mais próximo possível a partir de DATA_E_HORA_ATUAL.
                    4. Para os demais relatos, infira a urgência pelo conteúdo e sugira uma data/hora adequada.
                    5. Gere sempre um breve laudo técnico descrevendo o problema e a ação recomendada.

                    DEFINIÇÃO DE INTENÇÕES (INTENTS):
                    - AÇÃO: quando o usuário quer ABRIR, CRIAR ou REGISTRAR uma OS (por exemplo: "registre", "abra uma OS", "tem um problema", "preciso de manutenção"). Nesses casos, responda APENAS com o JSON_ACTION.
                    - CONSULTA: quando o usuário apenas pergunta sobre OSs abertas ou status do sistema (por exemplo: "quais OSs estão abertas?", "o que tem para hoje?"). Nesses casos, responda APENAS em texto natural, sem JSON_ACTION.

                    REGRAS GERAIS:
                    - Se for CONSULTA: responda em texto técnico claro, sem JSON_ACTION.
                    - Se for AÇÃO: siga OBRIGATORIAMENTE o protocolo JSON_ACTION abaixo.
                    - SE O RELATO NÃO DESCREVER NENHUM PROBLEMA OU SOLICITAÇÃO DE MANUTENÇÃO, VOCÊ ESTÁ PROIBIDO DE GERAR JSON_ACTION. Trate como CONSULTA.

                    REGRAS DE TEMPORALIDADE:
                    - DATA_E_HORA_ATUAL representa o momento exato do sistema. Use-a como base para todos os cálculos de "dataHoraSugerida".
                    - Para "Falha Crítica": defina "dataHoraSugerida" como DATA_E_HORA_ATUAL (atendimento imediato).
                    - Para "Alta Tensão" ou "Termografia": sugira dentro das próximas 4 horas.
                    - Para "Eficiência Energética": sugira para o próximo dia útil.
                    - Para "Inspeção de Rotina": sugira dentro da próxima semana.
                    - NUNCA agende OSs entre 00:00 e 06:00, a menos que seja "Falha Crítica" com perigo ativo.

                    PROTOCOLO JSON_ACTION (AÇÃO OBRIGATÓRIA):
                    Quando identificar um relato de problema (INTENT = AÇÃO), gere EXATAMENTE UMA linha com o seguinte formato:
                    JSON_ACTION: {"type":"CREATE_OS","titulo":"Título curto da OS","categoria":"Uma das 5 tags","dataHoraSugerida":"YYYY-MM-DDTHH:mm:ss","laudoTecnico":"Breve laudo técnico com diagnóstico e ação recomendada."}

                    REGRAS DO JSON_ACTION:
                    - "type" é sempre "CREATE_OS".
                    - "titulo": máximo 5 palavras, profissional, sem artigos iniciais. Ex: "Superaquecimento Painel QF-03".
                    - "categoria": OBRIGATORIAMENTE uma destas 5 opções exatas: "Alta Tensão", "Termografia", "Eficiência Energética", "Falha Crítica" ou "Inspeção de Rotina".
                    - "dataHoraSugerida": formato ISO local YYYY-MM-DDTHH:mm:ss, sem fuso, sem milissegundos.
                    - "laudoTecnico": 1 a 3 frases técnicas descrevendo o problema identificado, sua provável causa e a ação de manutenção recomendada.
                    - NÃO use bloco de código, NÃO use markdown. Apenas a linha JSON_ACTION pura.
                    - O JSON_ACTION DEVE ser a ÚNICA coisa na resposta quando for AÇÃO.

                    GUIA DE CATEGORIZAÇÃO:
                    - "Falha Crítica": superaquecimento, faísca, arco elétrico, fumaça, ruído anômalo, cheiro de queimado, oscilação crítica de tensão, curto-circuito, equipamento desligando sozinho. → dataHoraSugerida = DATA_E_HORA_ATUAL.
                    - "Alta Tensão": trabalhos em linhas de AT, subestações, transformadores, disjuntores de alta tensão.
                    - "Termografia": solicitação de inspeção termográfica, análise de temperatura em painéis, cabos, conexões.
                    - "Eficiência Energética": análise de consumo, fator de potência, estudo de carga, correção de harmônicos.
                    - "Inspeção de Rotina": verificação programada, manutenção preventiva sem urgência, limpeza de painéis, aferição de medidores.

                    RELATO DO TÉCNICO (analise e gere o JSON_ACTION ou responda em texto): %s

                    PARA AÇÕES: sua resposta DEVE ser APENAS a linha JSON_ACTION, sem nenhum texto antes ou depois.
                    PARA CONSULTAS: responda apenas em texto técnico natural, sem JSON_ACTION.

                    Exemplo de saída CORRETA para AÇÃO:
                    JSON_ACTION: {"type":"CREATE_OS","titulo":"Superaquecimento QF-03","categoria":"Falha Crítica","dataHoraSugerida":"2026-02-23T14:35:00","laudoTecnico":"Técnico relatou superaquecimento no quadro de força QF-03. Provável sobrecarga ou conexão oxidada. Recomenda-se intervenção imediata com termografia e inspeção de barramentos."}
                    """
                    .formatted(now.toString(), dayOfWeek, taskContext, todayContext, request.message());

            log.debug("🔍 Enviando prompt completo para IA (tamanho={} chars)", prompt.length());
            log.info("===== PROMPT_ENVIADO_PARA_IA =====\n{}\n===== FIM_PROMPT_IA =====", prompt);

            log.info("Tentando chamada de IA com o modelo: {}", modelName);

            String response = chatModel.generate(prompt);

            // Pós-processamento: garantir que, se houver JSON_ACTION, apenas o bloco JSON
            // seja retornado
            String cleanedResponse = sanitizeChatResponse(response);

            log.info("✅ Resposta gerada com sucesso ({} caracteres)", cleanedResponse.length());

            return new AIChatResponseDTO(cleanedResponse, LocalDateTime.now());

        } catch (Exception e) {
            String rawMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido na chamada da IA.";
            String simpleName = e.getClass().getSimpleName();

            boolean isOpenAiHttp = "OpenAiHttpException".equals(simpleName)
                    || simpleName.contains("OpenAiHttp");
            boolean isBalanceIssue = rawMessage.toLowerCase().contains("insufficient balance")
                    || rawMessage.contains("402");

            if (isOpenAiHttp && isBalanceIssue) {
                log.warn("⚠️ IA de chat indisponível por saldo insuficiente: {} - {}", simpleName, rawMessage);

                String friendly = "No momento, meu módulo de análise avançada está descansando. " +
                        "Mas você ainda pode agendar manualmente!";

                return new AIChatResponseDTO(friendly, LocalDateTime.now());
            }

            // Loga o erro completo sempre
            log.error("❌ Erro ao gerar resposta do chat: tipo={}, mensagem={}",
                    simpleName, rawMessage, e);

            // Se for um erro HTTP comum (401/403/429), repassa o conteúdo sem mascarar
            if (rawMessage.contains("401") || rawMessage.contains("403") || rawMessage.contains("429")) {
                throw new RuntimeException("Erro ao chamar a IA (detalhes): " + rawMessage, e);
            }

            if (rawMessage.contains("Configure sua chave de IA no backend para conversar comigo")) {
                throw new RuntimeException(rawMessage, e);
            }

            if (rawMessage.toLowerCase().contains("api key")) {
                throw new RuntimeException(
                        "Problema ao validar a chave da API do provedor de IA: " + rawMessage,
                        e);
            }

            if (rawMessage.toLowerCase().contains("timeout")) {
                throw new RuntimeException(
                        "A chamada para a IA expirou (timeout). Detalhes: " + rawMessage,
                        e);
            }

            if (rawMessage.toLowerCase().contains("rate limit")) {
                throw new RuntimeException(
                        "A IA retornou erro de rate limit. Detalhes: " + rawMessage,
                        e);
            }

            // Fallback: devolve a mensagem real para facilitar debug
            throw new RuntimeException("Erro genérico ao chamar a IA: " + rawMessage, e);
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private String buildAnalysisContext(List<Task> tasks, long total, long completed, long late, long scheduled,
            double rate) {
        return """
                Total de Tarefas: %d
                Tarefas Concluídas: %d
                Tarefas Atrasadas: %d
                Tarefas Agendadas: %d
                Taxa de Conclusão: %.1f%%

                DISTRIBUIÇÃO DE PRIORIDADES:
                %s
                """.formatted(total, completed, late, scheduled, rate, analyzePriorities(tasks));
    }

    private String analyzePriorities(List<Task> tasks) {
        long high = tasks.stream().filter(t -> "HIGH".equals(t.getPriority())).count();
        long medium = tasks.stream().filter(t -> "MEDIUM".equals(t.getPriority())).count();
        long low = tasks.stream().filter(t -> "LOW".equals(t.getPriority())).count();

        return "Alta: %d | Média: %d | Baixa: %d".formatted(high, medium, low);
    }

    private String buildTaskSummary(List<Task> tasks) {
        long pending = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long scheduled = tasks.stream().filter(t -> t.getStatus() == TaskStatus.SCHEDULED).count();
        long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        return "To Do (sem horário): %d | Agendadas: %d | Concluídas: %d".formatted(pending, scheduled, done);
    }

    /**
     * Cria um resumo textual das tarefas de hoje e amanhã para uso como contexto
     * oculto no prompt da IA.
     */
    private String buildContextSummary(List<Task> contextTasks, LocalDate today) {
        if (contextTasks == null || contextTasks.isEmpty()) {
            return "Hoje e amanhã o usuário não têm tarefas agendadas no calendário.";
        }

        // Formato completo ISO local, alinhado com o campo "start" do JSON_ACTION
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        StringBuilder sb = new StringBuilder();
        LocalDate tomorrow = today.plusDays(1);

        sb.append("Contexto de tarefas entre hoje (")
                .append(today)
                .append(") e amanhã (")
                .append(tomorrow)
                .append("): ");

        sb.append("Principais tarefas (máximo 8, em ordem de data/hora): ");

        contextTasks.stream()
                .sorted(Comparator.comparing(
                        t -> t.getStartTime() != null ? t.getStartTime() : LocalDateTime.MAX))
                .limit(8)
                .forEach(task -> {
                    String priorityLabel = "";
                    TaskPriority priority = task.getPriority();
                    if (priority != null) {
                        switch (priority) {
                            case HIGH -> priorityLabel = "P3";
                            case MEDIUM -> priorityLabel = "P2";
                            case LOW -> priorityLabel = "P1";
                        }
                    }

                    sb.append("[");

                    if (!priorityLabel.isEmpty()) {
                        sb.append(priorityLabel).append(" - ");
                    }

                    LocalDateTime start = task.getStartTime();
                    LocalDateTime end = task.getEndTime();

                    if (start != null && end != null) {
                        sb.append(start.format(timeFormatter))
                                .append(" às ")
                                .append(end.format(timeFormatter))
                                .append(" (OCUPADO) - ");
                    } else if (start != null) {
                        sb.append(start.format(timeFormatter))
                                .append(" (OCUPADO) - ");
                    }

                    sb.append(task.getTitle() != null ? task.getTitle() : "(sem título)");

                    if (task.getPriority() != null) {
                        sb.append(" | prioridade ").append(task.getPriority().name());
                    }

                    if (task.getStatus() != null) {
                        sb.append(" | status ").append(task.getStatus().name());
                    }

                    sb.append("]; ");
                });

        return sb.toString().trim();
    }

    /**
     * Sanitiza uma string para uso no prompt, evitando quebras de linha ou
     * caracteres que possam atrapalhar serialização/logs. Mantém o conteúdo
     * semântico, apenas normalizando espaços.
     */
    private String sanitizeForPrompt(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildProductivityPrompt(String context) {
        return """
                Você é um especialista em produtividade cognitiva. Analise os seguintes dados:

                %s

                Forneça uma análise ESTRUTURADA neste formato EXATO:
                SUMMARY: [1-2 frases sobre o estado geral da produtividade]
                INSIGHTS: [Insight 1]; [Insight 2]; [Insight 3]
                RECOMMENDATIONS: [Recomendação 1]; [Recomendação 2]; [Recomendação 3]

                Seja específico, direto e acionável.
                """.formatted(context);
    }

    private String buildPatternsPrompt(String context) {
        return """
                Você é um analista de comportamento cognitivo. Identifique padrões nos dados:

                %s

                Forneça uma análise ESTRUTURADA neste formato EXATO:
                SUMMARY: [Principais padrões identificados]
                INSIGHTS: [Padrão 1]; [Padrão 2]; [Padrão 3]
                RECOMMENDATIONS: [Como otimizar padrão 1]; [Como otimizar padrão 2]; [Como otimizar padrão 3]

                Foque em carga cognitiva e time-blocking.
                """.formatted(context);
    }

    private String buildRecommendationsPrompt(String context) {
        return """
                Você é um coach de produtividade. Com base nos dados:

                %s

                Forneça recomendações ESTRUTURADAS neste formato EXATO:
                SUMMARY: [Diagnóstico principal]
                INSIGHTS: [Insight acionável 1]; [Insight acionável 2]; [Insight acionável 3]
                RECOMMENDATIONS: [Ação específica 1]; [Ação específica 2]; [Ação específica 3]

                Priorize ações de alto impacto e baixo esforço.
                """.formatted(context);
    }

    private String buildGeneralPrompt(String context) {
        return buildProductivityPrompt(context);
    }

    private AIAnalysisResponseDTO parseAIResponse(String aiResponse, int score) {
        try {
            String summary = extractSection(aiResponse, "SUMMARY:");
            List<String> insights = extractList(aiResponse, "INSIGHTS:");
            List<String> recommendations = extractList(aiResponse, "RECOMMENDATIONS:");

            return new AIAnalysisResponseDTO(
                    summary,
                    insights,
                    recommendations,
                    score,
                    LocalDateTime.now());
        } catch (Exception e) {
            log.warn("⚠️ Erro ao parsear resposta da IA. Retornando formato padrão.");
            return new AIAnalysisResponseDTO(
                    aiResponse.substring(0, Math.min(200, aiResponse.length())),
                    List.of("Análise em andamento..."),
                    List.of("Continue registrando suas tarefas para insights mais precisos."),
                    score,
                    LocalDateTime.now());
        }
    }

    /**
     * Sanitiza a resposta do chat da IA para evitar que textos extras quebrem o
     * JSON.
     *
     * - Se contiver JSON_ACTION:, extrai apenas o objeto JSON e o retorna em uma
     * única linha
     * no formato: "JSON_ACTION: { ... }" (sem textos antes/depois).
     * - Se não contiver JSON_ACTION:, devolve o texto original (caso de CONSULTA).
     */
    private String sanitizeChatResponse(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }

        String trimmed = rawResponse.trim();
        String upper = trimmed.toUpperCase();

        if (!upper.contains("JSON_ACTION:")) {
            // CONSULTA: apenas texto, sem JSON_ACTION
            return trimmed;
        }

        // Tenta isolar JSON_ACTION e o objeto JSON associado, ignorando textos extras
        Pattern pattern = Pattern.compile("(?i)JSON_ACTION:\\s*(\\{[\\s\\S]*?\\})");
        Matcher matcher = pattern.matcher(trimmed);

        if (matcher.find()) {
            String jsonObject = matcher.group(1);
            // Compacta quebras de linha e espaços múltiplos dentro do JSON
            String compactJson = jsonObject.replaceAll("\\s+", " ").trim();
            return "JSON_ACTION: " + compactJson;
        }

        // Fallback: se não conseguir achar o objeto JSON, retorna o texto original
        // aparado
        return trimmed;
    }

    private String extractSection(String text, String marker) {
        int start = text.indexOf(marker);
        if (start == -1)
            return "Análise em processamento...";

        start += marker.length();
        int end = text.indexOf("\n", start);
        if (end == -1)
            end = text.length();

        return text.substring(start, end).trim();
    }

    private List<String> extractList(String text, String marker) {
        String section = extractSection(text, marker);
        if (section.isEmpty())
            return new ArrayList<>();

        String[] items = section.split(";");
        List<String> result = new ArrayList<>();
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
