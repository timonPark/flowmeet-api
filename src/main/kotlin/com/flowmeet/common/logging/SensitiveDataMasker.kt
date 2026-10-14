package com.flowmeet.common.logging

/**
 * 로그·에러 메시지에 노출되기 전에 민감한 값을 마스킹한다.
 *
 * 응답 바디에 민감 정보를 넣지 않는 것이 1차 방어선이고, 이 유틸은 **로그 2차 방어선** 역할.
 * TRD §11 — 비밀번호·JWT·음성 원문·회의 세부 내용은 로그에도 쓰지 않는다.
 */
object SensitiveDataMasker {
    /**
     * 이메일을 로컬 파트의 양 끝 1자만 남기고 가린다.
     *
     * - `a@b.com` → `*@b.com`
     * - `jj@flowmeet.com` → `j*j@flowmeet.com`
     * - `john.doe@example.com` → `j******e@example.com`
     * - `null` 또는 `@` 없는 입력은 원본 반환하지 않고 `***`.
     */
    fun email(value: String?): String {
        if (value.isNullOrBlank()) return "***"
        val at = value.indexOf('@')
        if (at <= 0 || at == value.length - 1) return "***"
        val local = value.substring(0, at)
        val domain = value.substring(at)
        val masked =
            when (local.length) {
                1 -> "*"
                2 -> local[0] + "*"
                else -> local[0] + "*".repeat(local.length - 2) + local[local.length - 1]
            }
        return "$masked$domain"
    }

    /**
     * 전화번호를 뒤 4자리만 남기고 가린다. `+82-10-1234-5678` → `+82-10-****-5678`.
     *
     * - 숫자가 4자 미만이면 전부 `***`로 처리.
     * - 공백·하이픈 등 포맷은 유지한다.
     */
    fun phone(value: String?): String {
        if (value.isNullOrBlank()) return "***"
        val digits = value.filter(Char::isDigit)
        if (digits.length < 4) return "***"
        val keep = digits.takeLast(4)
        var seenDigits = 0
        val total = digits.length
        val builder = StringBuilder(value.length)
        for (ch in value) {
            if (ch.isDigit()) {
                seenDigits++
                builder.append(if (seenDigits > total - keep.length) ch else '*')
            } else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }

    /**
     * JWT·API 키·bearer 토큰 등 토큰성 문자열. 앞 4자만 남기고 마스킹.
     * 로그 포맷에 쓸 때 **반드시 이 함수를 통과시킨다**.
     */
    fun token(value: String?): String {
        if (value.isNullOrBlank()) return "***"
        val head = value.take(4)
        return "$head…(${value.length}chars)"
    }

    /**
     * 비밀번호·비밀번호 해시 등은 **길이도 노출하지 않는다**. 상수.
     */
    fun password(): String = "***"

    /**
     * 임의 문자열에서 토큰·비밀번호·이메일 패턴을 찾아 치환.
     *
     * 로그 메시지를 직접 쓰는 대신 가능한 한 구조화 필드(key=value)를 쓰고,
     * free-form 메시지를 어쩔 수 없이 로깅할 때만 이 함수를 통과시킨다.
     */
    fun scrub(message: String?): String {
        if (message.isNullOrBlank()) return ""
        var out = message
        out = EMAIL_PATTERN.replace(out) { email(it.value) }
        out = BEARER_PATTERN.replace(out) { "Bearer ${token(it.groupValues[1])}" }
        out = JWT_PATTERN.replace(out) { token(it.value) }
        return out
    }

    private val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val BEARER_PATTERN = Regex("""(?i)Bearer\s+([A-Za-z0-9._~+/=-]+)""")
    private val JWT_PATTERN = Regex("""eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")
}
