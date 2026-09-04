package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.model.MasterDataPage;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.shared.domain.BaseEntity;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 六类主数据的 MyBatis-Plus Repository 公共实现。
 * <p>
 * 用途：集中保证租户、逻辑删除、关键词分页和安全排序规则，具体 Repository 只声明表字段差异。
 * 入参：可信租户、规范化查询及主数据实体；出参：领域分页模型或更新结果。
 * 流程：构造带 tenant_id/isdel 条件的 QueryWrapper，调用 BaseMapper 完成读写；不存在的跨租户资源自然返回空。
 * </p>
 *
 * @param <E> 主数据实体类型
 */
public abstract class AbstractMyBatisMasterDataRepository<E extends BaseEntity>
        implements MasterDataRepository<E> {

    private final BaseMapper<E> mapper;

    protected AbstractMyBatisMasterDataRepository(BaseMapper<E> mapper) {
        this.mapper = mapper;
    }

    /**
     * 返回数据库中的编码列名，用于租户内唯一检查和关键词查询。
     *
     * @return 安全常量列名
     */
    protected abstract String codeColumn();

    /**
     * 返回数据库中的名称列名，用于关键词查询。
     *
     * @return 安全常量列名
     */
    protected abstract String nameColumn();

    /**
     * 对具体资源追加领域筛选条件。
     *
     * @param wrapper 已带租户和未删除条件的查询包装器
     * @param query   规范化查询参数
     */
    protected void applySpecificFilters(QueryWrapper<E> wrapper, MasterDataPageQuery query) {
        // 默认资源没有额外筛选条件。
    }

    /**
     * 分页查询当前租户未删除数据。
     *
     * @param tenantId 当前可信租户
     * @param query    分页与筛选参数
     * @return 租户内分页结果
     */
    @Override
    public MasterDataPage<E> findPage(UUID tenantId, MasterDataPageQuery query) {
        MasterDataPageQuery normalized = query == null ? new MasterDataPageQuery().normalized() : query.normalized();
        QueryWrapper<E> wrapper = tenantScope(tenantId);
        if (normalized.getKeyword() != null) {
            String keyword = normalized.getKeyword();
            wrapper.and(item -> item.like(codeColumn(), keyword).or().like(nameColumn(), keyword));
        }
        if (normalized.getStatus() != null) {
            wrapper.eq("status", normalized.getStatus());
        }
        applySpecificFilters(wrapper, normalized);
        applySafeOrder(wrapper, normalized);
        Page<E> page = new Page<>(normalized.getPage(), normalized.getSize());
        var result = mapper.selectPage(page, wrapper);
        return new MasterDataPage<>(result.getRecords(), result.getTotal(), normalized.getPage(), normalized.getSize());
    }

    /**
     * 按当前租户和主键查询未删除实体。
     *
     * @param tenantId 当前可信租户
     * @param id       主数据主键
     * @return 当前租户可见实体
     */
    @Override
    public Optional<E> findById(UUID tenantId, UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        E entity = mapper.selectOne(tenantScope(tenantId).eq("id", id));
        return Optional.ofNullable(entity);
    }

    /**
     * 检查当前租户内的未删除编码是否被其他实体占用。
     *
     * @param tenantId 当前可信租户
     * @param code     已规范化编码
     * @param excludeId 修改时排除的实体 ID
     * @return 已存在返回 true
     */
    @Override
    public boolean existsByCode(UUID tenantId, String code, UUID excludeId) {
        QueryWrapper<E> wrapper = tenantScope(tenantId).eq(codeColumn(), code);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    /**
     * 插入已经完成上下文审计填充的实体。
     *
     * @param entity 待插入实体
     */
    @Override
    public void insert(E entity) {
        mapper.insert(entity);
    }

    /**
     * 更新当前租户已加载的实体。
     *
     * @param entity 待更新实体
     */
    @Override
    public void update(E entity) {
        mapper.updateById(entity);
    }

    /**
     * 仅使用逻辑删除标记，不执行 DELETE 语句。
     *
     * @param tenantId 当前可信租户
     * @param id       待删除主键
     * @param userId   操作用户
     * @param now      操作时间
     */
    protected void logicalDelete(UUID tenantId, UUID id, UUID userId, OffsetDateTime now) {
        UpdateWrapper<E> wrapper = new UpdateWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("id", id)
                .eq("isdel", 0)
                .set("isdel", 1)
                .set("updated_by", userId)
                .set("updated_at", now);
        mapper.update(null, wrapper);
    }

    /**
     * 构造所有查询必须复用的租户与软删除范围。
     *
     * @param tenantId 当前可信租户
     * @return 带隔离条件的查询包装器
     */
    protected QueryWrapper<E> tenantScope(UUID tenantId) {
        return new QueryWrapper<E>()
                .eq("tenant_id", tenantId)
                .eq("isdel", 0);
    }

    private void applySafeOrder(QueryWrapper<E> wrapper, MasterDataPageQuery query) {
        String field = query.getSortField();
        String column = switch (field == null ? "" : field) {
            case "code", "sku" -> codeColumn();
            case "name" -> nameColumn();
            case "status" -> "status";
            case "createdAt" -> "created_at";
            default -> codeColumn();
        };
        boolean descending = "desc".equalsIgnoreCase(query.getSortOrder());
        wrapper.orderBy(true, !descending, column);
    }
}
