-- ==============================================================================
-- Flyway Migration Script: V1__init_auth_schema.sql
-- 模块说明: Platform-Auth 认证与权限中心基础数据库 Schema 初始化
-- 独立历史表: auth_flyway_schema_history
-- 适用数据库: PostgreSQL 12.1及以上兼容
-- 规范要求:
--   1. 所有表使用 'auth_' 前缀；
--   2. 主键统一使用 UUID 并默认 gen_random_uuid()；
--   3. 时间字段统一使用 timestamptz 并默认 CURRENT_TIMESTAMP；
--   4. 逻辑删除统一使用 isdel (0: 正常, 1: 已删除)，配置 CHECK (isdel IN (0, 1))；
--   5. 唯一索引均采用部分唯一索引约束 (WHERE isdel = 0)。
-- ==============================================================================
-- 启用 pgcrypto 扩展（确保 gen_random_uuid() 函数可用）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------------------------
-- 1. 租户表: auth_tenant
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_tenant (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel       SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_tenant IS '租户信息表';
COMMENT ON COLUMN auth_tenant.id IS '租户主键ID (UUID)';
COMMENT ON COLUMN auth_tenant.tenant_code IS '租户唯一编码 (业务标识)';
COMMENT ON COLUMN auth_tenant.tenant_name IS '租户名称 (如华东制造一号基地)';
COMMENT ON COLUMN auth_tenant.status IS '租户状态 (ACTIVE: 启用, DISABLED: 停用)';
COMMENT ON COLUMN auth_tenant.created_by IS '创建人账号';
COMMENT ON COLUMN auth_tenant.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN auth_tenant.updated_by IS '最后更新人账号';
COMMENT ON COLUMN auth_tenant.updated_at IS '最后更新时间 (带时区)';
COMMENT ON COLUMN auth_tenant.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 租户编码未删除部分唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_tenant_code_active
    ON auth_tenant (tenant_code)
    WHERE isdel = 0;


-- ------------------------------------------------------------------------------
-- 2. 用户表: auth_user
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES auth_tenant(id) ON DELETE RESTRICT,
    user_no       VARCHAR(64) NOT NULL,
    username      VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name     VARCHAR(64) NOT NULL,
    email         VARCHAR(128),
    phone         VARCHAR(32),
    status        VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel         SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_user IS '用户账号表';
COMMENT ON COLUMN auth_user.id IS '用户主键ID (UUID)';
COMMENT ON COLUMN auth_user.tenant_id IS '所属租户ID (外键关联 auth_tenant.id)';
COMMENT ON COLUMN auth_user.user_no IS '用户工号/编号 (租户内唯一)';
COMMENT ON COLUMN auth_user.username IS '登录登录名 (租户内唯一)';
COMMENT ON COLUMN auth_user.password_hash IS 'BCrypt哈希密码';
COMMENT ON COLUMN auth_user.real_name IS '用户真实姓名';
COMMENT ON COLUMN auth_user.email IS '电子邮箱';
COMMENT ON COLUMN auth_user.phone IS '联系手机号';
COMMENT ON COLUMN auth_user.status IS '账号状态 (ACTIVE: 正常, LOCKED: 锁定, DISABLED: 禁用)';
COMMENT ON COLUMN auth_user.created_by IS '创建人账号';
COMMENT ON COLUMN auth_user.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN auth_user.updated_by IS '最后更新人账号';
COMMENT ON COLUMN auth_user.updated_at IS '最后更新时间 (带时区)';
COMMENT ON COLUMN auth_user.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 租户内登录名唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_user_tenant_username_active
    ON auth_user (tenant_id, username)
    WHERE isdel = 0;

-- 租户内工号唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_user_tenant_userno_active
    ON auth_user (tenant_id, user_no)
    WHERE isdel = 0;

-- 租户外键快速检索索引
CREATE INDEX IF NOT EXISTS idx_auth_user_tenant_id
    ON auth_user (tenant_id);


-- ------------------------------------------------------------------------------
-- 3. 角色表: auth_role
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_role (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES auth_tenant(id) ON DELETE RESTRICT,
    role_code   VARCHAR(64) NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel       SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_role IS '角色表';
COMMENT ON COLUMN auth_role.id IS '角色主键ID (UUID)';
COMMENT ON COLUMN auth_role.tenant_id IS '所属租户ID (外键关联 auth_tenant.id)';
COMMENT ON COLUMN auth_role.role_code IS '角色编码 (如 tenant.admin, sales.rep)';
COMMENT ON COLUMN auth_role.role_name IS '角色名称 (如 租户管理员, 销售人员)';
COMMENT ON COLUMN auth_role.description IS '角色职责描述';
COMMENT ON COLUMN auth_role.status IS '角色状态 (ACTIVE: 启用, DISABLED: 停用)';
COMMENT ON COLUMN auth_role.created_by IS '创建人账号';
COMMENT ON COLUMN auth_role.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN auth_role.updated_by IS '最后更新人账号';
COMMENT ON COLUMN auth_role.updated_at IS '最后更新时间 (带时区)';
COMMENT ON COLUMN auth_role.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 租户内角色编码唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_role_tenant_code_active
    ON auth_role (tenant_id, role_code)
    WHERE isdel = 0;

-- 租户检索索引
CREATE INDEX IF NOT EXISTS idx_auth_role_tenant_id
    ON auth_role (tenant_id);


-- ------------------------------------------------------------------------------
-- 4. 功能权限点表: auth_permission
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_permission (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    module          VARCHAR(64) NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel           SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_permission IS '系统功能权限点字典表';
COMMENT ON COLUMN auth_permission.id IS '权限点主键ID (UUID)';
COMMENT ON COLUMN auth_permission.permission_code IS '权限标识串 (如 pur:order:create, sales:pick:confirm)';
COMMENT ON COLUMN auth_permission.permission_name IS '权限点名称';
COMMENT ON COLUMN auth_permission.module IS '所属业务模块 (如 purchasing, sales, inventory, mes, iot, auth)';
COMMENT ON COLUMN auth_permission.description IS '权限详细说明';
COMMENT ON COLUMN auth_permission.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN auth_permission.updated_at IS '更新时间 (带时区)';
COMMENT ON COLUMN auth_permission.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 权限编码唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_permission_code_active
    ON auth_permission (permission_code)
    WHERE isdel = 0;

-- 模块检索索引
CREATE INDEX IF NOT EXISTS idx_auth_permission_module
    ON auth_permission (module);


-- ------------------------------------------------------------------------------
-- 5. 动态菜单表: auth_menu
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_menu (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id       UUID REFERENCES auth_menu(id) ON DELETE RESTRICT,
    menu_code       VARCHAR(64) NOT NULL,
    menu_name       VARCHAR(128) NOT NULL,
    route_path      VARCHAR(255),
    component_path  VARCHAR(255),
    icon            VARCHAR(64),
    sort_order      INT NOT NULL DEFAULT 0,
    permission_code VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel           SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_menu IS '动态菜单树结构表';
COMMENT ON COLUMN auth_menu.id IS '菜单主键ID (UUID)';
COMMENT ON COLUMN auth_menu.parent_id IS '父级菜单ID (顶级菜单为NULL)';
COMMENT ON COLUMN auth_menu.menu_code IS '菜单唯一编码 (如 dashboard, purchase_order)';
COMMENT ON COLUMN auth_menu.menu_name IS '菜单显示名称 (如 控制台主页, 采购订单)';
COMMENT ON COLUMN auth_menu.route_path IS '前端路由路径 (如 /dashboard, /purchase/orders)';
COMMENT ON COLUMN auth_menu.component_path IS '前端组件路径 (如 views/Dashboard.vue)';
COMMENT ON COLUMN auth_menu.icon IS '菜单图标标识 (如 DashboardOutlined)';
COMMENT ON COLUMN auth_menu.sort_order IS '同级排序权重 (升序)';
COMMENT ON COLUMN auth_menu.permission_code IS '关联权限编码 (可选)';
COMMENT ON COLUMN auth_menu.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN auth_menu.updated_at IS '更新时间 (带时区)';
COMMENT ON COLUMN auth_menu.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 菜单编码唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_menu_code_active
    ON auth_menu (menu_code)
    WHERE isdel = 0;

-- 父级菜单检索索引
CREATE INDEX IF NOT EXISTS idx_auth_menu_parent_id
    ON auth_menu (parent_id);


-- ------------------------------------------------------------------------------
-- 6. 用户角色关系表: auth_user_role
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_user_role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES auth_tenant(id) ON DELETE RESTRICT,
    user_id    UUID NOT NULL REFERENCES auth_user(id) ON DELETE RESTRICT,
    role_id    UUID NOT NULL REFERENCES auth_role(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel      SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_user_role IS '用户与角色多对多关联表';
COMMENT ON COLUMN auth_user_role.id IS '关联主键ID (UUID)';
COMMENT ON COLUMN auth_user_role.tenant_id IS '租户ID';
COMMENT ON COLUMN auth_user_role.user_id IS '用户ID (关联 auth_user.id)';
COMMENT ON COLUMN auth_user_role.role_id IS '角色ID (关联 auth_role.id)';
COMMENT ON COLUMN auth_user_role.created_at IS '关联创建时间 (带时区)';
COMMENT ON COLUMN auth_user_role.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 用户角色组合唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_user_role_active
    ON auth_user_role (tenant_id, user_id, role_id)
    WHERE isdel = 0;

-- 用户和角色检索索引
CREATE INDEX IF NOT EXISTS idx_auth_user_role_user_id ON auth_user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_role_role_id ON auth_user_role (role_id);


-- ------------------------------------------------------------------------------
-- 7. 角色权限关系表: auth_role_permission
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_role_permission (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id       UUID NOT NULL REFERENCES auth_role(id) ON DELETE RESTRICT,
    permission_id UUID NOT NULL REFERENCES auth_permission(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel         SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_role_permission IS '角色与权限多对多关联表';
COMMENT ON COLUMN auth_role_permission.id IS '关联主键ID (UUID)';
COMMENT ON COLUMN auth_role_permission.role_id IS '角色ID (关联 auth_role.id)';
COMMENT ON COLUMN auth_role_permission.permission_id IS '权限ID (关联 auth_permission.id)';
COMMENT ON COLUMN auth_role_permission.created_at IS '关联创建时间 (带时区)';
COMMENT ON COLUMN auth_role_permission.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 角色权限组合唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_role_perm_active
    ON auth_role_permission (role_id, permission_id)
    WHERE isdel = 0;

-- 角色和权限检索索引
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_role_id ON auth_role_permission (role_id);
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_perm_id ON auth_role_permission (permission_id);


-- ------------------------------------------------------------------------------
-- 8. 角色菜单关系表: auth_role_menu
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_role_menu (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id    UUID NOT NULL REFERENCES auth_role(id) ON DELETE RESTRICT,
    menu_id    UUID NOT NULL REFERENCES auth_menu(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel      SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

COMMENT ON TABLE auth_role_menu IS '角色与菜单多对多授权关联表';
COMMENT ON COLUMN auth_role_menu.id IS '关联主键ID (UUID)';
COMMENT ON COLUMN auth_role_menu.role_id IS '角色ID (关联 auth_role.id)';
COMMENT ON COLUMN auth_role_menu.menu_id IS '菜单ID (关联 auth_menu.id)';
COMMENT ON COLUMN auth_role_menu.created_at IS '授权时间 (带时区)';
COMMENT ON COLUMN auth_role_menu.isdel IS '软删除标记 (0: 未删除, 1: 已删除)';

-- 角色菜单组合唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_role_menu_active
    ON auth_role_menu (role_id, menu_id)
    WHERE isdel = 0;

-- 角色和菜单检索索引
CREATE INDEX IF NOT EXISTS idx_auth_role_menu_role_id ON auth_role_menu (role_id);
CREATE INDEX IF NOT EXISTS idx_auth_role_menu_menu_id ON auth_role_menu (menu_id);


-- ------------------------------------------------------------------------------
-- 9. 用户登录会话表: auth_session
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_session (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES auth_tenant(id) ON DELETE RESTRICT,
    user_id        UUID NOT NULL REFERENCES auth_user(id) ON DELETE RESTRICT,
    jti            VARCHAR(64) NOT NULL,
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(512),
    login_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(255),
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

COMMENT ON TABLE auth_session IS '用户在线会话与 Token 审计表';
COMMENT ON COLUMN auth_session.id IS '会话主键ID (UUID)';
COMMENT ON COLUMN auth_session.tenant_id IS '所属租户ID';
COMMENT ON COLUMN auth_session.user_id IS '用户ID (关联 auth_user.id)';
COMMENT ON COLUMN auth_session.jti IS 'JWT 唯一标识 (Token JTI)';
COMMENT ON COLUMN auth_session.ip_address IS '登录客户端IP地址';
COMMENT ON COLUMN auth_session.user_agent IS '客户端 User-Agent 头信息';
COMMENT ON COLUMN auth_session.login_at IS '登录成功时间 (带时区)';
COMMENT ON COLUMN auth_session.expires_at IS '会话过期时间 (带时区)';
COMMENT ON COLUMN auth_session.revoked_at IS '会话注销/吊销时间';
COMMENT ON COLUMN auth_session.revoked_reason IS '会话注销原因 (如 LOGOUT, REPLACED_BY_NEW_LOGIN, ADMIN_KICK)';
COMMENT ON COLUMN auth_session.status IS '会话状态 (ACTIVE: 有效, REVOKED: 已注销, EXPIRED: 已过期)';

-- 会话 JTI 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_session_jti
    ON auth_session (jti);

-- 用户有效会话检索索引
CREATE INDEX IF NOT EXISTS idx_auth_session_user_status
    ON auth_session (user_id, status);

-- 租户检索索引
CREATE INDEX IF NOT EXISTS idx_auth_session_tenant_id
    ON auth_session (tenant_id);


-- ------------------------------------------------------------------------------
-- 10. Auth 幂等记录表: auth_idempotency_record
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_idempotency_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES auth_tenant(id) ON DELETE RESTRICT,
    endpoint        VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    VARCHAR(128),
    response_body   TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth_idempotency_record IS 'Auth 模块写操作幂等记录表';
COMMENT ON COLUMN auth_idempotency_record.id IS '幂等记录主键ID (UUID)';
COMMENT ON COLUMN auth_idempotency_record.tenant_id IS '所属租户ID';
COMMENT ON COLUMN auth_idempotency_record.endpoint IS '请求接口路径 (如 /api/auth/users)';
COMMENT ON COLUMN auth_idempotency_record.idempotency_key IS '客户端请求头 Idempotency-Key';
COMMENT ON COLUMN auth_idempotency_record.request_hash IS '请求参数 SHA-256 哈希指纹';
COMMENT ON COLUMN auth_idempotency_record.response_body IS '执行成功后的响应缓存结果';
COMMENT ON COLUMN auth_idempotency_record.status IS '处理状态 (PROCESSING: 处理中, SUCCESS: 成功, FAILED: 失败)';
COMMENT ON COLUMN auth_idempotency_record.created_at IS '记录创建时间 (带时区)';

-- 租户+接口+幂等键唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_idempotency_record
    ON auth_idempotency_record (tenant_id, endpoint, idempotency_key);

-- 租户检索索引
CREATE INDEX IF NOT EXISTS idx_auth_idempotency_tenant_id
    ON auth_idempotency_record (tenant_id);
