package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findBySubject(String subject);
}
