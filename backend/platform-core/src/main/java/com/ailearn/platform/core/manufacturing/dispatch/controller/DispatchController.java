package com.ailearn.platform.core.manufacturing.dispatch.controller;

import com.ailearn.platform.core.manufacturing.dispatch.application.DispatchApplicationService;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchCreateRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import jakarta.validation.Valid;
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
 * 派工安排 REST Controller。
 * <p>
 * Controller 不复制工单或工序状态规则；派工数量和操作员由请求 DTO 显式接收并由 HTTP 校验保证必填，
 * 业务状态规则仍交给派工应用端口。
 * </p>
 */
@RestController
@RequestMapping("/api/dispatch-orders")
public class DispatchController {

    private final DispatchApplicationService service;

    /**
     * 注入派工应用端口。
     *
     * @param service 派工应用端口
     */
    public DispatchController(DispatchApplicationService service) {
        this.service = service;
    }

    /**
     * 创建 Draft 派工安排。
     * 入参：派工创建 DTO 和 HTTP 幂等键；出参：Draft 派工单；流程：透传应用端口完成可信上下文、工单状态和幂等校验。
     *
     * @param request 派工创建请求
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Draft 派工单
     */
    @PostMapping
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public ApiResponse<DispatchOrder> create(@Valid @RequestBody DispatchCreateRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.create(request, idempotencyKey));
    }

    /**
     * 查询当前租户的派工详情。
     * 入参：派工单 ID；出参：派工单或空数据；流程：由应用端口按可信租户查询，跨租户对象保持不可见。
     *
     * @param id 派工单 ID
     * @return 派工单详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public ApiResponse<DispatchOrder> detail(@PathVariable("id") UUID id) {
        return ApiResponse.success(service.find(id).orElse(null));
    }

    /**
     * 发布派工安排。
     * 入参：派工单 ID 和 HTTP 幂等键；出参：Released 派工单；流程：由应用端口校验关联工单已下达并执行 Draft 到 Released 状态迁移。
     *
     * @param id 派工单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 已发布派工单
     */
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public ApiResponse<DispatchOrder> release(@PathVariable("id") UUID id,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.release(id, idempotencyKey));
    }

    /**
     * 将派工安排标记为 Processing。
     * 入参：派工单 ID 和 HTTP 幂等键；出参：Processing 派工单；流程：由应用端口执行已有的派工状态迁移，不伪造工序执行事实。
     *
     * @param id 派工单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Processing 派工单
     */
    @PostMapping("/{id}/start-processing")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<DispatchOrder> startProcessing(
            @PathVariable("id") UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.startProcessing(id, idempotencyKey));
    }

    /**
     * 完成派工安排。
     * 入参：派工单 ID 和 HTTP 幂等键；出参：Completed 派工单；流程：由应用端口校验 Processing 状态并保存派工完成审计。
     *
     * @param id 派工单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Completed 派工单
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public ApiResponse<DispatchOrder> complete(@PathVariable("id") UUID id,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.complete(id, idempotencyKey));
    }
}
