package com.ailearn.platform.core.masterdata.domain.model;

import java.util.List;

/**
 * Repository 层统一分页模型，避免应用层依赖 MyBatis-Plus 的 IPage 类型。
 *
 * @param <E> 领域实体类型
 */
public record MasterDataPage<E>(List<E> records, long total, long page, long size) {

    /**
     * 构造安全的分页模型，防止空记录集合向应用层传播 null。
     *
     * @param records 当前页实体
     * @param total   总数
     * @param page    页码
     * @param size    页大小
     */
    public MasterDataPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
