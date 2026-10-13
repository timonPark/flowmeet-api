package com.flowmeet.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * 전역 예외 핸들러.
 *
 * - [ApiException]: errorCode.httpStatus로 응답. 사용자 메시지만 노출.
 * - [MethodArgumentNotValidException]: 400 + fieldErrors.
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
        log.warn("api exception: code={} path={} message={}", ex.errorCode.code, request.requestURI, ex.userMessage, ex)
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                errorCode = ErrorCode.INVALID_REQUEST.code,
                message = ErrorCode.INVALID_REQUEST.defaultMessage,
                path = request.requestURI,
                timestamp = Instant.now(),
                fieldErrors = fieldErrors,
            ),
        )
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
}
