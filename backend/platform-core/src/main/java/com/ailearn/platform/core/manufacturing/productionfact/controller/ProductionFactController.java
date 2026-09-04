package com.ailearn.platform.core.manufacturing.productionfact.controller;

import com.ailearn.platform.core.manufacturing.productionfact.application.ProductionFactApplicationService;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactSummary;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import com.ailearn.platform.core.manufacturing.productionfact.dto.FinishedGoodsReceiptCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialIssueCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialReturnCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionSubmitRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.WorkReportCreateRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Task16 生产事实 REST 控制器；不接收客户端租户字段。 */
@RestController
@RequestMapping("/api")
public class ProductionFactController {

    private final ProductionFactApplicationService service;

    /** 注入生产事实应用端口。 */
    public ProductionFactController(ProductionFactApplicationService service) {
        this.service = service;
    }

    /** 创建生产领料 Draft。 */
    @PostMapping("/material-issues")
    @PreAuthorize("hasAuthority('mes:material:requisition')")
    public ApiResponse<MaterialIssue> createIssue(@RequestBody MaterialIssueCreateRequest request,
                                                   @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.createMaterialIssue(request, key));
    }

    /** 确认生产领料。 */
    @PostMapping("/material-issues/{id}/confirm")
    @PreAuthorize("hasAuthority('mes:material:confirm')")
    public ApiResponse<ProductionFactSummary> confirmIssue(@PathVariable UUID id,
                                                            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.confirmMaterialIssue(id, key));
    }

    /** 创建生产退料 Draft。 */
    @PostMapping("/material-returns")
    @PreAuthorize("hasAuthority('mes:material:requisition')")
    public ApiResponse<MaterialReturn> createReturn(@RequestBody MaterialReturnCreateRequest request,
                                                    @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.createMaterialReturn(request, key));
    }

    /** 确认生产退料。 */
    @PostMapping("/material-returns/{id}/confirm")
    @PreAuthorize("hasAuthority('mes:material:confirm')")
    public ApiResponse<ProductionFactSummary> confirmReturn(@PathVariable UUID id,
                                                             @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.confirmMaterialReturn(id, key));
    }

    /** 创建报工事实。 */
    @PostMapping("/work-reports")
    @PreAuthorize("hasAuthority('mes:report:manage')")
    public ApiResponse<WorkReport> createReport(@RequestBody WorkReportCreateRequest request,
                                                @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.createWorkReport(request, key));
    }

    /** 查询当前租户工单报工。 */
    @GetMapping("/work-reports/{workOrderId}")
    @PreAuthorize("hasAuthority('mes:report:manage')")
    public ApiResponse<List<WorkReport>> reports(@PathVariable UUID workOrderId) {
        return ApiResponse.success(service.findWorkReports(workOrderId));
    }

    /** 创建 Draft 质检。 */
    @PostMapping("/quality-inspections")
    @PreAuthorize("hasAuthority('mes:quality:inspect')")
    public ApiResponse<QualityInspection> createInspection(
            @RequestBody QualityInspectionCreateRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.createQualityInspection(request, key));
    }

    /** 提交质检结果。 */
    @PostMapping("/quality-inspections/{id}/submit")
    @PreAuthorize("hasAuthority('mes:quality:inspect')")
    public ApiResponse<QualityInspection> submitInspection(@PathVariable UUID id,
                                                            @RequestBody QualityInspectionSubmitRequest request,
                                                            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.submitQualityInspection(id, request, key));
    }

    /** 查询当前租户工单质检。 */
    @GetMapping("/quality-inspections/{workOrderId}")
    @PreAuthorize("hasAuthority('mes:quality:inspect')")
    public ApiResponse<List<QualityInspection>> inspections(@PathVariable UUID workOrderId) {
        return ApiResponse.success(service.findQualityInspections(workOrderId));
    }

    /** 创建 Draft 成品入库。 */
    @PostMapping("/finished-goods-receipts")
    @PreAuthorize("hasAuthority('mes:finished:receipt')")
    public ApiResponse<FinishedGoodsReceipt> createReceipt(
            @RequestBody FinishedGoodsReceiptCreateRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.createFinishedGoodsReceipt(request, key));
    }

    /** 确认成品入库。 */
    @PostMapping("/finished-goods-receipts/{id}/confirm")
    @PreAuthorize("hasAuthority('mes:finished:confirm')")
    public ApiResponse<ProductionFactSummary> confirmReceipt(@PathVariable UUID id,
                                                              @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.confirmFinishedGoodsReceipt(id, key));
    }

    /** 查询当前租户工单成品入库。 */
    @GetMapping("/finished-goods-receipts/{workOrderId}")
    @PreAuthorize("hasAuthority('mes:finished:receipt')")
    public ApiResponse<List<FinishedGoodsReceipt>> receipts(@PathVariable UUID workOrderId) {
        return ApiResponse.success(service.findFinishedGoodsReceipts(workOrderId));
    }
}
