package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
}
