package com.ailearn.platform.iot.credential.application;

import com.ailearn.platform.iot.credential.dto.CredentialCreateRequest;
import com.ailearn.platform.iot.credential.dto.CredentialCreatedView;
import com.ailearn.platform.iot.credential.dto.CredentialView;
import java.util.List;
import java.util.UUID;

/** 设备凭证创建、查询和撤销端口。 */
public interface DeviceCredentialApplicationService {
    CredentialCreatedView create(UUID deviceId, CredentialCreateRequest request, String idempotencyKey);
    List<CredentialView> list(UUID deviceId);
    CredentialView revoke(UUID deviceId, UUID credentialId, String idempotencyKey);
}
