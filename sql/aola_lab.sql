-- MySQL 8.0; execute only against the intended server and database.
-- Existing databases are not altered. Existing tables cause an error.
-- For existing databases, run upgrade_post_comments.sql before deploying the updated application.
CREATE DATABASE IF NOT EXISTS `aola_lab`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `aola_lab`;

CREATE TABLE `app_users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(40) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `admin` BIT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `UKspsnwr241e9k9c8p5xl4k45ih` UNIQUE (`username`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `tags` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(40) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `UKt48xdq560gs3gap9g7jg36kgc` UNIQUE (`name`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `resource_folders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `parent_id` BIGINT,
    PRIMARY KEY (`id`),
    CONSTRAINT `UKow2afpj56aiedja4wics9k6gk` UNIQUE (`name`),
    CONSTRAINT `FK_folder_parent` FOREIGN KEY (`parent_id`) REFERENCES `resource_folders` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `login_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `logged_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK7y465cmumbypha9eqq27dw7pw` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `friendship` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `low_user_id` BIGINT NOT NULL,
    `high_user_id` BIGINT NOT NULL,
    `requester_id` BIGINT NOT NULL,
    `status` ENUM('ACCEPTED', 'PENDING') NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `UKdy9upha1t9diye2u2b2n1mt7y` UNIQUE (`low_user_id`, `high_user_id`),
    CONSTRAINT `FKkyd10llxph8i6eju416s0tqbl` FOREIGN KEY (`low_user_id`) REFERENCES `app_users` (`id`),
    CONSTRAINT `FKk38cqbc32p1ai3elwr0f5jjt7` FOREIGN KEY (`high_user_id`) REFERENCES `app_users` (`id`),
    CONSTRAINT `FK2ex62x0lhkqjuod4atralae5o` FOREIGN KEY (`requester_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `posts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `author_id` BIGINT NOT NULL,
    `kind` ENUM('BLOG', 'VIDEO') NOT NULL,
    `title` VARCHAR(150) NOT NULL,
    `body` VARCHAR(10000),
    `visibility` ENUM('FRIENDS', 'PRIVATE', 'PUBLIC') NOT NULL,
    `review` ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL,
    `rejection_reason` VARCHAR(500),
    `file_key` VARCHAR(100),
    `file_type` VARCHAR(100),
    `view_count` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK59ytrjv3ku2g82tyk9sou5lnr` FOREIGN KEY (`author_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `post_tags` (
    `model$post_id` BIGINT NOT NULL,
    `tags_id` BIGINT NOT NULL,
    PRIMARY KEY (`model$post_id`, `tags_id`),
    CONSTRAINT `FK12af4v6mkdjp0askgitum0h4x` FOREIGN KEY (`model$post_id`) REFERENCES `posts` (`id`),
    CONSTRAINT `FKc9w0wcpokniw86jioo5rrb37s` FOREIGN KEY (`tags_id`) REFERENCES `tags` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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

CREATE TABLE `comments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `post_id` BIGINT NOT NULL,
    `author_id` BIGINT NOT NULL,
    `body` VARCHAR(1000) NOT NULL,
    `parent_id` BIGINT,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FKh4c7lvsc298whoyd4w9ta25cr` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
    CONSTRAINT `FKh1svnilxctesxuj1ycundagfj` FOREIGN KEY (`author_id`) REFERENCES `app_users` (`id`),
    CONSTRAINT `FK_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `resources` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `folder_id` BIGINT NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `file_key` VARCHAR(100) NOT NULL,
    `file_type` VARCHAR(100) NOT NULL,
    `visible` BIT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK1vip3yasb3769qv3v7lykvqm2` FOREIGN KEY (`folder_id`) REFERENCES `resource_folders` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
