package com.flowmeet.auth.infrastructure.token

import com.flowmeet.common.security.RefreshTokenProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RefreshTokenHasherTest {
    private fun props(pepper: String) =
        RefreshTokenProperties(
            pepper = pepper,
            cookieSecure = true,
        )

    @Test
    fun `동일 입력과 동일 pepper는 같은 해시를 반환한다`() {
        val hasher = RefreshTokenHasher(props("pepper-at-least-32-bytes-for-local-dev"))

        val first = hasher.hash("opaque-refresh-token-1")
        val second = hasher.hash("opaque-refresh-token-1")

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `pepper가 다르면 같은 입력도 다른 해시를 반환한다`() {
        val a = RefreshTokenHasher(props("pepper-at-least-32-bytes-for-local-dev"))
        val b = RefreshTokenHasher(props("another-pepper-at-least-32-bytes-dev"))

        val tokenA = a.hash("same-opaque-token")
        val tokenB = b.hash("same-opaque-token")

        assertThat(tokenA).isNotEqualTo(tokenB)
    }

    @Test
    fun `서로 다른 입력은 다른 해시를 반환한다`() {
        val hasher = RefreshTokenHasher(props("pepper-at-least-32-bytes-for-local-dev"))

        val one = hasher.hash("token-one")
        val two = hasher.hash("token-two")

        assertThat(one).isNotEqualTo(two)
    }

    @Test
    fun `해시는 Base64URL 문자셋만 포함한다`() {
        val hasher = RefreshTokenHasher(props("pepper-at-least-32-bytes-for-local-dev"))

        val digest = hasher.hash("some-token-value")

        assertThat(digest).matches(Regex("^[A-Za-z0-9_-]+$").toPattern())
    }
}
