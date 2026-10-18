package com.yourcompany.common.web.handler;

import com.yourcompany.common.base.exception.BizErrorCode;
import com.yourcompany.common.base.exception.BizException;
import com.yourcompany.common.base.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>Controller 内禁止写 try-catch，所有异常在此统一转换为 Result。
 * 业务异常记 WARN，系统异常记 ERROR，且必须传入异常对象以保留堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：已知失败，记 WARN 即可
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("biz exception, code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    /**
     * 参数校验失败：聚合所有字段错误，不只返回第一个
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        log.warn("param invalid, message={}", message);
        return Result.error(BizErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 兜底：未知系统异常，记录完整堆栈，不向前端暴露细节
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("system exception", e);
        return Result.error(BizErrorCode.SYS_ERROR.getCode(), BizErrorCode.SYS_ERROR.getMessage());
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ":" + fieldError.getDefaultMessage();
    }
}
