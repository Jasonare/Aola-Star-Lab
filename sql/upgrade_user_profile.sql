-- MySQL 8.0：执行前备份数据库，并确认 app_users 尚无以下列。
-- 在目标数据库执行一次；已有用户名唯一索引和 utf8mb4 排序规则保持不变。
ALTER TABLE `app_users`
    ADD COLUMN `signature` VARCHAR(200) NULL,
    ADD COLUMN `security_version` BIGINT NOT NULL DEFAULT 0;
