package com.flowmeet.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SensitiveDataMaskerTest {
    @Nested
    inner class EmailMasking {
        @Test
        fun `일반 이메일은 로컬 파트 양 끝 1자만 남긴다`() {
            assertThat(SensitiveDataMasker.email("john.doe@example.com")).isEqualTo("j******e@example.com")
        }

        @Test
        fun `로컬 파트가 2자이면 뒤 1자만 마스킹`() {
            assertThat(SensitiveDataMasker.email("jj@flowmeet.com")).isEqualTo("j*@flowmeet.com")
        }

        @Test
        fun `로컬 파트가 1자이면 전체 마스킹`() {
            assertThat(SensitiveDataMasker.email("a@b.com")).isEqualTo("*@b.com")
        }

        @Test
        fun `null·빈값·@ 없는 입력은 모두 반환하지 않는다`() {
            assertThat(SensitiveDataMasker.email(null)).isEqualTo("***")
            assertThat(SensitiveDataMasker.email("")).isEqualTo("***")
            assertThat(SensitiveDataMasker.email("no-at-sign")).isEqualTo("***")
            assertThat(SensitiveDataMasker.email("@domain.only")).isEqualTo("***")
            assertThat(SensitiveDataMasker.email("local@")).isEqualTo("***")
        }
    }

    @Nested
    inner class PhoneMasking {
        @Test
        fun `뒤 4자리만 남기고 숫자는 모두 마스킹하되 포맷은 유지`() {
            assertThat(SensitiveDataMasker.phone("+82-10-1234-5678")).isEqualTo("+**-**-****-5678")
        }

        @Test
        fun `공백 포함 입력도 뒤 4자리 유지`() {
            assertThat(SensitiveDataMasker.phone("010 1234 5678")).isEqualTo("*** **** 5678")
        }

        @Test
        fun `숫자가 4자 미만이면 전부 가린다`() {
            assertThat(SensitiveDataMasker.phone("123")).isEqualTo("***")
            assertThat(SensitiveDataMasker.phone("")).isEqualTo("***")
            assertThat(SensitiveDataMasker.phone(null)).isEqualTo("***")
        }
    }

    @Nested
    inner class TokenMasking {
        @Test
        fun `토큰은 앞 4자만 남기고 길이만 노출한다`() {
            val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"
            assertThat(SensitiveDataMasker.token(jwt)).isEqualTo("eyJh…(${jwt.length}chars)")
        }

        @Test
        fun `null과 빈 토큰은 전부 마스킹`() {
            assertThat(SensitiveDataMasker.token(null)).isEqualTo("***")
            assertThat(SensitiveDataMasker.token("")).isEqualTo("***")
        }
    }

    @Nested
    inner class PasswordMasking {
        @Test
        fun `비밀번호는 길이도 노출하지 않는 고정 상수`() {
            assertThat(SensitiveDataMasker.password()).isEqualTo("***")
        }
    }

    @Nested
    inner class ScrubFreeForm {
        @Test
        fun `프리폼 메시지의 이메일을 치환한다`() {
            val scrubbed = SensitiveDataMasker.scrub("login attempt by john.doe@example.com failed")
            assertThat(scrubbed).contains("j******e@example.com")
            assertThat(scrubbed).doesNotContain("john.doe@example.com")
        }

        @Test
        fun `Authorization 헤더 토큰도 치환한다`() {
            val scrubbed = SensitiveDataMasker.scrub("Authorization: Bearer eyJhbGci.payload.sig")
            assertThat(scrubbed).contains("Bearer eyJh…")
            assertThat(scrubbed).doesNotContain("payload.sig")
        }

        @Test
        fun `JWT 형태 토큰이 Bearer 접두사 없이 등장해도 치환한다`() {
            val jwt = "eyJhbGciAAAAAAAAAA.payloadBBBB.sigCCCCCCCC"
            val scrubbed = SensitiveDataMasker.scrub("token=$jwt expired")
            assertThat(scrubbed).contains("eyJh…")
            assertThat(scrubbed).doesNotContain(jwt)
        }

        @Test
        fun `null·빈 메시지는 빈 문자열로 반환`() {
            assertThat(SensitiveDataMasker.scrub(null)).isEmpty()
            assertThat(SensitiveDataMasker.scrub("")).isEmpty()
        }
    }
}
