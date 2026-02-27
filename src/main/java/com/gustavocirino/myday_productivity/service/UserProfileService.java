package com.gustavocirino.myday_productivity.service;

import com.gustavocirino.myday_productivity.dto.UserProfileDTO;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.model.UserProfile;
import com.gustavocirino.myday_productivity.repository.UserProfileRepository;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile() {
        Optional<UserProfile> existing = userProfileRepository.findAll().stream().findFirst();
        return existing.map(this::toDto).orElse(null);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getProfileByUserId(Long userId) {
        if (userId == null) {
            return getProfile();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        Optional<UserProfile> existing = userProfileRepository.findByUser(user);
        return existing.map(this::toDto).orElse(null);
    }

    @Transactional
    public UserProfileDTO saveOrUpdate(UserProfileDTO dto) {
        UserProfile entity = userProfileRepository.findAll().stream().findFirst().orElseGet(UserProfile::new);

        entity.setName(dto.name());
        entity.setEmail(dto.email());
        entity.setPhone(dto.phone());
        entity.setPhotoBase64(dto.photoBase64());

        UserProfile saved = userProfileRepository.save(entity);
        log.info("User profile saved/updated with id={}", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public UserProfileDTO saveOrUpdateForUser(Long userId, UserProfileDTO dto) {
        if (userId == null) {
            return saveOrUpdate(dto);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        UserProfile entity = userProfileRepository.findByUser(user).orElseGet(UserProfile::new);
        entity.setUser(user);
        entity.setName(dto.name());
        entity.setEmail(dto.email());
        entity.setPhone(dto.phone());
        entity.setPhotoBase64(dto.photoBase64());

        UserProfile saved = userProfileRepository.save(entity);
        log.info("User profile saved/updated for userId={} with profileId={}", userId, saved.getId());
        return toDto(saved);
    }

    private UserProfileDTO toDto(UserProfile entity) {
        return new UserProfileDTO(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPhotoBase64());
    }
}
