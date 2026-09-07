package com.ailearn.platform.core.transfer.controller;

import com.ailearn.platform.core.transfer.application.TransferApplicationService;
import com.ailearn.platform.core.transfer.dto.TransferCreateRequest;
import com.ailearn.platform.core.transfer.dto.TransferPageQuery;
import com.ailearn.platform.core.transfer.dto.TransferView;
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
 * 调拨 REST API。
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferApplicationService applicationService;

    /**
     * 注入调拨应用服务。
     *
     * @param applicationService 调拨应用服务
     */
    public TransferController(TransferApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询当前租户调拨分页。 */
    @GetMapping
    public ApiResponse<MasterDataPageResult<TransferView>> page(@ModelAttribute TransferPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /** 查询当前租户调拨详情。 */
    @GetMapping("/{id}")
    public ApiResponse<TransferView> find(@PathVariable("id") UUID id) {
        return ApiResponse.success(applicationService.find(id));
    }

    /**
     * 创建调拨草稿。
     *
     * @param request 创建请求
     * @param idempotencyKey 幂等键
     * @return 草稿响应
     */
    @PostMapping
    public ApiResponse<TransferView> create(@RequestBody TransferCreateRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 确认调拨。
     *
     * @param id 调拨单 ID
     * @param idempotencyKey 幂等键
     * @return 已确认响应
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<TransferView> confirm(@PathVariable("id") UUID id,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirm(id, idempotencyKey));
    }
}
