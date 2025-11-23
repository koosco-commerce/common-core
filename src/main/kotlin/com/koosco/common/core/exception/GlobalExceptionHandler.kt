package com.koosco.common.core.exception

import com.koosco.common.core.error.ApiError
import com.koosco.common.core.error.CommonErrorCode
import com.koosco.common.core.response.ApiResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * BaseException 처리
     */
    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("BaseException occurred: [{}] {}", e.errorCode.code, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.error(e.toApiError()))
    }

    /**
     * Bean validation 오류 처리 @Valid 어노테이션
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        e: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Validation error: {}", e.message)
        val fieldErrors = e.bindingResult.fieldErrors.map { error ->
            ApiError.FieldError(
                field = error.field,
                value = error.rejectedValue,
                reason = error.defaultMessage ?: "Invalid value",
            )
        }
        return ResponseEntity
            .status(CommonErrorCode.VALIDATION_ERROR.status)
            .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, fieldErrors = fieldErrors))
    }

    /**
     * BindException 처리 (주로 GET 요청의 쿼리 파라미터 바인딩 오류)
     */
    @ExceptionHandler(BindException::class)
    fun handleBindException(e: BindException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Bind error: {}", e.message)
        val fieldErrors = e.bindingResult.fieldErrors.map { error ->
            ApiError.FieldError(
                field = error.field,
                value = error.rejectedValue,
                reason = error.defaultMessage ?: "Invalid value",
            )
        }
        return ResponseEntity
            .status(CommonErrorCode.VALIDATION_ERROR.status)
            .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, fieldErrors = fieldErrors))
    }

    /**
     * ConstraintViolationException 처리 (주로 @Validated 어노테이션)
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        e: ConstraintViolationException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Constraint violation: {}", e.message)
        val fieldErrors = e.constraintViolations.map { violation ->
            val propertyPath = violation.propertyPath.toString()
            val field = propertyPath.substringAfterLast('.')
            ApiError.FieldError(
                field = field,
                value = violation.invalidValue,
                reason = violation.message,
            )
        }
        return ResponseEntity
            .status(CommonErrorCode.VALIDATION_ERROR.status)
            .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, fieldErrors = fieldErrors))
    }

    /**
     * 누락된 요청 파라미터 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        e: MissingServletRequestParameterException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Missing parameter: {}", e.parameterName)
        return ResponseEntity
            .status(CommonErrorCode.MISSING_PARAMETER.status)
            .body(
                ApiResponse.error(
                    CommonErrorCode.MISSING_PARAMETER,
                    "Missing parameter: ${e.parameterName}",
                ),
            )
    }

    /**
     * 타입 불일치 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        e: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Type mismatch: {} = {}", e.name, e.value)
        return ResponseEntity
            .status(CommonErrorCode.INVALID_TYPE.status)
            .body(
                ApiResponse.error(
                    CommonErrorCode.INVALID_TYPE,
                    "Invalid type for parameter: ${e.name}",
                ),
            )
    }

    /**
     * 읽을 수 없는 메시지 처리 (예: 잘못된 JSON 포맷)
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        e: HttpMessageNotReadableException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Message not readable: {}", e.message)
        return ResponseEntity
            .status(CommonErrorCode.INVALID_INPUT.status)
            .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT, "Invalid request body"))
    }

    /**
     * 지원되지 않는 HTTP 메서드 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleHttpRequestMethodNotSupportedException(
        e: HttpRequestMethodNotSupportedException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Method not supported: {}", e.method)
        return ResponseEntity
            .status(CommonErrorCode.METHOD_NOT_ALLOWED.status)
            .body(
                ApiResponse.error(
                    CommonErrorCode.METHOD_NOT_ALLOWED,
                    "Method ${e.method} not allowed",
                ),
            )
    }

    /**
     * 지원되지 않는 미디어 타입 처리
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupportedException(
        e: HttpMediaTypeNotSupportedException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Media type not supported: {}", e.contentType)
        return ResponseEntity
            .status(CommonErrorCode.BAD_REQUEST.status)
            .body(ApiResponse.error(CommonErrorCode.BAD_REQUEST, "Media type not supported"))
    }

    /**
     * 예상치 못한 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected error occurred", e)
        return ResponseEntity
            .status(CommonErrorCode.INTERNAL_SERVER_ERROR.status)
            .body(ApiResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR))
    }
}
