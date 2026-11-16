package com.example.videokyc.auth.repository;

import com.example.videokyc.auth.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.example.videokyc.auth.domain.RoleName;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> lockById(@Param("id") UUID id);

    @Query("select count(distinct u) from UserEntity u join u.roles r where r.name = :role")
    long countUsersWithRole(@Param("role") RoleName role);
}
