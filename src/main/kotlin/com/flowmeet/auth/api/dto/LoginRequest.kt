package com.flowmeet.auth.api.dto

import com.flowmeet.common.logging.SensitiveDataMasker
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,
) {
    override fun toString(): String = "LoginRequest(email=${SensitiveDataMasker.email(email)}, password=***)"
}
