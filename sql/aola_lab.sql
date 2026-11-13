-- MySQL 8.0; execute only against the intended server and database.
-- Existing databases are not altered. Existing tables cause an error.
-- For existing databases, run upgrade_post_comments.sql before deploying the updated application.
CREATE DATABASE IF NOT EXISTS `aola_lab`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `aola_lab`;

CREATE TABLE `chronicle_snapshot` (
    `id` TINYINT NOT NULL,
    `version` BIGINT NOT NULL,
    `nodes_json` LONGTEXT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `CHK_chronicle_snapshot_id` CHECK (`id` = 1),
    CONSTRAINT `CHK_chronicle_snapshot_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO `chronicle_snapshot` (`id`, `version`, `nodes_json`) VALUES
(1, 0, '[{"year":"2010","title":"启航元年","yabi":"滴滴 · 兔小子 · 吉拉","description":"从选择第一只伙伴开始，踏上属于小奥拉的星际冒险。","background":"assets/sky.png"},{"year":"2011","title":"星际探险","yabi":"神武月 · 战无炎","description":"跟着星图探索陌生的星球，和伙伴挑战记忆中的强敌。","background":"assets/sky.png#hue=36"},{"year":"2012","title":"守护之约","yabi":"圣光修罗 · 凯撒","description":"光与暗交错，记忆里的战斗画面随着星光再次亮起。","background":"assets/sky.png#hue=67"},{"year":"2013","title":"英雄集结","yabi":"帝皇龙 · 龙刃","description":"收集、培养、并肩作战，在星际旅途中留下自己的印记。","background":"assets/sky.png#hue=-35"},{"year":"2014","title":"群星之战","yabi":"天使莱特 · 暗夜卡伦","description":"约上旧友，再走一次熟悉的地图，再赴一场星光下的挑战。","background":"assets/sky.png#hue=36"},{"year":"2015","title":"星光再会","yabi":"奇灵王 · 奥天","description":"重访曾经停留的星球，把最舍不得忘记的瞬间留在这里。","background":"assets/sky.png#hue=-35"}]');

CREATE TABLE `app_users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(40) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `signature` VARCHAR(200) NULL,
    `security_version` BIGINT NOT NULL DEFAULT 0,
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
    `ordered_images` TEXT,
    `view_count` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK59ytrjv3ku2g82tyk9sou5lnr` FOREIGN KEY (`author_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `post_tags` (
    `post_id` BIGINT NOT NULL,
    `tags_id` BIGINT NOT NULL,
    PRIMARY KEY (`post_id`, `tags_id`),
    CONSTRAINT `FK_post_tags_post` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
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
