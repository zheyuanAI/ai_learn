package com.ailearn.platform.core.masterdata.domain.port;

import com.ailearn.platform.core.masterdata.domain.model.MasterDataPage;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.shared.domain.BaseEntity;
import java.util.Optional;
import java.util.UUID;

/**
 * 主数据持久化端口。
 * <p>
 * 应用层只依赖此端口，不依赖 MyBatis-Plus；所有实现都必须在查询条件中带上 tenantId 和未删除条件。
 * </p>
 *
 * @param <E> 主数据实体类型
 */
public interface MasterDataRepository<E extends BaseEntity> {

    /**
     * 分页查询当前租户的未删除主数据。
     *
     * @param tenantId 当前可信租户
     * @param query    已规范化的分页和筛选参数
     * @return 当前租户范围内的分页结果
     */
    MasterDataPage<E> findPage(UUID tenantId, MasterDataPageQuery query);

    /**
     * 按租户和主键查询有效实体，跨租户资源必须返回空。
     *
     * @param tenantId 当前可信租户
     * @param id       主数据主键
     * @return 当前租户可见实体
     */
    Optional<E> findById(UUID tenantId, UUID id);

    /**
     * 检查租户内编码是否已被其他未删除实体使用。
     *
     * @param tenantId 当前可信租户
     * @param code     已规范化编码
     * @param excludeId 修改时排除的实体 ID，创建时为 null
     * @return 编码已存在返回 true
     */
    boolean existsByCode(UUID tenantId, String code, UUID excludeId);

    /**
     * 插入主数据实体。
     *
     * @param entity 已填充租户、审计和业务字段的实体
     */
    void insert(E entity);

    /**
     * 更新主数据实体。
     *
     * @param entity 已按当前租户加载并完成校验的实体
     */
    void update(E entity);

    /**
     * 判断实体是否已被业务事实引用。
     * <p>
     * 应用层禁止物理删除；实现可用此端口阻止会破坏来源关系的删除请求。
     * </p>
     *
     * @param tenantId 当前可信租户
     * @param id       主数据主键
     * @return 已被引用返回 true
     */
    default boolean hasReferences(UUID tenantId, UUID id) {
        return false;
    }
}
