package com.flowmeet.common.exception

import java.time.Instant

/**
 * 표준 에러 응답 바디.
 *
 * - [errorCode]: 클라이언트 분기용 기계 식별자(`ErrorCode.code`).
 * - [message]: 사용자 노출 메시지(민감정보 금지).
 * - [path]: 요청 경로.
 * - [timestamp]: 서버 발생 시각(UTC, ISO-8601).
 * - [fieldErrors]: Bean Validation 실패 필드 상세. 아니면 null.
 */
data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val path: String,
    val timestamp: Instant,
    val fieldErrors: List<FieldError>? = null,
) {
    data class FieldError(
        val field: String,
        val reason: String,
    )
}
