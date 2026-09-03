-- =============================================================================
-- Core 服务阶段 0 最小数据库基线标记脚本
-- 声明 Core 服务的独立 Flyway 迁移历史与基础环境验证表（阶段二业务表在此基线上扩展）
-- =============================================================================

CREATE TABLE IF NOT EXISTS core_schema_baseline (
    id VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    initialized_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO core_schema_baseline (id, description)
VALUES ('core-stage-0', 'Core 业务微服务阶段 0 数据库基线标记')
ON CONFLICT (id) DO NOTHING;
