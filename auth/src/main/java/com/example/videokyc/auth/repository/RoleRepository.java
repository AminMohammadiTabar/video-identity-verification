package com.example.videokyc.auth.repository;

import com.example.videokyc.auth.domain.RoleEntity;
import com.example.videokyc.auth.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(RoleName name);
}
