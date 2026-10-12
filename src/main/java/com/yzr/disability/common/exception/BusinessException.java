package com.yzr.disability.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常，由 GlobalExceptionHandler 统一捕获并返回 Result。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {

    private int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}