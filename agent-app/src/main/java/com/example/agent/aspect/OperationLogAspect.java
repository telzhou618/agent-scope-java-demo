package com.example.agent.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.agent.entity.User;
import com.example.agent.mapper.UserMapper;
import com.example.agent.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：拦截 @OperationLog 标注的接口，落 t_operation_log；
 * 记库失败只告警，绝不影响主流程
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final UserMapper userMapper;

    @Around("@annotation(operationLogAnn)")
    public Object around(ProceedingJoinPoint joinPoint,
                         com.example.agent.annotation.OperationLog operationLogAnn) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object ret = joinPoint.proceed();
            record(joinPoint, operationLogAnn.value(), start, "success");
            return ret;
        } catch (Throwable e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String result = "fail: " + message;
            if (result.length() > 500) {
                result = result.substring(0, 500);
            }
            record(joinPoint, operationLogAnn.value(), start, result);
            throw e;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, String operation, long start, String result) {
        try {
            com.example.agent.entity.OperationLog entry = new com.example.agent.entity.OperationLog();
            entry.setOperation(operation);
            entry.setResult(result);
            entry.setCostMs(System.currentTimeMillis() - start);

            // 未登录（如登录接口本身）记 0 / anonymous
            long userId = 0L;
            String username = "anonymous";
            if (StpUtil.isLogin()) {
                userId = StpUtil.getLoginIdAsLong();
                User user = userMapper.selectById(userId);
                username = user == null ? "" : user.getUsername();
            }
            entry.setUserId(userId);
            entry.setUsername(username);

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                entry.setMethod(request.getMethod());
                entry.setPath(request.getRequestURI());
                entry.setIp(request.getRemoteAddr());
            } else {
                entry.setMethod("");
                entry.setPath("");
                entry.setIp("");
            }

            entry.setParams(serializeParams(joinPoint.getArgs()));
            operationLogService.save(entry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 方法参数序列化为 JSON；servlet/binding/文件类型直接跳过，password 字段脱敏
     */
    private String serializeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> filtered = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof BindingResult || arg instanceof Model
                    || arg instanceof MultipartFile) {
                continue;
            }
            filtered.add(arg);
        }
        if (filtered.isEmpty()) {
            return "";
        }
        String json = filtered.size() == 1
                ? JSON.toJSONString(filtered.get(0))
                : JSON.toJSONString(filtered);
        return maskSensitive(json);
    }

    /**
     * 先按 JSON 解析后递归替换 key 含 password（不区分大小写）的值；
     * 解析失败退化为原样返回（参数由 fastjson2 序列化产生，正常不会走到）
     */
    private String maskSensitive(String json) {
        try {
            Object parsed = JSON.parse(json);
            maskNode(parsed);
            return JSON.toJSONString(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    private void maskNode(Object node) {
        if (node instanceof JSONObject obj) {
            for (String key : new ArrayList<>(obj.keySet())) {
                if (key.toLowerCase().contains("password")) {
                    obj.put(key, "******");
                } else {
                    maskNode(obj.get(key));
                }
            }
        } else if (node instanceof JSONArray arr) {
            for (Object item : arr) {
                maskNode(item);
            }
        }
    }
}
