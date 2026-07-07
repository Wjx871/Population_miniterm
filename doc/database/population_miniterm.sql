CREATE DATABASE IF NOT EXISTS population_miniterm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE population_miniterm;

CREATE TABLE IF NOT EXISTS residents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    birth_date DATE NOT NULL,
    id_card_number VARCHAR(18) NOT NULL,
    phone_number VARCHAR(30) NULL,
    province VARCHAR(100) NULL,
    city VARCHAR(100) NULL,
    district VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_residents_id_card_number (id_card_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO residents (
    name,
    gender,
    birth_date,
    id_card_number,
    phone_number,
    province,
    city,
    district,
    address,
    active
) VALUES (
    '张三',
    'MALE',
    '1999-01-01',
    '110101199901010011',
    '13800138000',
    '北京市',
    '北京市',
    '东城区',
    '示例地址',
    b'1'
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    phone_number = VALUES(phone_number),
    updated_at = CURRENT_TIMESTAMP(6);
