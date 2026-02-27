package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.UserProfileDTO;
import com.gustavocirino.myday_productivity.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Profile", description = "Gerenciamento de perfil do usuário único da aplicação")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(summary = "Obter perfil do usuário")
    public ResponseEntity<UserProfileDTO> getProfile() {
        UserProfileDTO dto = userProfileService.getProfile();
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @Operation(summary = "Criar ou atualizar perfil do usuário")
    public ResponseEntity<UserProfileDTO> saveProfile(@RequestBody UserProfileDTO dto) {
        UserProfileDTO saved = userProfileService.saveOrUpdate(dto);
        return ResponseEntity.ok(saved);
    }
}
