package com.flowmeet.common.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerTest {
    private val objectMapper =
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()

    @Test
    fun `ApiException은 errorCode의 HTTP 상태와 메시지로 응답된다`() {
        mockMvc
            .perform(get("/test/api-exception"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("MEETING-404"))
            .andExpect(jsonPath("$.message").value("회의를 찾을 수 없습니다."))
            .andExpect(jsonPath("$.path").value("/test/api-exception"))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `overrideMessage가 지정되면 defaultMessage 대신 노출된다`() {
        mockMvc
            .perform(get("/test/api-exception-override"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("MEETING-409"))
            .andExpect(jsonPath("$.message").value("overridden"))
    }

    @Test
    fun `Bean Validation 실패는 400과 fieldErrors로 응답된다`() {
        mockMvc
            .perform(
                post("/test/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name": ""}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("COMMON-400"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
            .andExpect(jsonPath("$.fieldErrors[0].reason").exists())
    }

    @Test
    fun `처리되지 않은 예외는 500으로 변환되며 원인 메시지는 응답에 노출되지 않는다`() {
        mockMvc
            .perform(get("/test/generic"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("COMMON-500"))
            .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다."))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-payload"))))
    }

    @Test
    fun `ApiException의 cause message는 응답에 노출되지 않는다`() {
        mockMvc
            .perform(get("/test/api-exception-with-cause"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("COMMON-500"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("db-secret"))))
    }

    @Test
    fun `잘못된 JSON 바디는 400으로 변환된다`() {
        mockMvc
            .perform(
                post("/test/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("COMMON-400"))
    }

    @Test
    fun `경로 변수 타입 불일치는 400으로 변환된다`() {
        mockMvc
            .perform(get("/test/typed/not-a-number"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("COMMON-400"))
    }

    @Test
    fun `필수 쿼리 파라미터 누락은 400으로 변환된다`() {
        mockMvc
            .perform(get("/test/required"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("COMMON-400"))
    }

    @RestController
    @RequestMapping("/test")
    class TestController {
        @GetMapping("/api-exception")
        fun apiException(): Nothing = throw ApiException(ErrorCode.MEETING_NOT_FOUND)

        @GetMapping("/api-exception-override")
        fun apiExceptionOverride(): Nothing = throw ApiException(ErrorCode.MEETING_ALREADY_COMPLETED, overrideMessage = "overridden")

        @GetMapping("/api-exception-with-cause")
        fun apiExceptionWithCause(): Nothing =
            throw ApiException(
                ErrorCode.INTERNAL_ERROR,
                cause = IllegalStateException("db-secret: pw=1234"),
            )

        @PostMapping("/validate")
        fun validate(
            @Valid @RequestBody request: NamedRequest,
        ): String = request.name

        @GetMapping("/typed/{id}")
        fun typed(
            @org.springframework.web.bind.annotation.PathVariable id: Long,
        ): String = id.toString()

        @GetMapping("/required")
        fun required(
            @org.springframework.web.bind.annotation.RequestParam(name = "q") q: String,
        ): String = q

        @GetMapping("/generic")
        fun generic(): Nothing = throw IllegalStateException("secret-payload: token=sk_live_xyz")
    }

    data class NamedRequest(
        @field:NotBlank(message = "name은 비어 있을 수 없습니다.")
        val name: String,
    )
}
