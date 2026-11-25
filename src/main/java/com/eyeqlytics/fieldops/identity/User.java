package com.eyeqlytics.fieldops.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "users") @Getter @Setter
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "full_name")
    private String fullName;
    private String email;
    private String phone;
    @Column(name = "password_hash")
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(name = "team_id")
    private Long teamId;
    @Column(name = "zone_id")
    private Long zoneId;
    private boolean active = true;
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
