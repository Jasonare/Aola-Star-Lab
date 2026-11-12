-- 在关闭 Hibernate 自动建表/更新的现有 MySQL 数据库上执行一次。
-- 旧图片集合不作迁移；文章首次保存时写入有序字段。
ALTER TABLE `posts` ADD COLUMN `ordered_images` TEXT NULL;
