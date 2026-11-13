-- MySQL 8.0: select the intended database before running this migration.
-- Run before deploying the API. Re-running never overwrites an existing snapshot,
-- including an intentionally empty array. CREATE TABLE does not alter existing tables.
CREATE TABLE IF NOT EXISTS `chronicle_snapshot` (
    `id` TINYINT NOT NULL,
    `version` BIGINT NOT NULL,
    `nodes_json` LONGTEXT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `CHK_chronicle_snapshot_id` CHECK (`id` = 1),
    CONSTRAINT `CHK_chronicle_snapshot_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO `chronicle_snapshot` (`id`, `version`, `nodes_json`) VALUES
(1, 0, '[{"year":"2010","title":"启航元年","yabi":"滴滴 · 兔小子 · 吉拉","description":"从选择第一只伙伴开始，踏上属于小奥拉的星际冒险。","background":"assets/sky.png"},{"year":"2011","title":"星际探险","yabi":"神武月 · 战无炎","description":"跟着星图探索陌生的星球，和伙伴挑战记忆中的强敌。","background":"assets/sky.png#hue=36"},{"year":"2012","title":"守护之约","yabi":"圣光修罗 · 凯撒","description":"光与暗交错，记忆里的战斗画面随着星光再次亮起。","background":"assets/sky.png#hue=67"},{"year":"2013","title":"英雄集结","yabi":"帝皇龙 · 龙刃","description":"收集、培养、并肩作战，在星际旅途中留下自己的印记。","background":"assets/sky.png#hue=-35"},{"year":"2014","title":"群星之战","yabi":"天使莱特 · 暗夜卡伦","description":"约上旧友，再走一次熟悉的地图，再赴一场星光下的挑战。","background":"assets/sky.png#hue=36"},{"year":"2015","title":"星光再会","yabi":"奇灵王 · 奥天","description":"重访曾经停留的星球，把最舍不得忘记的瞬间留在这里。","background":"assets/sky.png#hue=-35"}]')
ON DUPLICATE KEY UPDATE `id` = `id`;
