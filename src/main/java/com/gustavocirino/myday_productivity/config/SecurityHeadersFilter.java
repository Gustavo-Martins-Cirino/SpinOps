package com.gustavocirino.myday_productivity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro simples para ajustar os headers de segurança (CSP) e permitir
 * a execução do frontend SPA e do chat, evitando bloqueios indevidos
 * em browsers que aplicam Content-Security-Policy mais restritiva.
 *
 * Observação: esta política é intencionalmente permissiva para scripts
 * inline e event handlers do NeuroTask. Ajuste em produção conforme
 * as necessidades reais de segurança do ambiente.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Só define CSP se ainda não houver uma definida por algum proxy/camada externa
        if (response.getHeader("Content-Security-Policy") == null) {
            String csp = "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data: blob:; " +
                    "connect-src 'self' https://api.groq.com;";

            response.setHeader("Content-Security-Policy", csp);
        }

        filterChain.doFilter(request, response);
    }
}
