package ch.fasteque.sdd_demo

import ch.fasteque.sdd_demo.generated.model.Error as ApiError
import ch.fasteque.sdd_demo.generated.model.ErrorDetail
import ch.fasteque.sdd_demo.generated.model.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.module.kotlin.KotlinInvalidNullException
import java.util.UUID

data class FieldValidationError(val field: String, val code: String, val message: String)

class RequestValidationException(val errors: List<FieldValidationError>) : RuntimeException()

class ResourceNotFoundException(val code: String, override val message: String) : RuntimeException(message)

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException::class)
	fun onResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
		errorResponse(HttpStatus.NOT_FOUND, ex.code, ex.message)

	@ExceptionHandler(ResponseStatusException::class)
	fun onResponseStatusException(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
		val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
		return errorResponse(status, status.name, ex.reason ?: status.reasonPhrase)
	}

	@ExceptionHandler(RequestValidationException::class)
	fun onRequestValidationException(ex: RequestValidationException): ResponseEntity<ErrorResponse> =
		validationFailed(ex.errors.map { ErrorDetail(field = it.field, code = it.code, message = it.message) })

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun onMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
		validationFailed(
			ex.bindingResult.fieldErrors.map {
				ErrorDetail(field = it.field, code = "INVALID_VALUE", message = it.defaultMessage ?: "invalid value")
			}
		)

	@ExceptionHandler(ConstraintViolationException::class)
	fun onConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> =
		validationFailed(
			ex.constraintViolations
				.map {
					val field = it.propertyPath.toString().substringAfterLast('.')
					ErrorDetail(field = field, code = "INVALID_VALUE", message = it.message)
				}
				.sortedBy { it.field }
		)

	@ExceptionHandler(MethodArgumentTypeMismatchException::class)
	fun onMethodArgumentTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
		validationFailed(
			listOf(
				ErrorDetail(
					field = ex.name,
					code = "INVALID_VALUE",
					message = "must be a valid ${ex.requiredType?.simpleName ?: "value"}",
				)
			)
		)

	// Jackson's constructor-based binding fails on the first broken property it hits, so unlike the
	// other validation handlers this can only ever report one detail per response, even though the
	// client sent multiple broken fields.
	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun onHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
		val detail = when (val cause = ex.cause) {
			is KotlinInvalidNullException ->
				ErrorDetail(field = cause.kotlinPropertyName, code = "REQUIRED", message = "must not be null")
			is MismatchedInputException -> {
				val field = cause.path.asReversed().firstOrNull { it.propertyName != null }?.propertyName ?: "request"
				ErrorDetail(field = field, code = "INVALID_VALUE", message = "invalid value for field '$field'")
			}
			else -> ErrorDetail(field = "request", code = "MALFORMED_REQUEST", message = "malformed request body")
		}
		return validationFailed(listOf(detail))
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException::class)
	fun onMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
		errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.message ?: "method not allowed")

	@ExceptionHandler(NoResourceFoundException::class)
	fun onNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
		errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "no route matches this request")

	@ExceptionHandler(Exception::class)
	fun onUnexpectedException(ex: Exception): ResponseEntity<ErrorResponse> =
		errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "an unexpected error occurred")

	private fun validationFailed(details: List<ErrorDetail>): ResponseEntity<ErrorResponse> =
		errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details)

	private fun errorResponse(
		status: HttpStatus,
		code: String,
		message: String,
		details: List<ErrorDetail>? = null,
	): ResponseEntity<ErrorResponse> =
		ResponseEntity.status(status).body(
			ErrorResponse(ApiError(code = code, message = message, traceId = UUID.randomUUID().toString(), details = details))
		)
}
