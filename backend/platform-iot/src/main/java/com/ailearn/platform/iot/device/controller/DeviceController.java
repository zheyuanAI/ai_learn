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

    @PostMapping
    public ApiResponse<DeviceView> create(@RequestBody DeviceCreateRequest request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.create(request, idempotencyKey));
    }

    @GetMapping
    public ApiResponse<DevicePageResult> page(
            @RequestParam(name = "device_code", required = false) String deviceCode,
            @RequestParam(name = "lifecycle_status", required = false) String lifecycleStatus,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.page(deviceCode, lifecycleStatus, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceView> detail(@PathVariable UUID id) {
        return ApiResponse.success(service.detail(id));
    }

    @PatchMapping("/{id}/lifecycle")
    public ApiResponse<DeviceView> lifecycle(@PathVariable UUID id, @RequestBody DeviceLifecycleRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.changeLifecycle(id, request, idempotencyKey));
    }
}
