package com.gustavocirino.myday_productivity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do Swagger/OpenAPI para documentação automática das APIs.
 * 
 * Acesse a documentação em: http://localhost:8080/swagger-ui/index.html
 * JSON Schema em: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI neurotaskOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Servidor de Desenvolvimento Local");

        Contact contact = new Contact();
        contact.setName("Gustavo Cirino");
        contact.setEmail("gustavocirino@example.com");
        contact.setUrl("https://github.com/gustavocirino");

        License mitLicense = new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");

        Info info = new Info()
                .title("NeuroTask API")
                .version("1.0.0")
                .contact(contact)
                .description("API REST para sistema de produtividade cognitiva com Time-Blocking e IA")
                .summary("Plataforma que combina time-blocking, Google Gemini AI e analytics comportamental")
                .license(mitLicense);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
