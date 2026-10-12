package com.yzr.disability.common.exception;

import com.yzr.disability.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理，统一返回 Result。
 *
 * <p>注意：Spring Security 过滤器链中的认证/授权异常不经过此处，仍由 SecurityConfig 处理。
 * 未知异常仅记录日志，不向客户端暴露堆栈、SQL 等敏感信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验异常（@Valid / @Validated） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 方法级权限拒绝（@PreAuthorize）。
     *
     * <p>注意：此类异常发生在 Controller 调用阶段，不会被 SecurityConfig 的
     * AccessDeniedHandler 处理（后者仅处理过滤器链内的授权异常），
     * 若不加此分支会被兜底为 500。此处统一返回 403，保持与过滤器链一致。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("访问被拒绝: {}", e.getMessage());
        return Result.error(403, "没有操作权限");
    }

    /** 请求体不可读（JSON 格式错误、空 Body、类型不匹配） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadableException(HttpMessageNotReadableException e) {
        // 仅在日志中记录详情，不向客户端暴露解析细节
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求参数格式错误，请检查请求体");
    }

    /** 缺少必填请求参数（@RequestParam 必填项缺失） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParamException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(400, "缺少必填参数: " + e.getParameterName());
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return Result.error(405, "请求方式不支持: " + e.getMethod());
    }

    /** 接口不存在（无匹配 Handler，需开启 throwExceptionIfNoHandlerFound 才会触发） */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.error(404, "接口不存在");
    }

    /** 静态资源不存在（Spring 6.1+ 由静态资源处理器抛出，本机实测为此类） */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getMessage());
        return Result.error(404, "资源不存在");
    }

    /** 其他未捕获异常：仅记录日志，返回通用错误 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("服务器内部错误，请稍后重试");
    }
}