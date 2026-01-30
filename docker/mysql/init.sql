CREATE DATABASE IF NOT EXISTS mall_db;
USE mall_db;

-- User 테이블 생성
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '내부 관리용 PK',
    `user_id` VARCHAR(36) NOT NULL UNIQUE COMMENT '서비스 간 식별자 (UUID)',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '로그인용 아이디',
    `password` VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    `name` VARCHAR(50) NOT NULL COMMENT '사용자 실명',
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '사용자 이메일',
    `created_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    `updated_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',

    -- 성능을 위한 인덱스 설정
    INDEX idx_user_id (`user_id`),
    INDEX idx_username (`username`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;