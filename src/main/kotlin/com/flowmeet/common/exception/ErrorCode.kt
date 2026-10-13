package com.flowmeet.common.exception

import org.springframework.http.HttpStatus

/**
 * 모든 비즈니스 에러의 식별자.
 *
 * - [code]: 기계용 식별자. `<BC>-<번호>` 형식. 변경 금지(클라이언트 분기 키).
 * - [httpStatus]: HTTP 응답 상태.
 * - [defaultMessage]: 사용자 노출용 기본 메시지. 민감 정보 금지(TRD §11).
 */
enum class ErrorCode(
    val code: String,
    val httpStatus: HttpStatus,
    val defaultMessage: String,
) {
    // 공통 (COMMON-xxx)
    INVALID_REQUEST("COMMON-400", HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다."),
    UNAUTHORIZED("COMMON-401", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("COMMON-403", HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND("COMMON-404", HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    CONFLICT("COMMON-409", HttpStatus.CONFLICT, "리소스 상태 충돌이 발생했습니다."),
    INTERNAL_ERROR("COMMON-500", HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

    // 인증·사용자 (AUTH-xxx, USER-xxx)
    INVALID_CREDENTIALS("AUTH-401", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    EXPIRED_TOKEN("AUTH-402", HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),

    // 회의 (MEETING-xxx) — TRD §4.1 상태 머신 참조
    MEETING_NOT_FOUND("MEETING-404", HttpStatus.NOT_FOUND, "회의를 찾을 수 없습니다."),
    MEETING_ALREADY_COMPLETED("MEETING-409", HttpStatus.CONFLICT, "이미 종료된 회의입니다."),

    // 업무 (TASK-xxx)
    TASK_NOT_FOUND("TASK-404", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."),
}
