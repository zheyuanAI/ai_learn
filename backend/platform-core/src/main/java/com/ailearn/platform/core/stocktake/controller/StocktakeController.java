package com.ailearn.platform.core.stocktake.controller;

import com.ailearn.platform.core.stocktake.application.StocktakeApplicationService;
import com.ailearn.platform.core.stocktake.dto.StocktakeConfirmRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCreateRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeView;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 盘点 REST API。
 */
@RestController
@RequestMapping("/api/stocktakes")
public class StocktakeController {

    private final StocktakeApplicationService applicationService;

    /**
     * 注入盘点应用服务。
     *
     * @param applicationService 盘点应用服务
     */
    public StocktakeController(StocktakeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建盘点单。
     */
    @PostMapping
    public ApiResponse<StocktakeView> create(@RequestBody StocktakeCreateRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 开始盘点。
     */
    @PostMapping("/{id}/start")
    public ApiResponse<StocktakeView> start(@PathVariable UUID id,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.start(id, idempotencyKey));
    }

    /**
     * 确认并调整盘点差异。
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<StocktakeView> confirm(@PathVariable UUID id,
                                              @RequestBody StocktakeConfirmRequest request,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirm(id, request, idempotencyKey));
    }
}
