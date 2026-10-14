package com.flowmeet.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 사용자 역할. schema.sql `roles` 매핑.
 */
@Entity
@Table(name = "roles")
class Role(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    val name: String,
    @Column
    val description: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
