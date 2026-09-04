package com.ailearn.platform.core.quality.controller;

import com.ailearn.platform.core.quality.application.PurchaseQualityApplicationService;
import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import com.ailearn.platform.core.quality.dto.QualityDispositionConfirmRequest;
import com.ailearn.platform.core.quality.dto.QualityDispositionRequest;
import com.ailearn.platform.core.quality.dto.QualityInspectionRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购到货质检与质量处置 REST API。
 */
@RestController
public class PurchaseQualityController {

    private final PurchaseQualityApplicationService applicationService;

    /**
     * 注入质量应用服务。
     */
    public PurchaseQualityController(PurchaseQualityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前租户质检事实。
     */
    @GetMapping("/api/purchase-receipts/quality-inspections")
    public ApiResponse<List<com.ailearn.platform.core.quality.dto.QualityInspectionView>> inspections() {
        return ApiResponse.success(applicationService.listInspections());
    }

    /**
     * 记录一条采购到货质检事实。
     */
    @PostMapping("/api/purchase-receipts/{id}/quality/inspect")
    public ApiResponse<com.ailearn.platform.core.quality.dto.QualityInspectionView> inspect(
            @PathVariable UUID id, @RequestBody QualityInspectionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.inspect(id, request, idempotencyKey));
    }

    /**
     * 查询当前租户质量处置记录。
     */
    @GetMapping("/api/purchase-quality-dispositions")
    public ApiResponse<List<com.ailearn.platform.core.quality.dto.QualityDispositionView>> dispositions() {
        return ApiResponse.success(applicationService.listDispositions());
    }

    /**
     * 下达质量处置决定，决定完成后由仓库另行确认执行。
     */
    @PostMapping("/api/purchase-receipts/{id}/quality/{type}")
    public ApiResponse<com.ailearn.platform.core.quality.dto.QualityDispositionView> decide(
            @PathVariable UUID id, @PathVariable String type, @RequestBody QualityDispositionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        QualityDispositionType dispositionType;
        try {
            dispositionType = QualityDispositionType.valueOf(type.substring(0, 1).toUpperCase()
                    + type.substring(1).toLowerCase());
        } catch (RuntimeException exception) {
            throw new com.ailearn.platform.core.purchasing.exception.PurchasingException(
                    com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode.PO_004,
                    "不支持的质量处置类型");
        }
        return ApiResponse.success(switch (dispositionType) {
            case Release -> applicationService.release(id, request, idempotencyKey);
            case Return -> applicationService.returnToSupplier(id, request, idempotencyKey);
            case Scrap -> applicationService.scrap(id, request, idempotencyKey);
        });
    }

    /**
     * 仓库确认执行质量处置。
     */
    @PostMapping("/api/purchase-quality-dispositions/{id}/confirm")
    public ApiResponse<com.ailearn.platform.core.quality.dto.QualityDispositionView> confirm(
            @PathVariable UUID id, @RequestBody QualityDispositionConfirmRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirmDisposition(id, request, idempotencyKey));
    }
}
