package com.flowmeet.common.security

import com.flowmeet.common.logging.SensitiveDataMasker
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization Bearer 토큰이 유효하면 SecurityContext에 Authentication을 세팅.
 * 무효/누락 토큰은 아무 것도 하지 않고 체인 통과(inert 모드).
 * 401 분기는 후속 이슈(E2.4 — JwtAuthenticationEntryPoint)에서 담당.
 */
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val bearer = request.getHeader(HttpHeaders.AUTHORIZATION)
            val token =
                bearer
                    ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = false) }
                    ?.removePrefix(BEARER_PREFIX)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            if (token != null && tokenProvider.validate(token)) {
                val claims = tokenProvider.parse(token)
                val role = claims[JwtTokenProvider.CLAIM_ROLE] as? String
                if (!role.isNullOrBlank() && !claims.subject.isNullOrBlank()) {
                    val auth =
                        UsernamePasswordAuthenticationToken(
                            claims.subject,
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_$role")),
                        )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        } catch (ex: JwtException) {
            log.debug("JWT inert reject reason={}", ex.javaClass.simpleName)
        } catch (ex: RuntimeException) {
            log.debug(
                "JWT inert reject (unexpected) reason={} msg={}",
                ex.javaClass.simpleName,
                SensitiveDataMasker.scrub(ex.message),
            )
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
