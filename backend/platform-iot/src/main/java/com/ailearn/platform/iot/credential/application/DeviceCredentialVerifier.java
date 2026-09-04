package com.ailearn.platform.iot.credential.application;

import com.ailearn.platform.iot.device.domain.Device;
import java.util.UUID;

/** MQTT 认证端口；后续 MQTT 接入层只能通过该端口校验设备身份。 */
public interface DeviceCredentialVerifier {
    Device verify(UUID tenantId, String deviceCode, String credentialReference, String plainSecret);

    /**
     * 用途：校验由 MQTT 主题携带的凭证引用并解析可信设备身份；入参为主题中的 credential_reference。
     * 流程：读取唯一凭证 -> 校验 Active、设备租户、设备生命周期和 MQTT 协议 -> 返回设备；不信任消息载荷身份。
     * 说明：发布端密码由 Mosquitto 的 password_file 在连接阶段校验，订阅端回调按 MQTT 协议无法读取发布者密码。
     */
    Device verifyReference(String credentialReference);
}
