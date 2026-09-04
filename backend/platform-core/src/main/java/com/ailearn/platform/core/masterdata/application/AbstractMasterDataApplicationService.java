package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.masterdata.domain.enumtype.MasterDataStatus;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.MasterDataSaveRequest;
import com.ailearn.platform.core.masterdata.dto.MasterDataView;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.domain.BaseEntity;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 六类主数据应用服务的公共模板。
 * <p>
 * 用途：统一实现租户隔离、审计字段、编码唯一、分页、状态变更和逻辑删除，具体服务只负责领域字段映射。
 * 入参：具体 SaveRequest、主键和分页参数；出参：具体 View 或分页结果。
 * 流程：写操作先读取可信 tenant/user，再加载当前租户实体，完成领域校验后通过 Repository 在事务中持久化。
 * </p>
 *
 * @param <E> 主数据实体
 * @param <R> 主数据写请求
 * @param <V> 主数据响应视图
 */
public abstract class AbstractMasterDataApplicationService<
        E extends BaseEntity, R extends MasterDataSaveRequest, V extends MasterDataView> {

    protected final MasterDataRepository<E> repository;
    private final CoreIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;

    protected AbstractMasterDataApplicationService(MasterDataRepository<E> repository) {
        this(repository, new InMemoryIdempotencyStorage(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建使用共享幂等存储的主数据服务；生产构造器由 Spring 注入 PostgreSQL 实现，测试可继续使用旧构造器。
     */
    protected AbstractMasterDataApplicationService(MasterDataRepository<E> repository,
                                                    IdempotencyStorage storage,
                                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.idempotencyExecutor = new CoreIdempotencyExecutor(storage, objectMapper);
    }

    /**
     * 分页查询当前租户的未删除资源。
     *
     * @param query 分页和筛选参数，可为空
     * @return 当前租户可见的分页视图
     */
    public MasterDataPageResult<V> page(MasterDataPageQuery query) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        MasterDataPageQuery normalized = query == null
                ? new MasterDataPageQuery().normalized()
                : query.normalized();
        var result = repository.findPage(tenantId, normalized);
        List<V> records = result.records().stream().map(this::toViewWithActions).toList();
        return new MasterDataPageResult<>(records, result.total(), result.page(), result.size());
    }

    /**
     * 查询当前租户内单条未删除资源，跨租户主键按不存在处理。
     *
     * @param id 主数据主键
     * @return 当前租户资源详情
     * @throws NotFoundException 主键为空或不属于当前租户
     */
    public V detail(UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return toViewWithActions(requireEntity(tenantId, id));
    }

    /**
     * 创建主数据并写入可信租户、用户和审计时间。
     *
     * @param request 创建请求
     * @return 创建后的资源详情
     */
    @Transactional(rollbackFor = Exception.class)
    public V create(R request) {
        return createInternal(request);
    }

    /**
     * 通过操作域和服务端完整请求摘要执行主数据创建。
     */
    @Transactional(rollbackFor = Exception.class)
    public V create(R request, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        validateKey(idempotencyKey);
        return idempotencyExecutor.execute(operation("create"), tenantId, idempotencyKey,
                digest("create", request), viewType(), () -> createInternal(request));
    }

    private V createInternal(R request) {
        Actor actor = requireActor();
        requireRequest(request);
        String code = validateCode(request, true);
        validatePayload(request, true);
        if (repository.existsByCode(actor.tenantId(), code, null)) {
            throw new ConflictException("当前租户内编码已存在: " + code);
        }

        E entity = newEntity(request);
        initializeEntity(entity, actor, MasterDataStatus.normalize(request.getStatus()));
        repository.insert(entity);
        return toViewWithActions(entity);
    }

    /**
     * 修改当前租户资源的非身份字段；若请求带 status，则复用状态前置校验。
     *
     * @param id      资源主键
     * @param request 修改请求，未提供的可选字段保持原值
     * @return 修改后的资源详情
     */
    @Transactional(rollbackFor = Exception.class)
    public V update(UUID id, R request) {
        return updateInternal(id, request);
    }

    /**
     * 通过操作域和包含路径 ID 的完整请求摘要执行主数据修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public V update(UUID id, R request, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        validateKey(idempotencyKey);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", id);
        payload.put("request", request);
        return idempotencyExecutor.execute(operation("update"), tenantId, idempotencyKey,
                digest("update", payload), viewType(), () -> updateInternal(id, request));
    }

    private V updateInternal(UUID id, R request) {
        Actor actor = requireActor();
        requireRequest(request);
        E entity = requireEntity(actor.tenantId(), id);
        String requestedCode = codeOf(request);
        if (requestedCode != null && !requestedCode.isBlank()) {
            String code = validateCode(request, false);
            if (repository.existsByCode(actor.tenantId(), code, entity.getId())) {
                throw new ConflictException("当前租户内编码已存在: " + code);
            }
        }
        validatePayload(request, false);
        applyFields(entity, request);
        applyStatusIfRequested(entity, request, actor);
        touch(entity, actor.userId());
        repository.update(entity);
        return toViewWithActions(entity);
    }

    /**
     * 启用或停用当前租户资源。
     *
     * @param id     资源主键
     * @param status 目标状态，只接受 ACTIVE/INACTIVE 及已声明兼容别名
     * @return 状态变更后的资源详情
     */
    @Transactional(rollbackFor = Exception.class)
    public V changeStatus(UUID id, String status) {
        return changeStatusInternal(id, status);
    }

    /**
     * 通过操作域和包含路径 ID/状态的完整请求摘要执行状态变更。
     */
    @Transactional(rollbackFor = Exception.class)
    public V changeStatus(UUID id, String status, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        validateKey(idempotencyKey);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", id);
        payload.put("status", status);
        return idempotencyExecutor.execute(operation("status"), tenantId, idempotencyKey,
                digest("status", payload), viewType(), () -> changeStatusInternal(id, status));
    }

    private V changeStatusInternal(UUID id, String status) {
        Actor actor = requireActor();
        if (status == null || status.isBlank()) {
            throw new ValidationException("目标状态不能为空");
        }
        E entity = requireEntity(actor.tenantId(), id);
        String normalizedStatus = MasterDataStatus.normalize(status);
        beforeStatusChange(entity, normalizedStatus, actor);
        entity.setStatus(normalizedStatus);
        touch(entity, actor.userId());
        repository.update(entity);
        return toViewWithActions(entity);
    }

    /**
     * 对当前租户资源执行逻辑删除，不执行物理 DELETE。
     *
     * @param id 资源主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        deleteInternal(id);
    }

    /**
     * 通过操作域和资源 ID 的完整请求摘要执行逻辑删除；响应为空值仍缓存为成功结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        validateKey(idempotencyKey);
        idempotencyExecutor.execute(operation("delete"), tenantId, idempotencyKey,
                digest("delete", id), Void.class, () -> {
                    deleteInternal(id);
                    return null;
                });
    }

    private void deleteInternal(UUID id) {
        Actor actor = requireActor();
        E entity = requireEntity(actor.tenantId(), id);
        if (repository.hasReferences(actor.tenantId(), entity.getId())) {
            throw new ConflictException("主数据已被业务事实引用，只允许保留历史，不允许删除");
        }
        entity.setIsdel(1);
        touch(entity, actor.userId());
        repository.update(entity);
    }

    /**
     * 读取当前可信租户和用户，禁止客户端请求体覆盖审计归属。
     *
     * @return 写操作使用的可信操作人
     */
    protected Actor requireActor() {
        return new Actor(TenantContextHolder.requireTenantId(), UserContextHolder.requireUserId());
    }

    /**
     * 按租户范围加载资源；跨租户资源统一转换为 404。
     *
     * @param tenantId 当前可信租户
     * @param id       资源主键
     * @return 当前租户实体
     */
    protected E requireEntity(UUID tenantId, UUID id) {
        if (id == null) {
            throw new NotFoundException(resourceName() + "不存在");
        }
        Optional<E> entity = repository.findById(tenantId, id);
        return entity.orElseThrow(() -> new NotFoundException(resourceName() + "不存在"));
    }

    /**
     * 设置新实体的主键、租户、状态和审计字段。
     *
     * @param entity 实体
     * @param actor 可信操作人
     * @param status 规范化状态
     */
    protected void initializeEntity(E entity, Actor actor, String status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setId(UUID.randomUUID());
        entity.setTenantId(actor.tenantId());
        entity.setStatus(status);
        entity.setCreatedBy(actor.userId());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(actor.userId());
        entity.setUpdatedAt(now);
        entity.setIsdel(0);
    }

    /**
     * 更新审计时间和操作人。
     *
     * @param entity 待更新实体
     * @param userId 可信操作用户
     */
    protected void touch(E entity, UUID userId) {
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 规范化并检查请求中的编码。
     *
     * @param request 主数据请求
     * @param creating 是否为创建操作
     * @return 规范化编码；更新请求未提交编码时返回 null
     */
    protected String validateCode(R request, boolean creating) {
        String raw = codeOf(request);
        if (!creating && (raw == null || raw.isBlank())) {
            return null;
        }
        return com.ailearn.platform.core.masterdata.domain.service.MasterDataValidator
                .requireCode(codeField(), raw);
    }

    /**
     * 请求状态存在时更新实体状态并执行领域前置检查。
     *
     * @param entity 当前实体
     * @param request 更新请求
     * @param actor 可信操作人
     */
    protected void applyStatusIfRequested(E entity, R request, Actor actor) {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            return;
        }
        String status = MasterDataStatus.normalize(request.getStatus());
        if (!status.equalsIgnoreCase(entity.getStatus())) {
            beforeStatusChange(entity, status, actor);
            entity.setStatus(status);
        }
    }

    /**
     * 构造包含状态动作能力的响应视图。
     *
     * @param entity 主数据实体
     * @return 带 allowedActions 的响应视图
     */
    protected V toViewWithActions(E entity) {
        V view = toView(entity);
        boolean active = MasterDataStatus.ACTIVE.name().equalsIgnoreCase(entity.getStatus());
        view.setAllowedActions(List.of(
                new AllowedActionVo("update", true, null),
                new AllowedActionVo("enable", !active, active ? "当前已启用" : null),
                new AllowedActionVo("disable", active, active ? null : "当前已停用"),
                new AllowedActionVo("delete", true, "已被引用的主数据会被拒绝删除")));
        return view;
    }

    /**
     * 校验请求对象不为空。
     *
     * @param request 请求对象
     */
    protected void requireRequest(R request) {
        if (request == null) {
            throw new ValidationException("主数据请求不能为空");
        }
    }

    /**
     * 资源名称用于统一 404 文案。
     *
     * @return 中文资源名称
     */
    protected String resourceName() {
        return "主数据";
    }

    /**
     * 创建实体并映射请求字段。
     *
     * @param request 创建请求
     * @return 未填充通用审计字段的实体
     */
    protected abstract E newEntity(R request);

    /**
     * 修改实体领域字段；请求中未提交的字段应保持原值。
     *
     * @param entity 当前实体
     * @param request 修改请求
     */
    protected abstract void applyFields(E entity, R request);

    /**
     * 校验具体资源字段。
     *
     * @param request 请求对象
     * @param creating 是否为创建操作
     */
    protected abstract void validatePayload(R request, boolean creating);

    /**
     * 获取请求中的租户内唯一编码。
     *
     * @param request 请求对象
     * @return 编码原值
     */
    protected abstract String codeOf(R request);

    /**
     * 获取数据库编码列名对应的请求字段名。
     *
     * @return 校验错误字段名
     */
    protected abstract String codeField();

    /**
     * 将领域实体转换为 HTTP 视图。
     *
     * @param entity 领域实体
     * @return 响应视图
     */
    protected abstract V toView(E entity);

    /**
     * 获取资源名，用于形成主数据写操作域，避免不同资源共用同一幂等键。
     */
    protected abstract String resourceKey();

    /**
     * 获取具体响应类型，供成功结果重放反序列化。
     */
    protected abstract Class<V> viewType();

    private String operation(String action) {
        return "masterdata:" + resourceKey() + ":" + action;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    /**
     * 用服务端 JSON 序列化完整路径/请求载荷后计算摘要，不信任客户端自带摘要字段。
     */
    private String digest(String action, Object payload) {
        try {
            java.util.Map<String, Object> digestPayload = new java.util.LinkedHashMap<>();
            digestPayload.put("resource", resourceKey());
            digestPayload.put("action", action);
            digestPayload.put("payload", payload);
            String json = objectMapper.writeValueAsString(digestPayload);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new com.ailearn.platform.shared.exception.ServiceUnavailableException(
                    "主数据幂等载荷摘要生成失败", exception);
        }
    }

    /**
     * 状态变化前的领域钩子，例如库位停用前库存和预留必须为零。
     *
     * @param entity 当前实体
     * @param targetStatus 目标状态
     * @param actor 可信操作人
     */
    protected void beforeStatusChange(E entity, String targetStatus, Actor actor) {
        // 默认主数据无额外状态前置条件。
    }

    /**
     * 六类服务共用的可信操作人值对象。
     *
     * @param tenantId 可信租户 ID
     * @param userId   可信用户 ID
     */
    protected record Actor(UUID tenantId, UUID userId) {
    }
}
