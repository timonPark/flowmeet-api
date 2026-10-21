package com.flowmeet.common.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class, CorsProperties::class, RefreshTokenProperties::class)
class SecurityConfig {
    /**
     * S1-E1.1 뼈대: 모든 요청 permitAll. 실제 401 분기는 S1-E2.4에서 EntryPoint + authenticated()로 전환.
     * JwtAuthenticationFilter는 chain에 등록돼 있고, 유효 토큰이 오면 Authentication을 세팅한다(inert 모드).
     */
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtAuthenticationFilter(tokenProvider: JwtTokenProvider): JwtAuthenticationFilter = JwtAuthenticationFilter(tokenProvider)

    /**
     * Spring Boot는 jakarta.servlet.Filter Bean을 서블릿 체인에도 자동 등록한다.
     * Security 체인에서만 관리하도록 자동 등록을 비활성화한다.
     */
    @Bean
    fun jwtAuthenticationFilterRegistration(filter: JwtAuthenticationFilter): FilterRegistrationBean<JwtAuthenticationFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun corsConfigurationSource(cors: CorsProperties): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = cors.allowedOrigins
                allowedMethods = cors.allowedMethods
                allowedHeaders = cors.allowedHeaders
                allowCredentials = cors.allowCredentials
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
