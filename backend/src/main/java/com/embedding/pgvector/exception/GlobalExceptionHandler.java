package com.embedding.pgvector.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * REST hatalarını sabit hata kodlu Problem Details yanıtlarına çevirir; beklenmeyen hatayı loglayıp genel 500 döner.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_TYPE_BASE_URI = "https://embedding-pgvector.example/errors/";

    @ExceptionHandler(BaseException.class)
    public ProblemDetail handleBaseException(BaseException exception, HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setType(typeUri(exception.getErrorCode()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", exception.getErrorCode());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(toViolations(exception));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(toViolations(exception));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "Uploaded file exceeds the maximum size");
        problem.setType(typeUri("FILE_TOO_LARGE"));
        problem.setTitle("Content Too Large");
        problem.setProperty("errorCode", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && !hasErrorCode(problem)) {
            String errorCode = frameworkErrorCode(statusCode);
            problem.setType(typeUri(errorCode));
            problem.setProperty("errorCode", errorCode);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setType(typeUri("INTERNAL_ERROR"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        return problem;
    }

    private static boolean hasErrorCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("errorCode");
    }

    private static String frameworkErrorCode(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }

    private static URI typeUri(String errorCode) {
        return URI.create(ERROR_TYPE_BASE_URI + errorCode.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static ResponseEntity<Object> validationFailed(List<Map<String, String>> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(typeUri("VALIDATION_FAILED"));
        problem.setTitle("Validation Failed");
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    private static List<Map<String, String>> toViolations(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
    }

    private static List<Map<String, String>> toViolations(HandlerMethodValidationException exception) {
        List<Map<String, String>> violations = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(fieldError -> violations.add(toViolation(fieldError)));
                continue;
            }
            String field = parameterName(result.getMethodParameter());
            result.getResolvableErrors().forEach(error -> violations.add(violation(field, error.getDefaultMessage())));
        }
        return violations;
    }

    private static Map<String, String> toViolation(FieldError fieldError) {
        return violation(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static Map<String, String> violation(String field, @Nullable String message) {
        return Map.of("field", field, "message", Objects.requireNonNullElse(message, "Invalid value"));
    }

    private static String parameterName(MethodParameter parameter) {
        RequestPart requestPart = parameter.getParameterAnnotation(RequestPart.class);
        if (requestPart != null && !requestPart.name().isEmpty()) {
            return requestPart.name();
        }
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        return Objects.requireNonNullElse(parameter.getParameterName(), "parameter");
    }
}
