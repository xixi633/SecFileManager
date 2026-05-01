package com.security.filemanager.exception;

import com.security.filemanager.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("文件上传超限: {}", e.getMessage());
        return Result.error(400, "上传文件大小超出限制", "UPLOAD_SIZE_EXCEEDED");
    }

    /**
     * 空指针等系统级异常 — 不向客户端暴露实现细节
     */
    @ExceptionHandler({NullPointerException.class, ClassCastException.class,
            IndexOutOfBoundsException.class, IllegalStateException.class})
    public Result<Void> handleSystemRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("系统运行时异常: {} - ", request.getRequestURI(), e);
        return Result.error(500, "系统错误，请联系管理员", "SYSTEM_RUNTIME_ERROR");
    }

    /**
     * 统一业务异常（带错误码）
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {} ({})", request.getRequestURI(), e.getMessage(), e.getErrorCode());
        return Result.error(e.getHttpCode(), e.getMessage(), e.getErrorCode());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {}", request.getRequestURI(), e.getMessage());
        return Result.error(500, e.getMessage(), "RUNTIME_ERROR");
    }

    /**
     * 参数校验异常 - @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("参数校验失败: {}", message);
        return Result.error(400, message, "VALIDATION_ERROR");
    }

    /**
     * 参数校验异常 - @ModelAttribute
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("参数绑定失败: {}", message);
        return Result.error(400, message, "BIND_ERROR");
    }

    /**
     * 未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} - ", request.getRequestURI(), e);
        return Result.error(500, "系统错误，请联系管理员", "SYSTEM_ERROR");
    }
}
