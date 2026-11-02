-- Upgrade existing MySQL 8 databases for post update timestamps and threaded comments.
-- Run once against the intended aola_lab database.
USE `aola_lab`;

ALTER TABLE `posts`
    ADD COLUMN `updated_at` DATETIME(6) NULL;

UPDATE `posts`
SET `updated_at` = `created_at`
WHERE `updated_at` IS NULL;

ALTER TABLE `posts`
    MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;

ALTER TABLE `comments`
    ADD COLUMN `parent_id` BIGINT NULL;

ALTER TABLE `comments`
    ADD CONSTRAINT `FK_comment_parent`
        FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`);
