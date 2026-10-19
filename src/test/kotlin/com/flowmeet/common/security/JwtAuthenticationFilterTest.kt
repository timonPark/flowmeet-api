package com.flowmeet.common.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

class JwtAuthenticationFilterTest {
    private val secret = "a".repeat(64)
    private val base: Instant = Instant.parse("2026-10-19T07:00:00Z")

    private fun fixedClock(at: Instant = base): Clock =
        mockk<Clock>(relaxed = true).also {
            every { it.instant() } returns at
            every { it.zone } returns ZoneOffset.UTC
        }

    private fun provider(clock: Clock = fixedClock()): JwtTokenProvider =
        JwtTokenProvider(
            JwtProperties(
                secret = secret,
                accessTokenTtl = Duration.ofMinutes(30),
                refreshTokenTtl = Duration.ofDays(14),
            ),
            clock,
        )

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `유효한 Bearer 토큰이면 Authentication을 세팅하고 체인에 통과시킨다`() {
        val issuer = provider()
        val token = issuer.issueAccessToken(subject = "user-42", role = "ADMIN")

        val filter = JwtAuthenticationFilter(issuer)
        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $token")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertThat(auth).isNotNull
        assertThat(auth.principal).isEqualTo("user-42")
        assertThat(auth.authorities.first().authority).isEqualTo("ROLE_ADMIN")
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `Authorization 헤더가 없으면 Context에 손대지 않고 체인에 통과시킨다`() {
        val filter = JwtAuthenticationFilter(provider())
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `Bearer 접두사가 아닌 Authorization은 무시하고 체인에 통과시킨다`() {
        val filter = JwtAuthenticationFilter(provider())
        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Basic dXNlcjpwYXNz")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `만료된 토큰은 Authentication 세팅 없이 체인에 통과시킨다 (inert — 401은 E2 4에서)`() {
        val issuer = provider(clock = fixedClock(base))
        val token = issuer.issueAccessToken(subject = "user-42", role = "USER")

        val expiredCheckProvider = provider(clock = fixedClock(base.plus(Duration.ofMinutes(31))))
        val filter = JwtAuthenticationFilter(expiredCheckProvider)
        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $token")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(res.status).isEqualTo(HttpServletResponse.SC_OK)
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `malformed 토큰은 Authentication 세팅 없이 체인에 통과시킨다`() {
        val filter = JwtAuthenticationFilter(provider())
        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer not.a.jwt")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    private fun signWithoutRole(
        subject: String?,
        at: Instant = base,
    ): String {
        val key = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
        val builder =
            Jwts
                .builder()
                .issuedAt(Date.from(at))
                .expiration(Date.from(at.plus(Duration.ofMinutes(30))))
                .signWith(key, Jwts.SIG.HS256)
        if (subject != null) builder.subject(subject)
        return builder.compact()
    }

    @Test
    fun `동일 키로 서명했지만 role claim이 없는 토큰은 Authentication 세팅하지 않는다`() {
        val filter = JwtAuthenticationFilter(provider())
        val tokenWithoutRole = signWithoutRole(subject = "user-42")

        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $tokenWithoutRole")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `동일 키로 서명했지만 subject가 없는 토큰은 Authentication 세팅하지 않는다`() {
        val filter = JwtAuthenticationFilter(provider())
        val key = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
        val tokenWithoutSubject =
            Jwts
                .builder()
                .claim(JwtTokenProvider.CLAIM_ROLE, "ADMIN")
                .issuedAt(Date.from(base))
                .expiration(Date.from(base.plus(Duration.ofMinutes(30))))
                .signWith(key, Jwts.SIG.HS256)
                .compact()

        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $tokenWithoutSubject")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `다른 서명 키로 발급된 토큰은 Authentication 세팅하지 않는다`() {
        val filter = JwtAuthenticationFilter(provider())
        val attackerToken =
            JwtTokenProvider(
                JwtProperties(
                    secret = "b".repeat(64),
                    accessTokenTtl = Duration.ofMinutes(30),
                    refreshTokenTtl = Duration.ofDays(14),
                ),
                fixedClock(),
            ).issueAccessToken(subject = "evil", role = "ADMIN")

        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $attackerToken")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(req, res) }
    }

    @Test
    fun `parse 중 IllegalArgumentException이 나도 chain은 호출된다 (inert 보장)`() {
        val provider = mockk<JwtTokenProvider>()
        every { provider.parse(any()) } throws IllegalArgumentException("bad key size")

        val filter = JwtAuthenticationFilter(provider)
        val req =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer abc.def.ghi")
            }
        val res = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, res, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(exactly = 1) { chain.doFilter(any<HttpServletRequest>(), any<HttpServletResponse>()) }
    }
}
