package com.gustavocirino.myday_productivity.exception;

import com.gustavocirino.myday_productivity.exception.TimeSlotConflictException;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 * Captura exceções lançadas nos Services e retorna respostas HTTP adequadas
 * Melhora a experiência do frontend com mensagens de erro estruturadas
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Trata erros de validação do Bean Validation em @RequestBody (@Valid).
     * Ex.: campos obrigatórios ausentes.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("⚠️ Erro de validação (Bean Validation): {}", ex.getMessage());

        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> {
            if (!fields.containsKey(err.getField())) {
                fields.put(err.getField(), err.getDefaultMessage());
            }
        });

        String message = fields.isEmpty()
                ? "Erro de validação"
                : fields.values().iterator().next();

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erro de Validação");
        body.put("message", message);
        body.put("fields", fields);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Trata erros de validação em parâmetros/paths (ConstraintViolationException).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("⚠️ Erro de validação (ConstraintViolation): {}", ex.getMessage());

        Map<String, String> violations = new HashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "param";
            if (!violations.containsKey(path)) {
                violations.put(path, v.getMessage());
            }
        }

        String message = violations.isEmpty()
                ? "Erro de validação"
                : violations.values().iterator().next();

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erro de Validação");
        body.put("message", message);
        body.put("violations", violations);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Trata erros de parsing do corpo da requisição (ex.: JSON inválido).
     * Importante: HttpMessageNotReadableException é RuntimeException; sem este
     * handler,
     * cairia em handleRuntimeException e viraria 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("⚠️ Corpo da requisição inválido (JSON/encoding): {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Requisição Inválida");
        body.put("message", "JSON inválido. Verifique o formato e a codificação UTF-8.");

        if (ex.getMessage() != null) {
            body.put("details", ex.getMessage());
        }

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Trata rotas/recursos inexistentes (ex.: endpoint não mapeado).
     * Evita retornar 500 para casos que são 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("⚠️ Recurso/rota não encontrado(a): {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Recurso Não Encontrado");
        body.put("message", "Recurso não encontrado");

        if (ex.getMessage() != null) {
            body.put("details", ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Trata conflitos de horário com sugestão automática do próximo slot livre.
     * Retorna HTTP 409 com suggestedStart e suggestedEnd para o frontend reagendar.
     */
    @ExceptionHandler(TimeSlotConflictException.class)
    public ResponseEntity<Map<String, Object>> handleTimeSlotConflict(TimeSlotConflictException ex) {
        log.warn("⚠️ Conflito de horário detectado: {}", ex.getMessage());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:00");

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflito de Horário");
        body.put("message", ex.getMessage());
        body.put("conflictingTask", ex.getConflictingTaskTitle());
        body.put("conflict", true);

        if (ex.getSuggestedStart() != null && ex.getSuggestedEnd() != null) {
            body.put("suggestedStart", ex.getSuggestedStart().format(fmt));
            body.put("suggestedEnd",   ex.getSuggestedEnd().format(fmt));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Trata erros de validação (IllegalArgumentException)
     * Exemplo: Título vazio, datas inválidas
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(IllegalArgumentException ex) {
        log.warn("⚠️ Erro de validação: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erro de Validação");
        body.put("message", ex.getMessage());

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Trata erros de recurso não encontrado (RuntimeException com mensagem
     * específica)
     * e erros da API de IA
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {

        // Recurso não encontrado
        if (ex.getMessage() != null && ex.getMessage().contains("não encontrada")) {
            log.warn("⚠️ Recurso não encontrado: {}", ex.getMessage());

            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "Recurso Não Encontrado");
            body.put("message", ex.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        // Erro de IA
        log.error("❌ RuntimeException: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        log.debug("Stack trace:", ex);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Erro no Processamento");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Erro inesperado ao processar sua requisição");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Trata erros genéricos não capturados acima
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        log.error("❌ Exception não tratada: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        log.debug("Stack trace completo:", ex);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Erro Interno do Servidor");
        body.put("message", "Ocorreu um erro inesperado. Por favor, tente novamente.");

        // Adiciona detalhes do erro apenas para debug (não expor em produção)
        if (ex.getMessage() != null) {
            body.put("details", ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
