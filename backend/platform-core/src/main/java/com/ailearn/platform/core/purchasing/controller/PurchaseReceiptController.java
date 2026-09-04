package com.ailearn.platform.core.purchasing.controller;

import com.ailearn.platform.core.purchasing.application.PurchaseOrderApplicationService;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptConfirmRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptView;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购到货验收 REST API。
 */
@RestController
@RequestMapping("/api/purchase-receipts")
public class PurchaseReceiptController {

    private final PurchaseOrderApplicationService applicationService;

    /**
     * 注入采购应用服务。
     */
    public PurchaseReceiptController(PurchaseOrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 确认外观验收、拒收和实际接收。
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<PurchaseReceiptView> confirm(@PathVariable UUID id,
                                                    @RequestBody PurchaseReceiptConfirmRequest request,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirmReceipt(id, request, idempotencyKey));
    }
}
