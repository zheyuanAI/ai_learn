package com.ailearn.platform.iot.credential.domain.port;

import com.ailearn.platform.iot.credential.domain.DeviceCredential;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 凭证持久化端口；所有方法带租户，设备关系由服务层再次校验。 */
public interface CredentialRepository {
    DeviceCredential insert(DeviceCredential credential);
    Optional<DeviceCredential> findById(UUID tenantId, UUID deviceId, UUID credentialId);
    Optional<DeviceCredential> findByReference(UUID tenantId, String credentialReference);
    /**
     * 按凭证业务标识跨租户定位 MQTT 主题凭证；调用方只接收内部对象并立即校验归属，不对外暴露租户信息。
     * 返回列表是为了在历史数据出现跨租户重复标识时拒绝歧义认证，而不是任选一条记录。
     */
    List<DeviceCredential> findByReferenceAcrossTenants(String credentialReference);
    List<DeviceCredential> findByDevice(UUID tenantId, UUID deviceId);
    boolean revoke(UUID tenantId, UUID deviceId, UUID credentialId, UUID operatorId, OffsetDateTime revokedAt);
}
