package com.gustavocirino.myday_productivity.service;

import com.gustavocirino.myday_productivity.dto.*;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.model.UserProfile;
import com.gustavocirino.myday_productivity.repository.UserProfileRepository;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public AuthUserResponseDTO register(AuthRegisterRequestDTO request) {
        String normalizedEmail = normalize(request.email());
        String rawPassword = request.password();

        if (!rawPassword.matches("^(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")) {
            throw new IllegalArgumentException(
                    "A senha deve ter pelo menos 8 caracteres, conter um número e um caractere especial.");
        }

        userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
            throw new IllegalArgumentException("E-mail já cadastrado");
        });

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setVerified(false);
        user.setVerificationCode(generateVerificationCode());

        User savedUser = userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(savedUser);
        profile.setName(request.name());
        profile.setEmail(normalizedEmail);
        userProfileRepository.save(profile);

        sendVerificationEmail(savedUser);

        return new AuthUserResponseDTO(savedUser.getId(), profile.getName(), savedUser.getEmail(),
                savedUser.isVerified(), null);
    }

    @Transactional(readOnly = true)
    public AuthUserResponseDTO login(AuthLoginRequestDTO request) {
        String normalizedEmail = normalize(request.email());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }

        String name = userProfileRepository.findByUser(user).map(UserProfile::getName).orElse(null);
        String token = generateOrRefreshToken(user);

        return new AuthUserResponseDTO(user.getId(), name, user.getEmail(), user.isVerified(), token);
    }

    @Transactional
    public AuthUserResponseDTO verifyEmail(AuthVerifyEmailRequestDTO request) {
        String normalizedEmail = normalize(request.email());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (!request.code().equals(user.getVerificationCode())) {
            throw new IllegalArgumentException("Código de verificação inválido");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        String token = generateOrRefreshToken(user);
        userRepository.save(user);

        String name = userProfileRepository.findByUser(user).map(UserProfile::getName).orElse(null);
        return new AuthUserResponseDTO(user.getId(), name, user.getEmail(), true, token);
    }

    @Transactional
    public void resendVerificationCode(AuthResendCodeRequestDTO request) {
        String normalizedEmail = normalize(request.email());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (user.isVerified())
            return;

        String newCode = generateVerificationCode();
        user.setVerificationCode(newCode);
        userRepository.save(user);

        log.info("Novo código gerado para {}: {}", normalizedEmail, newCode);
        sendVerificationEmail(user);
    }

    private void sendVerificationEmail(User user) {
        try {
            String subject = "Seu código de verificação NeuroTask";
            String html = """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Verifique seu e-mail</title>
                    </head>
                    <body style="margin:0;padding:24px;background-color:#f4f4f5;font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                        <table width="100%" border="0" cellspacing="0" cellpadding="0" role="presentation" style="border-collapse:collapse;">
                            <tr>
                                <td align="center" style="padding:0;margin:0;">
                                    <table width="100%" border="0" cellspacing="0" cellpadding="0" role="presentation" style="max-width:500px;margin:0 auto;border-radius:24px;overflow:hidden;background-color:transparent;">
                                        <tr>
                                            <td style="background-color:#7c3aed;padding:24px 24px 20px 24px;text-align:center;">
                                                <table width="100%" border="0" cellspacing="0" cellpadding="0" role="presentation" style="border-collapse:collapse;">
                                                    <tr>
                                                        <td align="center" style="padding:0;margin:0;">
                                                            <div style="margin:0 auto 10px auto;width:44px;height:44px;border-radius:999px;background-color:rgba(15,23,42,0.16);display:flex;align-items:center;justify-content:center;">
                                                                <span style="font-size:22px;">🧠</span>
                                                            </div>
                                                            <div style="margin:0;font-size:18px;font-weight:700;letter-spacing:0.18em;color:#f9fafb;text-transform:uppercase;">
                                                                NEUROTASK
                                                            </div>
                                                        </td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding:24px;background-color:transparent;">
                                                <table width="100%" border="0" cellspacing="0" cellpadding="0" role="presentation" style="background-color:#ffffff;border-radius:24px;box-shadow:0 18px 40px rgba(79,70,229,0.18);padding:28px 22px;max-width:500px;margin:0 auto;">
                                                    <tr>
                                                        <td style="padding:0;margin:0;">
                                                            <h1 style="margin:0 0 10px 0;font-size:20px;font-weight:600;color:#18181b;">Verifique seu e-mail</h1>
                                                            <p style="margin:0 0 22px 0;font-size:14px;line-height:1.6;color:#4b5563;">
                                                                Use o código abaixo para confirmar seu acesso ao NeuroTask.
                                                            </p>
                                                            <div style="margin:0 auto 22px auto;padding:16px 18px;border-radius:16px;border:1px dotted #7c3aed;background-color:#f8fafc;max-width:320px;">
                                                                <div style="font-size:30px;font-weight:700;letter-spacing:0.35em;color:#111827;white-space:nowrap;font-family:'SF Mono', ui-monospace, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;">
                                                                    {{CODE}}
                                                                </div>
                                                            </div>
                                                            <p style="margin:0 0 6px 0;font-size:12px;line-height:1.5;color:#6b7280;">
                                                                O código expira em 15 minutos e só deve ser usado por você.
                                                            </p>
                                                            <p style="margin:0;font-size:12px;line-height:1.5;color:#9ca3af;">
                                                                Se você não solicitou este acesso, pode simplesmente ignorar esta mensagem.
                                                            </p>
                                                        </td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding:16px 24px 10px 24px;text-align:center;font-size:11px;color:#a1a1aa;line-height:1.5;">
                                                <a href="https://www.google.com/search?q=neurotask.com" style="color:#a1a1aa;text-decoration:none;">https://www.google.com/search?q=neurotask.com</a><br />
                                                <span style="letter-spacing:0.16em;text-transform:uppercase;">DENSIDADE DE TRABALHO</span>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </body>
                    </html>
                    """
                    .replace("{{CODE}}", formatVerificationCode(user.getVerificationCode()));

            sendHtmlEmail(user.getEmail(), subject, html);
            log.info("E-mail de verificação enviado para: {}", user.getEmail());
        } catch (Exception ex) {
            log.error("Erro ao enviar e-mail: {}", ex.getMessage());
        }
    }

    private String formatVerificationCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    private void sendWelcomeEmail(User user) {
        try {
            String name = resolveGreetingName(user);
            String subject = "🚀 Bem-vindo ao time NeuroTask!";
            String html = """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <body style="margin:0;padding:0;background-color:#050505;font-family:sans-serif;">
                        <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color:#050505;padding:40px 0;">
                            <tr>
                                <td align="center">
                                    <table width="450" border="0" cellspacing="0" cellpadding="0" style="background-color:#111111;border:1px solid #222222;border-radius:24px;overflow:hidden;">
                                        <tr>
                                            <td align="center" style="background:linear-gradient(135deg, #22c55e 0%, #a3e635 100%);padding:40px 20px;">
                                                <h1 style="margin:0;color:#050505;letter-spacing:2px;font-size:26px;">BEM-VINDO!</h1>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding:40px 30px;text-align:center;">
                                                <h2 style="color:#ffffff;font-size:22px;margin-bottom:15px;">Olá, {{NAME}}!</h2>
                                                <p style="color:#888888;font-size:16px;line-height:1.6;margin-bottom:30px;">
                                                    Sua conta foi ativada com sucesso. Prepare-se para elevar sua produtividade a um nível neuro-otimizado.
                                                </p>
                                                <a href="http://localhost:8080" style="background-color:#a3e635;color:#050505;padding:15px 35px;border-radius:50px;text-decoration:none;font-weight:bold;font-size:14px;display:inline-block;box-shadow:0 4px 15px rgba(163,230,53,0.3);">ACESSAR MEU PAINEL</a>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding:20px;background-color:#0d0d0d;text-align:center;">
                                                <p style="color:#444444;font-size:11px;margin:0;">Equipe NeuroTask &bull; A Revolução da Produtividade</p>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </body>
                    </html>
                    """
                    .replace("{{NAME}}", name);

            sendHtmlEmail(user.getEmail(), subject, html);
            log.info("E-mail de boas-vindas enviado para: {}", user.getEmail());
        } catch (Exception ex) {
            log.error("Erro ao enviar boas-vindas: {}", ex.getMessage());
        }
    }

    private void sendHtmlEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(mimeMessage);
    }

    private String resolveGreetingName(User user) {
        return userProfileRepository.findByUser(user)
                .map(p -> " " + p.getName().trim())
                .orElse("");
    }

    private String generateVerificationCode() {
        return String.valueOf(100000 + new SecureRandom().nextInt(900000));
    }

    private String generateOrRefreshToken(User user) {
        if (user.getAuthToken() != null && !user.getAuthToken().isBlank()) {
            return user.getAuthToken();
        }
        String token = UUID.randomUUID().toString();
        user.setAuthToken(token);
        return token;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}