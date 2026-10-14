package com.example.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的文件列表项
 */
@Data
@Builder
public class UserFileVO {

    private Long id;

    private String fileName;

    private String ext;

    private Long size;

    private LocalDateTime createTime;

    /** OSS 签名下载地址（每次列表时新生成） */
    private String downloadUrl;
}