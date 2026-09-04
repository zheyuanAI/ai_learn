package com.ailearn.platform.core.manufacturing.execution.controller;

import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单生命周期 REST Controller。
 * <p>
 * Controller 负责请求绑定、幂等键透传、权限入口和统一响应包装；租户、状态机和业务审计由应用端口负责。
 * </p>
 */
@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderExecutionController {

    private final WorkOrderExecutionService service;

    /**
     * 注入工单生命周期应用端口。
     *
     * @param service 工单生命周期应用端口
     */
    public WorkOrderExecutionController(WorkOrderExecutionService service) {
        this.service = service;
    }

    /**
     * 创建 Draft 工单。
     * 入参：工单创建 DTO 和 HTTP 幂等键；出参：含工单基础事实与生命周期状态的统一响应；流程：透传应用端口
     * 完成可信上下文校验、BOM/Routing 校验、持久化和幂等处理。
     *
     * @param request 工单创建请求
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Draft 工单生命周期
     */
    @PostMapping
    @PreAuthorize("hasAuthority('mes:workorder:create')")
    public ApiResponse<WorkOrderLifecycle> create(@RequestBody WorkOrderCreateRequest request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.createWorkOrder(request, idempotencyKey));
    }

    /**
     * 查询当前租户的工单生命周期详情。
     * 入参：工单 ID；出参：工单生命周期或空数据；流程：由应用端口按可信租户查询，跨租户对象保持不可见。
     *
     * @param id 工单 ID
     * @return 工单生命周期详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mes:workorder:view')")
    public ApiResponse<WorkOrderLifecycle> detail(@PathVariable("id") UUID id) {
        return ApiResponse.success(service.find(id).orElse(null));
    }

    /**
     * 提交工单审核。
     * 入参：工单 ID 和 HTTP 幂等键；出参：待审核生命周期；流程：由应用端口校验 Draft/Rejected 状态并保存提交审计。
     *
     * @param id 工单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 提交后的工单生命周期
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('mes:workorder:submit')")
    public ApiResponse<WorkOrderLifecycle> submit(@PathVariable("id") UUID id,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.submit(id, idempotencyKey));
    }

    /**
     * 审核并下达工单。
     * 入参：工单 ID 和 HTTP 幂等键；出参：Released 生命周期；流程：由应用端口重新校验同租户 BOM/Routing 并锁定版本。
     *
     * @param id 工单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 下达后的工单生命周期
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('mes:workorder:approve')")
    public ApiResponse<WorkOrderLifecycle> approve(@PathVariable("id") UUID id,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.approve(id, idempotencyKey));
    }

    /**
     * 驳回待审核工单。
     * 入参：工单 ID、拒绝原因请求体和 HTTP 幂等键；出参：Rejected 生命周期；流程：由应用端口校验状态和非空原因并保存审核审计。
     *
     * @param id 工单 ID
     * @param request 拒绝原因请求体，可为空以交由应用端口返回稳定业务错误
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 驳回后的工单生命周期
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('mes:workorder:approve')")
    public ApiResponse<WorkOrderLifecycle> reject(@PathVariable("id") UUID id,
                                                  @RequestBody(required = false) RejectRequest request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String reason = request == null ? null : request.rejectionReason();
        return ApiResponse.success(service.reject(id, reason, idempotencyKey));
    }

    /**
     * 正常完成工单。
     * 入参：工单 ID 和 HTTP 幂等键；出参：Completed/Normal 生命周期；流程：由应用端口校验工序、报工、质检、入库及在途库存约束。
     *
     * @param id 工单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 正常完成后的工单生命周期
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('mes:workorder:complete')")
    public ApiResponse<WorkOrderLifecycle> complete(@PathVariable("id") UUID id,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.complete(id, idempotencyKey));
    }

    /**
     * 人工完成工单。
     * 入参：工单 ID、人工完成原因请求体和 HTTP 幂等键；出参：Completed/Manual 生命周期；流程：由应用端口只保存人工审计，
     * 不补造工序、报工、质检、入库或库存事实。
     *
     * @param id 工单 ID
     * @param request 人工完成原因请求体，可为空以交由应用端口返回稳定业务错误
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 人工完成后的工单生命周期
     */
    @PostMapping("/{id}/manual-complete")
    @PreAuthorize("hasAuthority('mes:workorder:complete')")
    public ApiResponse<WorkOrderLifecycle> manualComplete(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ManualCompleteRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String reason = request == null ? null : request.completionReason();
        return ApiResponse.success(service.manualComplete(id, reason, idempotencyKey));
    }

    /**
     * 工单驳回请求体；仅承载 Controller 到应用端口所需的人工原因，不扩展领域 DTO。
     *
     * @param rejectionReason 驳回原因
     */
    public record RejectRequest(
            @JsonProperty("rejection_reason")
            @JsonAlias({"rejectionReason", "reason"})
            String rejectionReason) {
    }

    /**
     * 工单人工完成请求体；仅承载 Controller 到应用端口所需的人工原因，不扩展领域 DTO。
     *
     * @param completionReason 人工完成原因
     */
    public record ManualCompleteRequest(
            @JsonProperty("completion_reason")
            @JsonAlias({"completionReason", "reason"})
            String completionReason) {
    }
}
