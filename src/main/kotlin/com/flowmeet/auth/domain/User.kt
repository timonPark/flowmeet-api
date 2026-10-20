package com.flowmeet.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

enum class SystemPermission { ADMIN, USER }

@Entity
@Table(name = "users")
class User private constructor(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 100)
    val name: String,
    @Column(nullable = false, unique = true, length = 255)
    val email: String,
    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,
    @Column(name = "team_id")
    val teamId: UUID? = null,
    @Column(name = "role_id")
    val roleId: UUID? = null,
    @Column(name = "manager_id")
    val managerId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "system_permission", nullable = false, columnDefinition = "system_permission")
    val systemPermission: SystemPermission = SystemPermission.USER,
    @Column(name = "temporary_password", nullable = false)
    val temporaryPassword: Boolean = true,
    @Column(nullable = false)
    val active: Boolean = true,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
) {
    companion object {
        fun create(
            name: String,
            email: String,
            rawPassword: String,
            encoder: PasswordEncoder,
            systemPermission: SystemPermission = SystemPermission.USER,
            teamId: UUID? = null,
            roleId: UUID? = null,
            managerId: UUID? = null,
            temporaryPassword: Boolean = true,
        ): User =
            User(
                name = name,
                email = email,
                passwordHash = encoder.encode(rawPassword),
                teamId = teamId,
                roleId = roleId,
                managerId = managerId,
                systemPermission = systemPermission,
                temporaryPassword = temporaryPassword,
            )
    }
}
