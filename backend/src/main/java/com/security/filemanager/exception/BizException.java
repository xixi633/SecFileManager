package com.security.filemanager.exception;

/**
 * 统一业务异常，包含可观测错误码
 */
public class BizException extends RuntimeException {

    private final Integer httpCode;
    private final String errorCode;

    public BizException(Integer httpCode, String errorCode, String message) {
        super(message);
        this.httpCode = httpCode;
        this.errorCode = errorCode;
    }

    public Integer getHttpCode() {
        return httpCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static BizException badRequest(String errorCode, String message) {
        return new BizException(400, errorCode, message);
    }

    public static BizException conflict(String errorCode, String message) {
        return new BizException(409, errorCode, message);
    }

    public static BizException internal(String errorCode, String message) {
        return new BizException(500, errorCode, message);
    }
}
