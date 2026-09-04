package com.ailearn.platform.iot.profile.controller;

import com.ailearn.platform.iot.profile.application.DeviceProfileApplicationService;
import com.ailearn.platform.iot.profile.dto.AlarmRuleCreateRequest;
import com.ailearn.platform.iot.profile.dto.AlarmRuleView;
import com.ailearn.platform.iot.profile.dto.DeviceProfileCreateRequest;
import com.ailearn.platform.iot.profile.dto.DeviceProfilePageResult;
import com.ailearn.platform.iot.profile.dto.DeviceProfileView;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 设备模型和一期单指标告警规则 REST API。 */
@RestController
@RequestMapping("/api")
public class DeviceProfileController {
    private final DeviceProfileApplicationService service;

    public DeviceProfileController(DeviceProfileApplicationService service) {
        this.service = service;
    }

    @PostMapping("/device-profiles")
    public ApiResponse<DeviceProfileView> createProfile(@RequestBody DeviceProfileCreateRequest request,
                                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.create(request, idempotencyKey));
    }

    @GetMapping("/device-profiles")
    public ApiResponse<DeviceProfilePageResult> pageProfiles(
            @RequestParam(name = "profile_code", required = false) String profileCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.page(profileCode, page, size));
    }

    @GetMapping("/device-profiles/{id}")
    public ApiResponse<DeviceProfileView> profile(@PathVariable UUID id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping("/device-alarm-rules")
    public ApiResponse<AlarmRuleView> createRule(@RequestBody AlarmRuleCreateRequest request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.createRule(request, idempotencyKey));
    }

    @GetMapping("/device-alarm-rules")
    public ApiResponse<List<AlarmRuleView>> rules(
            @RequestParam(name = "device_profile_id", required = false) UUID profileId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.rules(profileId, page, size));
    }
}
