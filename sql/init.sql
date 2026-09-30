-- ============================================================
-- agent-scope-java-demo 初始化脚本
-- 用法：mysql -h127.0.0.1 -uroot -p < sql/init.sql
-- 可重复执行：库/表用 IF NOT EXISTS，种子用户用 INSERT IGNORE
-- ============================================================

-- 防止 Windows 上客户端按 gbk 读文件导致中文乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS agent_demo
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE agent_demo;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  username      VARCHAR(50)  NOT NULL COMMENT '用户名（登录账号）',
  password      VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密）',
  nickname      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '昵称',
  email         VARCHAR(100) NOT NULL DEFAULT '' COMMENT '邮箱',
  avatar        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '头像URL',
  status        TINYINT      NOT NULL DEFAULT 1  COMMENT '状态：1正常 0禁用',
  register_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 种子用户：admin / admin123（密码为 BCrypt 哈希）
-- 哈希由 PasswordGeneratorTest 生成
-- ------------------------------------------------------------
INSERT IGNORE INTO t_user (username, password, nickname, email, status)
VALUES ('admin', '$2a$10$cR0QnMeMK65z9w87FD7SQOAJKYLnUHryvwR8gCg99GzkGb5tTZv3a', '管理员', 'admin@example.com', 1);

-- ------------------------------------------------------------
-- token 消耗记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_token_usage (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id          BIGINT       NOT NULL COMMENT '用户ID',
  session_id       VARCHAR(64)  NOT NULL COMMENT '会话ID',
  request_id       VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '请求ID（前端每次发消息生成）',
  agent_name       VARCHAR(64)  NOT NULL COMMENT 'Agent名称',
  model_name       VARCHAR(64)  NOT NULL COMMENT '模型名称',
  input_tokens     INT          NOT NULL DEFAULT 0 COMMENT '输入token数',
  output_tokens    INT          NOT NULL DEFAULT 0 COMMENT '输出token数',
  cached_tokens    INT          NOT NULL DEFAULT 0 COMMENT '缓存命中token数（input子集）',
  duration_seconds DOUBLE       NOT NULL DEFAULT 0 COMMENT '本次模型调用耗时(秒)',
  cost             DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT '费用(元)',
  reply_id         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '框架回复ID（同一次回复可能多次调用）',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='token消耗记录';

-- ------------------------------------------------------------
-- AgentScope 会话状态（AgentStateStore → MysqlAgentStateStore）
-- 列名/类型与官方组件内置 SQL 严格一致，请勿修改
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agentscope_sessions (
  session_id VARCHAR(255) NOT NULL COMMENT '会话槽位（userId:sessionId）',
  state_key  VARCHAR(255) NOT NULL COMMENT '状态键',
  item_index INT          NOT NULL DEFAULT 0 COMMENT '列表状态序号，单值为0',
  state_data LONGTEXT     NOT NULL COMMENT '状态 JSON',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (session_id, state_key, item_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AgentScope会话状态';
