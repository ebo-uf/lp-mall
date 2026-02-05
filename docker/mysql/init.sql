CREATE DATABASE IF NOT EXISTS user_db;
USE user_db;

CREATE TABLE IF NOT EXISTS user
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL UNIQUE,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    INDEX idx_user_id (`user_id`),
    INDEX idx_username (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS product_db;
USE product_db;

CREATE TABLE IF NOT EXISTS product
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    artist_name    VARCHAR(255) NOT NULL,
    year           INT          NOT NULL,
    `condition`    VARCHAR(50)  NOT NULL,
    price          BIGINT       NOT NULL,
    stock          INT          NOT NULL DEFAULT 0,
    seller_id      VARCHAR(36)  NOT NULL,
    sale_start_at  DATETIME,
    is_limited     BOOL         NOT NULL DEFAULT FALSE,
    thumbnail_path VARCHAR(255) NOT NULL,
    created_at     DATETIME(6)           DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)           DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS order_db;
USE order_db;

CREATE TABLE IF NOT EXISTS `order`
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    VARCHAR(36) NOT NULL UNIQUE,
    status      VARCHAR(50) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    product_id  BIGINT      NOT NULL,
    quantity    INT         NOT NULL,
    total_price BIGINT      NOT NULL,
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;