package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.MasterDataView;
import com.ailearn.platform.shared.domain.BaseEntity;
import java.math.BigDecimal;

/**
 * 主数据视图映射辅助方法。
 */
final class MasterDataViewSupport {

    private MasterDataViewSupport() {
    }

    /**
     * 复制公共审计字段和 code/name 到响应视图。
     *
     * @param entity 主数据实体
     * @param view   目标视图
     * @param code   对外编码
     * @param name   对外名称
     * @return 已填充公共字段的视图
     */
    static <V extends MasterDataView> V copyBase(BaseEntity entity, V view, String code, String name) {
        view.setId(entity.getId());
        view.setCode(code);
        view.setName(name);
        view.setStatus(entity.getStatus());
        view.setCreatedBy(entity.getCreatedBy());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedBy(entity.getUpdatedBy());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }

    /**
     * 把内部 BigDecimal 按十进制非科学计数法转为 HTTP 字符串。
     *
     * @param value 内部数值
     * @return 数值字符串，空值仍为空
     */
    static String decimalToString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /**
     * 规范化可选字符串，避免把空白值写入主数据。
     *
     * @param value 原始值
     * @return 空白转 null 后的值
     */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
