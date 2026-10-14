package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户文件元数据 t_file（文件本体在 OSS）
 */
@Data
@TableName("t_file")
public class UserFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    private String ext;

    private Long size;

    private String ossKey;

    private String contentType;

    private String sessionId;

    private LocalDateTime createTime;
}