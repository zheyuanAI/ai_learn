package com.ailearn.platform.core.operationaudit.controller;

import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.context.TenantContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Core 业务对象操作时间线的普通只读 REST API，页面与 AI 共用同一查询能力。 */
@RestController
@RequestMapping("/api/operation-audits")
public class OperationAuditController {
    private final OperationAuditApplicationService applicationService;

    public OperationAuditController(OperationAuditApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 用途：查询指定销售订单的账号操作时间线。
     * 入参：实体类型、实体 UUID、可选时间范围和条数；出参：现有审计领域记录；流程：补充可信租户后调用应用服务。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sales:order:view')")
    public ApiResponse<List<OperationAuditEntry>> query(
            @RequestParam(name = "entity_type") String entityType,
            @RequestParam(name = "entity_id") UUID entityId,
            @RequestParam(name = "occurred_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime occurredFrom,
            @RequestParam(name = "occurred_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime occurredTo,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        String normalizedType = entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);
        if (!"SALES_ORDER".equals(normalizedType)) {
            throw new IllegalArgumentException("当前操作审计只支持 SALES_ORDER");
        }
        return ApiResponse.success(applicationService.query(new OperationAuditQuery(
                TenantContextHolder.requireTenantId(), normalizedType, entityId,
                occurredFrom, occurredTo, limit)));
    }
}
