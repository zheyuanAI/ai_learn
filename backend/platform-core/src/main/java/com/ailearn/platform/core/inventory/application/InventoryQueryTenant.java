package com.ailearn.platform.core.inventory.application;

import java.util.UUID;

/**
 * 查询租户过滤信息。
 *
 * @param tenantId 可选租户字段；应用服务会与可信上下文比对，不能用于切换租户
 */
public record InventoryQueryTenant(UUID tenantId) {
}
