package com.example.agent.enums;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 用户权限合集：以逗号分隔的权限码存储在 t_user.permissions（管理员不受限）
 */
@Getter
@AllArgsConstructor
public enum UserPermission {

    /**
     * 定时任务
     */
    SCHEDULE("schedule", "定时任务"),

    /**
     * 上传文件
     */
    UPLOAD_FILE("upload_file", "上传文件");

    private final String code;
    private final String label;

    /**
     * 全部权限码（管理员自动放行全部权限用）
     */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(UserPermission::getCode).toList();
    }

    /**
     * 逗号分隔权限码串 → 权限码列表；null/blank 返回空表
     */
    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return StrUtil.split(csv, ",").stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 权限码串中是否包含指定权限
     */
    public static boolean contains(String csv, UserPermission permission) {
        return parse(csv).contains(permission.getCode());
    }
}