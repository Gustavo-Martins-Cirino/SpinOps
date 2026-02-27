package com.gustavocirino.myday_productivity.repository;

import com.gustavocirino.myday_productivity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByAuthToken(String authToken);
}
