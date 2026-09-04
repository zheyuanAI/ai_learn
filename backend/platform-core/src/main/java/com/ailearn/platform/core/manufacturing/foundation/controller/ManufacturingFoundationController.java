package com.ailearn.platform.core.manufacturing.foundation.controller;

import com.ailearn.platform.core.manufacturing.foundation.application.ManufacturingFoundationService;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingCreateRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES foundation 的最小写入口。
 * <p>
 * Controller 只绑定请求并透传幂等键；租户、用户、权限、版本校验和 PostgreSQL 事务全部由应用服务负责，
 * 不接受客户端传入 tenant_id 或 created_by。
 * </p>
 */
@RestController
@RequestMapping("/api")
public class ManufacturingFoundationController {

    private final ManufacturingFoundationService service;

    /**
     * 注入 foundation 应用端口。
     *
     * @param service BOM/Routing 应用端口
     */
    public ManufacturingFoundationController(ManufacturingFoundationService service) {
        this.service = service;
    }

    /**
     * 创建 BOM 版本事实。
     * 入参：BOM 请求体和幂等键；出参：当前租户新建的 BOM；流程：交由应用服务完成权限、字段、租户关联和持久化校验。
     *
     * @param request BOM 创建请求
     * @param idempotencyKey HTTP 幂等键
     * @return BOM 事实
     */
    @PostMapping("/boms")
    public ApiResponse<BomFact> createBom(@RequestBody BomCreateRequest request,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.createBom(request, idempotencyKey));
    }

    /**
     * 创建 Routing 版本事实。
     * 入参：Routing 请求体和幂等键；出参：当前租户新建的 Routing；流程：交由应用服务完成权限、字段、租户关联和持久化校验。
     *
     * @param request Routing 创建请求
     * @param idempotencyKey HTTP 幂等键
     * @return Routing 事实
     */
    @PostMapping("/routings")
    public ApiResponse<RoutingFact> createRouting(@RequestBody RoutingCreateRequest request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.createRouting(request, idempotencyKey));
    }
}
