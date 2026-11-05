package com.example.videokyc.auth.repository;

import com.example.videokyc.auth.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.videokyc.auth.domain.RoleName;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(distinct u) from UserEntity u join u.roles r where r.name = :role")
    long countUsersWithRole(@Param("role") RoleName role);
}
