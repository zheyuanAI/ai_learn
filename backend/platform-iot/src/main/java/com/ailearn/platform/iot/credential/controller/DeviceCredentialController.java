package com.ailearn.platform.iot.credential.controller;

import com.ailearn.platform.iot.credential.application.DeviceCredentialApplicationService;
import com.ailearn.platform.iot.credential.dto.CredentialCreateRequest;
import com.ailearn.platform.iot.credential.dto.CredentialCreatedView;
import com.ailearn.platform.iot.credential.dto.CredentialView;
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

/** 设备接入凭证管理 REST API；明文只出现在创建响应。 */
@RestController
@RequestMapping("/api/devices/{deviceId}/credentials")
public class DeviceCredentialController {
    private final DeviceCredentialApplicationService service;

    public DeviceCredentialController(DeviceCredentialApplicationService service) {
        this.service = service;
    }

    /** 创建设备接入凭证；明文 secret 仅由应用服务在首次创建响应中返回。 */
    @PostMapping
    public ApiResponse<CredentialCreatedView> create(@PathVariable("deviceId") UUID deviceId,
                                                     @RequestBody(required = false) CredentialCreateRequest request,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.create(deviceId, request == null ? new CredentialCreateRequest() : request,
                idempotencyKey));
    }

    /** 查询设备凭证管理视图，不返回任何 secret 明文或摘要。 */
    @GetMapping
    public ApiResponse<List<CredentialView>> list(@PathVariable("deviceId") UUID deviceId) {
        return ApiResponse.success(service.list(deviceId));
    }

    /** 撤销设备凭证；幂等和 Active 状态校验由应用服务负责。 */
    @PostMapping("/{credentialId}/revoke")
    public ApiResponse<CredentialView> revoke(@PathVariable("deviceId") UUID deviceId,
                                             @PathVariable("credentialId") UUID credentialId,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.revoke(deviceId, credentialId, idempotencyKey));
    }
}
