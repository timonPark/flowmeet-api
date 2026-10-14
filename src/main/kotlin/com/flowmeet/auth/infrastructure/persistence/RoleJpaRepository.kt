package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleJpaRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Role?
}
