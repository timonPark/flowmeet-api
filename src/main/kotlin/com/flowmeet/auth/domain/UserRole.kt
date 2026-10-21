package com.flowmeet.auth.domain

enum class UserRole {
    ADMIN,
    EXECUTIVE,
    TEAM_LEADER,
    TEAM_MEMBER,
    ;

    companion object {
        fun fromName(name: String?): UserRole = name?.let { candidate -> entries.firstOrNull { it.name == candidate } } ?: TEAM_MEMBER
    }
}
