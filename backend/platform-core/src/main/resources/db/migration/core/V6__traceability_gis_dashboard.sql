-- =============================================================================
-- Core S7：二维地图展示配置与看板投影缓存元数据。
-- 本迁移不复制库存、订单、工单、质量或 IoT 事实；业务事实仍由各领域应用服务提供。
-- =============================================================================

CREATE TABLE IF NOT EXISTS gis_site_map (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    map_code VARCHAR(64) NOT NULL,
    map_name VARCHAR(128) NOT NULL,
    background_type VARCHAR(16) NOT NULL CHECK (background_type IN ('PNG', 'JPEG', 'WEBP')),
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gis_site_map_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_site_map_code
    ON gis_site_map (tenant_id, map_code) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS gis_site_map_asset (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_map_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(32) NOT NULL CHECK (mime_type IN ('image/png', 'image/jpeg', 'image/webp')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gis_site_map_asset_map UNIQUE (tenant_id, site_map_id),
    CONSTRAINT fk_gis_site_map_asset_map
        FOREIGN KEY (tenant_id, site_map_id) REFERENCES gis_site_map (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_gis_site_map_asset_tenant
    ON gis_site_map_asset (tenant_id, site_map_id) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS gis_map_point (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_map_id UUID NOT NULL,
    entity_type VARCHAR(32) NOT NULL CHECK (entity_type IN ('WAREHOUSE', 'PRODUCTION_AREA', 'DEVICE')),
    entity_id UUID NOT NULL,
    x_percent NUMERIC(5, 2) NOT NULL CHECK (x_percent >= 0 AND x_percent <= 100),
    y_percent NUMERIC(5, 2) NOT NULL CHECK (y_percent >= 0 AND y_percent <= 100),
    rotation NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (rotation >= -360 AND rotation <= 360),
    linked_page VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL,
    payload_digest CHAR(64) NOT NULL CHECK (payload_digest ~ '^[0-9a-fA-F]{64}$'),
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gis_map_point_map
        FOREIGN KEY (tenant_id, site_map_id) REFERENCES gis_site_map (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_map_point_idempotency
    ON gis_map_point (tenant_id, idempotency_key) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_gis_map_point_map
    ON gis_map_point (tenant_id, site_map_id) WHERE isdel = 0;

-- 只保存可丢弃的摘要投影，不作为业务事实来源。
CREATE TABLE IF NOT EXISTS gis_dashboard_cache (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    permission_fingerprint VARCHAR(128) NOT NULL,
    summary_type VARCHAR(32) NOT NULL,
    time_range_key VARCHAR(16) NOT NULL,
    filter_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_summary VARCHAR(512) NOT NULL,
    payload_json JSONB NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    stale_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_gis_dashboard_cache_key
        UNIQUE (tenant_id, permission_fingerprint, summary_type, time_range_key, filter_json)
);

CREATE INDEX IF NOT EXISTS ix_gis_dashboard_cache_lookup
    ON gis_dashboard_cache (tenant_id, permission_fingerprint, summary_type, generated_at DESC)
    WHERE isdel = 0;
