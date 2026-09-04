package com.ailearn.platform.core.masterdata.domain.enumtype;

import com.ailearn.platform.shared.exception.ValidationException;

/**
 * 主数据生命周期状态。
 */
public enum MasterDataStatus {
    ACTIVE,
    INACTIVE;

    /**
     * 解析状态并兼容早期页面使用的启用/停用别名。
     *
     * @param value HTTP 状态值
     * @return 规范化的 ACTIVE 或 INACTIVE
     * @throws ValidationException 状态为空或不支持
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE.name();
        }
        return switch (value.trim().toUpperCase()) {
            case "ACTIVE", "ENABLE", "ENABLED", "AVAILABLE" -> ACTIVE.name();
            case "INACTIVE", "DISABLE", "DISABLED", "LOCKED" -> INACTIVE.name();
            default -> throw new ValidationException("主数据状态不合法，仅支持 ACTIVE 或 INACTIVE");
        };
    }
}
