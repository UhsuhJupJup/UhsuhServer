package uhsuhjupjup.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ClassUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MYSQL_DUPLICATE_ENTRY = 1062;
    private static final String NUMBER_REQUIRED = "숫자여야 합니다.";
    private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";
    private static final String REQUIRED_VALUE = "필수 값입니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ec, req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, req.getRequestURI(), fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e,
                                                                HttpServletRequest req) {
        if (e.isForReturnValue()) {
            return handleEtc(e, req);
        }
        List<ErrorResponse.FieldError> fields = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                requestNameOf(result.getMethodParameter()), error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, req.getRequestURI(), fields));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                            HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = List.of(
                new ErrorResponse.FieldError(e.getName(), typeMismatchReason(e.getRequiredType())));
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, req.getRequestURI(), fields));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e,
                                                                HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = List.of(
                new ErrorResponse.FieldError(e.getParameterName(), REQUIRED_VALUE));
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, req.getRequestURI(), fields));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e,
                                                             HttpServletRequest req) {
        if (!isDuplicateKey(e)) {
            return handleEtc(e, req);
        }
        log.warn("Duplicate key conflict at {}", req.getRequestURI());
        return ResponseEntity.status(ErrorCode.RESOURCE_ALREADY_EXISTS.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.RESOURCE_ALREADY_EXISTS, req.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, req.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, req.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleEtc(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, req.getRequestURI()));
    }

    private static String requestNameOf(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.name().isEmpty()) {
            return pathVariable.name();
        }
        return parameter.getParameterName();
    }

    private String typeMismatchReason(Class<?> requiredType) {
        boolean numeric = requiredType != null
                && Number.class.isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(requiredType));
        return numeric ? NUMBER_REQUIRED : INVALID_FORMAT;
    }

    private boolean isDuplicateKey(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                return true;
            }
        }
        return false;
    }
}
