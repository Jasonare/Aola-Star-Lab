-- Repair post_tags after an older schema and the current schema created duplicate join columns.
-- Back up the database before running. Safe to run more than once.
USE `aola_lab`;

DELIMITER $$
CREATE PROCEDURE `repair_post_tags_columns`()
BEGIN
    DECLARE has_post_id INT DEFAULT 0;
    DECLARE has_legacy_post_id INT DEFAULT 0;
    DECLARE has_primary_key INT DEFAULT 0;
    DECLARE done INT DEFAULT 0;
    DECLARE fk_name VARCHAR(64);
    DECLARE fk_cursor CURSOR FOR
        SELECT DISTINCT constraint_name
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'post_tags'
          AND column_name = 'model$post_id'
          AND referenced_table_name IS NOT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SELECT COUNT(*) INTO has_post_id
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'post_tags'
      AND column_name = 'post_id';

    SELECT COUNT(*) INTO has_legacy_post_id
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'post_tags'
      AND column_name = 'model$post_id';

    IF has_post_id = 0 AND has_legacy_post_id = 1 THEN
        ALTER TABLE `post_tags` RENAME COLUMN `model$post_id` TO `post_id`;
    ELSEIF has_post_id = 1 AND has_legacy_post_id = 1 THEN
        SET @repair_sql = 'INSERT IGNORE INTO `post_tags` (`post_id`, `tags_id`) SELECT `model$post_id`, `tags_id` FROM `post_tags` WHERE `model$post_id` IS NOT NULL';
        PREPARE repair_stmt FROM @repair_sql;
        EXECUTE repair_stmt;
        DEALLOCATE PREPARE repair_stmt;

        OPEN fk_cursor;
        drop_foreign_keys: LOOP
            FETCH fk_cursor INTO fk_name;
            IF done = 1 THEN
                LEAVE drop_foreign_keys;
            END IF;
            SET @repair_sql = CONCAT('ALTER TABLE `post_tags` DROP FOREIGN KEY `', REPLACE(fk_name, '`', '``'), '`');
            PREPARE repair_stmt FROM @repair_sql;
            EXECUTE repair_stmt;
            DEALLOCATE PREPARE repair_stmt;
        END LOOP;
        CLOSE fk_cursor;

        SELECT COUNT(*) INTO has_primary_key
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'post_tags'
          AND constraint_name = 'PRIMARY'
          AND column_name = 'model$post_id';

        IF has_primary_key > 0 THEN
            ALTER TABLE `post_tags` DROP PRIMARY KEY;
            ALTER TABLE `post_tags`
                ADD PRIMARY KEY (`post_id`, `tags_id`);
        END IF;

        ALTER TABLE `post_tags` DROP COLUMN `model$post_id`;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name = 'post_tags'
              AND constraint_name = 'FK_post_tags_post'
              AND column_name = 'post_id'
        ) THEN
            ALTER TABLE `post_tags`
                ADD CONSTRAINT `FK_post_tags_post`
                    FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name = 'post_tags'
              AND constraint_name = 'FKc9w0wcpokniw86jioo5rrb37s'
              AND column_name = 'tags_id'
        ) THEN
            ALTER TABLE `post_tags`
                ADD CONSTRAINT `FKc9w0wcpokniw86jioo5rrb37s`
                    FOREIGN KEY (`tags_id`) REFERENCES `tags` (`id`);
        END IF;
    END IF;
END$$
DELIMITER ;

CALL `repair_post_tags_columns`();
DROP PROCEDURE `repair_post_tags_columns`;
