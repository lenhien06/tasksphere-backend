package com.zone.tasksphere.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.zone.tasksphere.dto.response.ApiErrorResponse;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.utils.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final CookieUtils cookieUtils;

    // 1. Xử lý lỗi không tìm thấy (404)
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(NotFoundException ex) {
        log.warn("Not Found: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // 2. Xử lý lỗi xung đột / trùng lặp (409)
    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflictException(Exception ex) {
        String message = ex.getMessage();
        if (ex instanceof DataIntegrityViolationException) {
            message = "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc hệ thống!";
            if (ex.getCause() != null && ex.getCause().getMessage().contains("Duplicate entry")) {
                message = "Dữ liệu này đã tồn tại trong hệ thống.";
            }
        }
        log.warn("Conflict: {}", message);
        return buildResponse(message, HttpStatus.CONFLICT);
    }

    // 3. Xử lý lỗi Validation (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation Error: {}", errorMessage);
        return buildResponse("Dữ liệu không hợp lệ: " + errorMessage, HttpStatus.BAD_REQUEST);
    }

    // 4. Xử lý lỗi quyền truy cập (403)
    @ExceptionHandler(Forbidden.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(Forbidden ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    // 5. Xử lý lỗi xác thực (401)
    @ExceptionHandler({AuthenticationException.class, SignInRequiredException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(Exception ex, HttpServletResponse response) {
        log.warn("Unauthorized: {}. Clearing cookies.", ex.getMessage());
        cookieUtils.deleteAuthCookies(response);
        return buildResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    // 6. Xử lý lỗi dung lượng file (413)
    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMaxSizeException(Exception ex) {
        log.warn("File too large: {}", ex.getMessage());
        return buildResponse("File vượt quá giới hạn cho phép (25MB)!", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // 6b. Custom file size validation (413)
    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(FileTooLargeException ex) {
        log.warn("File too large: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // 6c. Unsupported MIME type (415)
    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        log.warn("Unsupported file type: {}", ex.getMessage());
        return ResponseEntity.status(415).body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE", ex.getMessage()));
    }

    // 7. Xử lý các lỗi yêu cầu không hợp lệ chung (400)
    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(Exception ex) {
        log.warn("Bad Request: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // 7b. Xử lý JSON không đọc được hoặc enum không hợp lệ (400)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Dữ liệu gửi lên sai định dạng JSON hoặc bị thiếu.";

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String fieldName = ife.getPath().isEmpty() ? "unknown" : ife.getPath().get(0).getFieldName();
            String invalidValue = String.valueOf(ife.getValue());
            String validValues = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message = String.format(
                    "Giá trị '%s' không hợp lệ cho field '%s'. Các giá trị hợp lệ: [%s]",
                    invalidValue, fieldName, validValues
            );
        }

        log.warn("Bad Request (unreadable): {}", message);
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    // 8. Vi phạm quy tắc nghiệp vụ (422)
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRuleException(BusinessRuleException ex) {
        log.warn("Business Rule Violation: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // 8a. BR-18: Sub-task pending (422 với danh sách subtask chưa xong)
    @ExceptionHandler(SubtaskPendingException.class)
    public ResponseEntity<ApiErrorResponse> handleSubtaskPendingException(SubtaskPendingException ex) {
        log.warn("BR-18 subtask pending: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiErrorResponse.builder()
                        .error("SUBTASK_PENDING")
                        .message(ex.getMessage())
                        .meta(java.util.Map.of("pendingSubtasks", ex.getPendingSubtasks()))
                        .build()
        );
    }

    // 8b. Lỗi có mã ổn định (Member/Invite & các API chuẩn hóa)
    @ExceptionHandler(StructuredApiException.class)
    public ResponseEntity<ApiErrorResponse> handleStructuredApiException(StructuredApiException ex) {
        log.warn("Structured API error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(
                ApiErrorResponse.builder()
                        .error(ex.getErrorCode())
                        .message(ex.getMessage())
                        .meta(ex.getMeta())
                        .build()
        );
    }

    // 9. Resource hợp lệ nhưng không còn khả dụng (410)
    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ApiResponse<Void>> handleGoneException(GoneException ex) {
        log.warn("Gone: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.GONE);
    }

    // 10. CATCH-ALL: Lỗi hệ thống (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Internal Server Error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return buildResponse("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau!", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.getReasonPhrase(), message));
    }
}
