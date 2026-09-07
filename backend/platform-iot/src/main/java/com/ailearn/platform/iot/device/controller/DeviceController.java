package com.ailearn.platform.iot.device.controller;

import com.ailearn.platform.iot.device.application.DeviceApplicationService;
import com.ailearn.platform.iot.device.dto.DeviceCreateRequest;
import com.ailearn.platform.iot.device.dto.DeviceLifecycleRequest;
import com.ailearn.platform.iot.device.dto.DevicePageResult;
import com.ailearn.platform.iot.device.dto.DeviceView;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 设备身份、稳定归属和生命周期 REST API。 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceApplicationService service;

    public DeviceController(DeviceApplicationService service) {
        this.service = service;
    }

    /** 创建设备；幂等键由应用服务校验，租户和操作人从可信上下文取得。 */
    @PostMapping
    public ApiResponse<DeviceView> create(@RequestBody DeviceCreateRequest request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.create(request, idempotencyKey));
    }

    /** 查询当前租户设备分页，筛选和分页边界由应用服务统一处理。 */
    @GetMapping
    public ApiResponse<DevicePageResult> page(
            @RequestParam(name = "device_code", required = false) String deviceCode,
            @RequestParam(name = "lifecycle_status", required = false) String lifecycleStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.page(deviceCode, lifecycleStatus, page, size));
    }

    /** 查询单台设备详情，跨租户或不存在的设备由应用服务统一隐藏。 */
    @GetMapping("/{id}")
    public ApiResponse<DeviceView> detail(@PathVariable("id") UUID id) {
        return ApiResponse.success(service.detail(id));
    }

    /** 幂等推进设备生命周期，不在 Controller 层直接修改设备状态。 */
    @PatchMapping("/{id}/lifecycle")
    public ApiResponse<DeviceView> lifecycle(@PathVariable("id") UUID id, @RequestBody DeviceLifecycleRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.changeLifecycle(id, request, idempotencyKey));
    }
}
