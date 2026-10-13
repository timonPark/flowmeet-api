package com.flowmeet.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

/**
 * 전역 예외 핸들러.
 *
 * - [ApiException]: errorCode.httpStatus로 응답. 사용자 메시지만 노출.
 * - 입력 오류(validation / malformed body / 타입 변환 / 필수 파라미터 누락): 400.
 * - 그 외: 500. 원인 메시지는 로그에만 기록(응답에 노출 금지 — TRD §11).
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        ex: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        if (ex.errorCode.httpStatus.is5xxServerError) {
            log.error("api exception (5xx): code={} path={}", ex.errorCode.code, request.requestURI, ex)
        } else {
            // 4xx: 비즈니스 흐름상 정상적인 거부. stack trace와 cause는 생략해 로그 노이즈·민감정보 유출을 줄인다.
            log.warn("api exception (4xx): code={} path={}", ex.errorCode.code, request.requestURI)
        }
        return ResponseEntity.status(ex.errorCode.httpStatus).body(
            ErrorResponse(
                errorCode = ex.errorCode.code,
                message = ex.userMessage,
                path = request.requestURI,
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val fieldErrors =
            ex.bindingResult.fieldErrors.map {
                ErrorResponse.FieldError(
                    field = it.field,
                    reason = it.defaultMessage ?: "유효하지 않은 값입니다.",
                )
            }
        return badRequest(request, fieldErrors = fieldErrors)
    }

    /**
     * JSON 파싱 오류, 타입 변환 오류, 필수 파라미터 누락 등 요청 자체가 서버에서 해석 불가한 경우.
     * 원인 메시지는 응답에 노출하지 않는다.
     */
    @ExceptionHandler(
        value = [
            HttpMessageNotReadableException::class,
            MethodArgumentTypeMismatchException::class,
            MissingServletRequestParameterException::class,
        ],
    )
    fun handleBadRequest(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("bad request: path={} type={}", request.requestURI, ex.javaClass.simpleName)
        return badRequest(request)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.error("unhandled exception: path={}", request.requestURI, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                errorCode = ErrorCode.INTERNAL_ERROR.code,
                message = ErrorCode.INTERNAL_ERROR.defaultMessage,
                path = request.requestURI,
                timestamp = Instant.now(),
            ),
        )
    }

    private fun badRequest(
        request: HttpServletRequest,
        fieldErrors: List<ErrorResponse.FieldError>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                errorCode = ErrorCode.INVALID_REQUEST.code,
                message = ErrorCode.INVALID_REQUEST.defaultMessage,
                path = request.requestURI,
                timestamp = Instant.now(),
                fieldErrors = fieldErrors,
            ),
        )
}
