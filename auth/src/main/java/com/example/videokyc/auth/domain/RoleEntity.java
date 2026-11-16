package com.example.videokyc.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "auth", name = "roles")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 32)
    private RoleName name;

    protected RoleEntity() {
        // JPA
    }

    public RoleEntity(RoleName name) {
        this.name = java.util.Objects.requireNonNull(name);
    }

    public Long getId() { return id; }
    public RoleName getName() { return name; }
}
