-- ==============================================================================
-- Flyway Migration Script: V3__repair_demo_password_hash.sql
-- 模块说明: 修复 V2 演示账号的密码哈希
-- 适用数据库: PostgreSQL 12.1及以上兼容
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 修复 V2 演示账号的密码哈希。
-- 仅匹配已发布的错误密文，避免覆盖用户后来主动修改过的密码。
-- ------------------------------------------------------------------------------
UPDATE auth_user
SET password_hash = '$2a$10$ojmRH0hpaeHIKF5u8/0ZCOjtRHCpfJTonxo7AmJ7sJav6pqeTZ0/2',
    updated_by = 'system',
    updated_at = CURRENT_TIMESTAMP
WHERE password_hash = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';
