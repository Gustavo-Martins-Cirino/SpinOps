package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.AuthLoginRequestDTO;
import com.gustavocirino.myday_productivity.dto.AuthRegisterRequestDTO;
import com.gustavocirino.myday_productivity.dto.AuthResendCodeRequestDTO;
import com.gustavocirino.myday_productivity.dto.AuthUserResponseDTO;
import com.gustavocirino.myday_productivity.dto.AuthVerifyEmailRequestDTO;
import com.gustavocirino.myday_productivity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Autenticação", description = "Cadastro, login e verificação de e-mail de usuários do NeuroTask")
public class UserController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registrar novo usuário", description = "Cria um usuário com senha criptografada e envia código de verificação por e-mail")
    public ResponseEntity<AuthUserResponseDTO> register(@RequestBody AuthRegisterRequestDTO request) {
        AuthUserResponseDTO created = authService.register(request);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/login")
    @Operation(summary = "Login de usuário", description = "Valida credenciais usando BCrypt e retorna os dados básicos do usuário")
    public ResponseEntity<AuthUserResponseDTO> login(@RequestBody AuthLoginRequestDTO request) {
        AuthUserResponseDTO logged = authService.login(request);
        return ResponseEntity.ok(logged);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verificar e-mail", description = "Confirma o e-mail do usuário usando o código enviado por e-mail")
    public ResponseEntity<AuthUserResponseDTO> verify(@RequestBody AuthVerifyEmailRequestDTO request) {
        AuthUserResponseDTO verified = authService.verifyEmail(request);
        return ResponseEntity.ok(verified);
    }

    @PostMapping("/resend-code")
    @Operation(summary = "Reenviar código de verificação", description = "Gera um novo código de 6 dígitos e envia por e-mail (também aparece no log do servidor)")
    public ResponseEntity<Void> resendCode(@RequestBody AuthResendCodeRequestDTO request) {
        authService.resendVerificationCode(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Ponto de saída lógico; o frontend limpa o estado local")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
