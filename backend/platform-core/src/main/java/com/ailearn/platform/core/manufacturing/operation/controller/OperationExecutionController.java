package com.ailearn.platform.core.manufacturing.operation.controller;

import com.ailearn.platform.core.manufacturing.operation.application.OperationExecutionApplicationService;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.dto.OperationExecutionCreateRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
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
 * 工序执行 REST Controller。
 * <p>
 * Controller 负责请求绑定、权限入口和端口调用；状态迁移、暂停原因、设备冲突、工单联动、租户隔离及幂等均由应用服务负责。
 * </p>
 */
@RestController
@RequestMapping("/api/operation-executions")
public class OperationExecutionController {

    private final OperationExecutionApplicationService service;

    /**
     * 注入工序执行应用端口。
     *
     * @param service 工序执行应用端口
     */
    public OperationExecutionController(OperationExecutionApplicationService service) {
        this.service = service;
    }

    /**
     * 创建 NotStarted 工序执行实例。
     * 入参：工序执行创建 DTO 和 HTTP 幂等键；出参：未开始执行实例；流程：由应用端口校验派工、工单和工序一致性后保存事实。
     *
     * @param request 工序执行创建请求
     * @param idempotencyKey HTTP Idempotency-Key
     * @return NotStarted 工序执行实例
     */
    @PostMapping
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<OperationExecution> create(
            @RequestBody OperationExecutionCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.create(request, idempotencyKey));
    }

    /**
     * 查询当前租户的工序执行详情。
     * 入参：执行实例 ID；出参：工序执行实例或空数据；流程：由应用端口按可信租户查询，跨租户对象保持不可见。
     *
     * @param id 执行实例 ID
     * @return 工序执行详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<OperationExecution> detail(@PathVariable("id") UUID id) {
        return ApiResponse.success(service.find(id).orElse(null));
    }

    /**
     * 开始工序执行。
     * 入参：执行实例 ID、可选事件请求体和 HTTP 幂等键；出参：Running 执行实例；流程：透传事件时间，由应用端口校验状态并记录 STARTED 事件。
     *
     * @param id 执行实例 ID
     * @param request 执行事件请求体，可为空以交由应用端口返回稳定业务错误
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Running 工序执行实例
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<OperationExecution> start(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) OperationEventRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.start(id, occurredAt(request), idempotencyKey));
    }

    /**
     * 暂停工序执行。
     * 入参：执行实例 ID、暂停原因/事件时间请求体和 HTTP 幂等键；出参：Paused 执行实例；流程：由应用端口校验 Running 状态、原因和事件顺序。
     *
     * @param id 执行实例 ID
     * @param request 暂停原因与事件时间请求体
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Paused 工序执行实例
     */
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<OperationExecution> pause(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) OperationEventRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(service.pause(id, reason, occurredAt(request), idempotencyKey));
    }

    /**
     * 恢复暂停中的工序执行。
     * 入参：执行实例 ID、可选事件请求体和 HTTP 幂等键；出参：Running 执行实例；流程：透传事件时间，由应用端口校验 Paused 状态并记录 RESUMED 事件。
     *
     * @param id 执行实例 ID
     * @param request 执行事件请求体，可为空以交由应用端口返回稳定业务错误
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Running 工序执行实例
     */
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<OperationExecution> resume(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) OperationEventRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.resume(id, occurredAt(request), idempotencyKey));
    }

    /**
     * 完成工序执行。
     * 入参：执行实例 ID、可选事件请求体和 HTTP 幂等键；出参：Completed 执行实例；流程：透传事件时间，由应用端口校验 Running 状态并记录 COMPLETED 事件。
     *
     * @param id 执行实例 ID
     * @param request 执行事件请求体，可为空以交由应用端口返回稳定业务错误
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Completed 工序执行实例
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<OperationExecution> complete(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) OperationEventRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.complete(id, occurredAt(request), idempotencyKey));
    }

    /**
     * 读取事件请求体中的发生时间；空请求体返回 null，由应用端口统一返回缺少事件时间的业务错误。
     *
     * @param request 工序事件请求体
     * @return 事件发生时间或 null
     */
    private OffsetDateTime occurredAt(OperationEventRequest request) {
        return request == null ? null : request.occurredAt();
    }

    /**
     * 工序事件请求体；开始、恢复、完成只使用 occurredAt，暂停额外使用 reason。
     *
     * @param occurredAt 事件发生时间
     * @param reason 暂停原因
     */
    public record OperationEventRequest(
            @JsonProperty("occurred_at")
            @JsonAlias("occurredAt")
            OffsetDateTime occurredAt,
            String reason) {
    }
}
