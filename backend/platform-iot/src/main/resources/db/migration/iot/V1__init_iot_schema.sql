-- =============================================================================
-- IoT 服务阶段 0 最小数据库基线标记脚本
-- 声明 IoT 服务的独立 Flyway 迁移历史与基础环境验证表（阶段二设备与遥测表在此基线上扩展）
-- =============================================================================

CREATE TABLE IF NOT EXISTS iot_schema_baseline (
    id VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    initialized_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO iot_schema_baseline (id, description)
VALUES ('iot-stage-0', 'IoT 物联网微服务阶段 0 数据库基线标记')
ON CONFLICT (id) DO NOTHING;
