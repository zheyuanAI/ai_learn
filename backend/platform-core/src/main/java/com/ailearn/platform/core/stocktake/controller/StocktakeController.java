package com.ailearn.platform.core.stocktake.controller;

import com.ailearn.platform.core.stocktake.application.StocktakeApplicationService;
import com.ailearn.platform.core.stocktake.dto.StocktakeConfirmRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCreateRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakePageQuery;
import com.ailearn.platform.core.stocktake.dto.StocktakeView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    /** 查询当前租户盘点分页。 */
    @GetMapping
    public ApiResponse<MasterDataPageResult<StocktakeView>> page(@ModelAttribute StocktakePageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /** 查询当前租户盘点详情。 */
    @GetMapping("/{id}")
    public ApiResponse<StocktakeView> find(@PathVariable("id") UUID id) {
        return ApiResponse.success(applicationService.find(id));
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
    public ApiResponse<StocktakeView> start(@PathVariable("id") UUID id,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.start(id, idempotencyKey));
    }

    /**
     * 确认并调整盘点差异。
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<StocktakeView> confirm(@PathVariable("id") UUID id,
                                              @RequestBody StocktakeConfirmRequest request,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirm(id, request, idempotencyKey));
    }
}
