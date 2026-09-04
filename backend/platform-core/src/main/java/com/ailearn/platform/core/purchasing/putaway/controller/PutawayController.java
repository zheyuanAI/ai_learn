package com.ailearn.platform.core.purchasing.putaway.controller;

import com.ailearn.platform.core.purchasing.putaway.application.PutawayApplicationService;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayConfirmRequest;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayTaskPageView;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayTaskView;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购质量放行后的上架任务 REST API。
 */
@RestController
@RequestMapping("/api/putaway-tasks")
public class PutawayController {

    private final PutawayApplicationService applicationService;

    /**
     * 注入上架应用服务。
     */
    public PutawayController(PutawayApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询上架任务。
     */
    @GetMapping
    public ApiResponse<PutawayTaskPageView> page(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(applicationService.page(status, page, size));
    }

    /**
     * 确认从 ReceivingStaging 移动到 Storage。
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<PutawayTaskView> confirm(@PathVariable UUID id,
                                                @RequestBody PutawayConfirmRequest request,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirm(id, request, idempotencyKey));
    }
}
