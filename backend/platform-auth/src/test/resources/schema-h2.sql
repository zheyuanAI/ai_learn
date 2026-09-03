-- ====================================================================
-- H2 内存数据库表结构 DDL (auth_* 规范表结构)
-- ====================================================================

DROP TABLE IF EXISTS auth_idempotency_record CASCADE;
DROP TABLE IF EXISTS auth_session CASCADE;
DROP TABLE IF EXISTS auth_role_menu CASCADE;
DROP TABLE IF EXISTS auth_role_permission CASCADE;
DROP TABLE IF EXISTS auth_user_role CASCADE;
DROP TABLE IF EXISTS auth_menu CASCADE;
DROP TABLE IF EXISTS auth_permission CASCADE;
DROP TABLE IF EXISTS auth_role CASCADE;
DROP TABLE IF EXISTS auth_user CASCADE;
DROP TABLE IF EXISTS auth_tenant CASCADE;

CREATE TABLE auth_tenant (
    id UUID PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_user (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_no VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    real_name VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_permission (
    id UUID PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    module VARCHAR(64) NOT NULL DEFAULT 'auth',
    description VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_menu (
    id UUID PRIMARY KEY,
    parent_id UUID,
    tenant_id UUID NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    route_path VARCHAR(256),
    component_path VARCHAR(256),
    icon VARCHAR(64),
    sort_order INT NOT NULL DEFAULT 0,
    permission_code VARCHAR(100),
    visible BOOLEAN NOT NULL DEFAULT TRUE CHECK (visible IN (TRUE, FALSE)),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

-- H2 测试表与 V5 保持同一租户级活动菜单编码约束语义。
CREATE UNIQUE INDEX uq_auth_menu_tenant_code_active
    ON auth_menu (tenant_id, menu_code, isdel);

CREATE TABLE auth_user_role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_role_permission (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_role_menu (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    menu_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    jti VARCHAR(64) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(256),
    login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE auth_idempotency_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128),
    response_body TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
