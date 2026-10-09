package com.example.agent.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.agent.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理：所有错误统一收敛到 Result 响应体
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未登录或 token 过期：HTTP 401，前端据此跳转登录页
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> notLogin(NotLoginException e) {
        return Result.error(ErrorCode.UNAUTHORIZED, "未登录或登录已过期，请重新登录");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * Sa-Token 角色/权限校验失败：HTTP 200 + code=403，与业务异常风格一致
     */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public Result<Void> noPermission(RuntimeException e) {
        return Result.error(ErrorCode.FORBIDDEN, "无权限执行此操作");
    }

    /**
     * 参数校验失败（@NotBlank/@Size 等）：返回第一条校验消息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .orElse("参数不合法");
        return Result.error(ErrorCode.PARAMS_GET_ERROR, msg);
    }

    /**
     * 兜底
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> unknown(Exception e) {
        log.error("系统异常", e);
        return Result.error();
    }
}
