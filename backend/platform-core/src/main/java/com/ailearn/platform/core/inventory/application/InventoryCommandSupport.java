package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ForbiddenException;
import com.ailearn.platform.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 库存应用服务的公共边界校验，不承担任何持久化行为。
 */
final class InventoryCommandSupport {

    private InventoryCommandSupport() {
    }

    /**
     * 校验命令元数据并绑定可信租户、用户、JTI 和请求 ID。
     *
     * @param command 待执行库存命令
     * @return 经过可信上下文确认的操作身份
     */
    static TrustedCommandContext validate(InventoryCommand command) {
        if (command == null || command.metadata() == null) {
            throw new ValidationException("库存命令不能为空");
        }
        command.metadata().validate();
        if (command.primaryDimension() == null) {
            throw new ValidationException("库存维度不能为空");
        }
        InventoryInvariant.requirePositive("quantity", command.quantity());

        UUID trustedTenantId = TenantContextHolder.requireTenantId();
        UUID trustedUserId = UserContextHolder.requireUserId();
        String trustedSessionId = UserContextHolder.getSessionId();
        String trustedRequestId = RequestContextHolder.getRequestId();
        if (!trustedTenantId.equals(command.tenantId()) || !trustedUserId.equals(command.userId())) {
            throw new ForbiddenException("库存命令租户或操作人不匹配可信上下文");
        }
        if (trustedSessionId == null || trustedSessionId.isBlank()
                || !trustedSessionId.equals(command.metadata().sessionId())) {
            throw new ForbiddenException("库存命令会话不匹配可信上下文");
        }
        if (trustedRequestId == null || trustedRequestId.isBlank()
                || !trustedRequestId.equals(command.metadata().requestId())) {
            throw new ForbiddenException("库存命令请求 ID 不匹配可信上下文");
        }
        return new TrustedCommandContext(trustedTenantId, trustedUserId, trustedSessionId, trustedRequestId);
    }

    /**
     * 校验并统一库存数量 scale。
     *
     * @param quantity 原始数量
     * @return 六位小数的正数
     */
    static BigDecimal positiveQuantity(BigDecimal quantity) {
        return InventoryInvariant.requirePositive("quantity", quantity);
    }

    /**
     * 校验查询租户断言，确保查询对象无法覆盖可信租户。
     *
     * @param queryTenantId 查询对象中的可选租户 ID
     * @return 可信租户 ID
     */
    static UUID trustedQueryTenant(UUID queryTenantId) {
        UUID trustedTenantId = TenantContextHolder.requireTenantId();
        if (queryTenantId != null && !trustedTenantId.equals(queryTenantId)) {
            throw new ForbiddenException("查询租户与可信上下文不匹配");
        }
        return trustedTenantId;
    }

    /**
     * 规范化分页参数，避免负 offset 或超大单页查询。
     *
     * @param page 请求页码
     * @param size 请求页大小
     * @return 规范化页码和页大小，数组下标 0 为 page、下标 1 为 size
     */
    static int[] page(int page, int size) {
        if (page < 1 || size < 1 || size > 200) {
            throw new ValidationException("分页参数必须满足 page >= 1 且 1 <= size <= 200");
        }
        return new int[]{page, size};
    }

    /**
     * 已由共享认证上下文确认的命令身份。
     *
     * @param tenantId 可信租户
     * @param userId 可信用户
     * @param sessionId 可信会话 JTI
     * @param requestId 可信请求 ID
     */
    record TrustedCommandContext(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }
}
