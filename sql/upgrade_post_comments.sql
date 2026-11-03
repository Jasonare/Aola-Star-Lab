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

ALTER TABLE `posts`
    ADD COLUMN `view_count` BIGINT NOT NULL DEFAULT 0;

CREATE TABLE `post_likes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `post_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_post_like_user` UNIQUE (`post_id`, `user_id`),
    CONSTRAINT `FK_post_like_post` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
    CONSTRAINT `FK_post_like_user` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
