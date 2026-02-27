package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.TagCreateDTO;
import com.gustavocirino.myday_productivity.dto.TagResponseDTO;
import com.gustavocirino.myday_productivity.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para gerenciamento de Tags.
 * 
 * Endpoints:
 * - GET /api/tags - Listar todas as tags
 * - POST /api/tags - Criar nova tag
 * - GET /api/tags/{id} - Buscar tag por ID
 * - DELETE /api/tags/{id} - Remover tag
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Slf4j
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponseDTO>> getAllTags() {
        log.info("📋 GET /api/tags - Listando todas as tags");
        return ResponseEntity.ok(tagService.getAllTags());
    }

    @PostMapping
    public ResponseEntity<TagResponseDTO> createTag(@Valid @RequestBody TagCreateDTO dto) {
        log.info("➕ POST /api/tags - Criando tag: {}", dto.name());
        return ResponseEntity.ok(tagService.createTag(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponseDTO> getTagById(@PathVariable Long id) {
        log.info("🔍 GET /api/tags/{} - Buscando tag", id);
        return ResponseEntity.ok(tagService.getTagById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        log.info("🗑️ DELETE /api/tags/{} - Removendo tag", id);
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }
}
