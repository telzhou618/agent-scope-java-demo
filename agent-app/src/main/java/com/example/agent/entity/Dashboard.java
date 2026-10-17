package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据看板 t_dashboard：单文件 HTML 页面，按 dkey 访问
 */
@Data
@TableName("t_dashboard")
public class Dashboard {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 页面唯一标识（UUID 去横杠），URL 使用 */
    private String dkey;

    private Long userId;

    private String name;

    private String description;

    /** 页面代码（整份 HTML） */
    private String code;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}