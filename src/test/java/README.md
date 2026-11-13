-- 테스트 전용 DB (root 계정으로 한 번만 실행)
CREATE DATABASE IF NOT EXISTS `project_preen_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `project_preen_test_migration_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `project_preen_test`.* TO 'staybook'@'%';
GRANT ALL PRIVILEGES ON `project_preen_test_migration_test`.* TO 'staybook'@'%';