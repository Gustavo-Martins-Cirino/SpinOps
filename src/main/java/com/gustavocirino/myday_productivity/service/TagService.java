package com.gustavocirino.myday_productivity.service;

import com.gustavocirino.myday_productivity.dto.TagCreateDTO;
import com.gustavocirino.myday_productivity.dto.TagResponseDTO;
import com.gustavocirino.myday_productivity.model.Tag;
import com.gustavocirino.myday_productivity.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de gerenciamento de Tags.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepository;

    @Transactional
    public TagResponseDTO createTag(TagCreateDTO dto) {
        if (tagRepository.existsByName(dto.name())) {
            throw new IllegalArgumentException("Tag com nome '" + dto.name() + "' já existe");
        }

        Tag tag = new Tag();
        tag.setName(dto.name());
        tag.setColor(dto.color() != null ? dto.color() : "#6366F1"); // Cor padrão

        tag = tagRepository.save(tag);
        log.info("✅ Tag criada: {}", tag.getName());

        return toResponseDTO(tag);
    }

    public List<TagResponseDTO> getAllTags() {
        return tagRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public TagResponseDTO getTagById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag não encontrada: " + id));
        return toResponseDTO(tag);
    }

    @Transactional
    public void deleteTag(Long id) {
        if (!tagRepository.existsById(id)) {
            throw new RuntimeException("Tag não encontrada: " + id);
        }
        tagRepository.deleteById(id);
        log.info("🗑️ Tag removida: {}", id);
    }

    private TagResponseDTO toResponseDTO(Tag tag) {
        return new TagResponseDTO(
                tag.getId(),
                tag.getName(),
                tag.getColor(),
                tag.getTasks() != null ? tag.getTasks().size() : 0);
    }
}
