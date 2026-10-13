package com.flowmeet.common.exception

/**
 * 모든 비즈니스 예외의 베이스. 이 외 예외는 전역 핸들러가 500으로 변환한다.
 *
 * - [errorCode]: 상태·식별자·기본 메시지의 단일 출처.
 * - [overrideMessage]: 사용자 노출용으로 기본 메시지를 바꿀 때. 민감정보 금지.
 * - [cause]: 원인 예외는 전파하되 로그로만 노출. 응답에는 노출하지 않는다.
 */
open class ApiException(
    val errorCode: ErrorCode,
    overrideMessage: String? = null,
    cause: Throwable? = null,
) : RuntimeException(overrideMessage ?: errorCode.defaultMessage, cause) {
    val userMessage: String get() = message ?: errorCode.defaultMessage
}
