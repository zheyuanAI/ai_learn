package com.ailearn.platform.iot.internal.s7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.alarm.domain.port.AlarmRepository;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

/** S7 内部 Facts 接口的服务间认证、时效和租户查询边界测试。 */
class S7FactsControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-04T02:00:00Z");
    private static final String SECRET = "s7-test-secret";

    @Test
    void acceptsFreshSignatureAndKeepsTenantFilter() {
        DeviceRepository devices = mock(DeviceRepository.class);
        AlarmRepository alarms = mock(AlarmRepository.class);
        DeviceStatusPort statuses = mock(DeviceStatusPort.class);
        when(devices.findPage(TENANT_ID, null, null, 0, 10000)).thenReturn(List.of(device()));
        when(statuses.find(TENANT_ID, DEVICE_ID)).thenReturn(DeviceStatus.initial(TENANT_ID, DEVICE_ID));
        S7FactsController controller = controller(devices, alarms, statuses);
        MockHttpServletRequest request = request("/internal/s7/facts/summary/device", "tenant_id=" + TENANT_ID);

        S7FactsController.SummaryResponse response = controller.summary("device", TENANT_ID, null,
                null, null, request, "platform-core", Long.toString(NOW.getEpochSecond()), signature(request));

        assertEquals(1, response.metrics().get("device_count").intValue());
        org.mockito.Mockito.verify(devices).findPage(TENANT_ID, null, null, 0, 10000);
        verifyNoInteractions(alarms);
    }

    @Test
    void rejectsInvalidSignatureBeforeReadingFacts() {
        DeviceRepository devices = mock(DeviceRepository.class);
        AlarmRepository alarms = mock(AlarmRepository.class);
        DeviceStatusPort statuses = mock(DeviceStatusPort.class);
        S7FactsController controller = controller(devices, alarms, statuses);
        MockHttpServletRequest request = request("/internal/s7/facts/summary/device", "tenant_id=" + TENANT_ID);

        assertThrows(ResponseStatusException.class, () -> controller.summary("device", TENANT_ID, null,
                null, null, request, "platform-core", Long.toString(NOW.getEpochSecond()), "invalid"));

        verifyNoInteractions(devices, alarms, statuses);
    }

    @Test
    void rejectsStaleSignatureBeforeReadingFacts() {
        DeviceRepository devices = mock(DeviceRepository.class);
        AlarmRepository alarms = mock(AlarmRepository.class);
        DeviceStatusPort statuses = mock(DeviceStatusPort.class);
        S7FactsController controller = controller(devices, alarms, statuses);
        MockHttpServletRequest request = request("/internal/s7/facts/summary/device", "tenant_id=" + TENANT_ID);
        long staleTimestamp = NOW.minusSeconds(121).getEpochSecond();

        assertThrows(ResponseStatusException.class, () -> controller.summary("device", TENANT_ID, null,
                null, null, request, "platform-core", Long.toString(staleTimestamp), signature(request, staleTimestamp)));

        verifyNoInteractions(devices, alarms, statuses);
    }

    /** 创建固定时钟的控制器，避免认证窗口测试依赖宿主机时间。 */
    private static S7FactsController controller(DeviceRepository devices, AlarmRepository alarms,
                                                 DeviceStatusPort statuses) {
        return new S7FactsController(devices, alarms, statuses, SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 创建与控制器验签原文一致的 GET 请求。 */
    private static MockHttpServletRequest request(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setQueryString(query);
        return request;
    }

    /** 使用当前时间戳生成内部服务签名。 */
    private static String signature(HttpServletRequest request) {
        return signature(request, NOW.getEpochSecond());
    }

    /** 使用指定时间戳生成内部服务签名，覆盖重放窗口边界。 */
    private static String signature(HttpServletRequest request, long timestamp) {
        String canonical = timestamp + "\n" + request.getMethod() + "\n"
                + request.getRequestURI() + "\n" + request.getQueryString();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormatHolder.encode(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Device device() {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new Device(DEVICE_ID, TENANT_ID, "M-001", "机台", UUID.randomUUID(), "MQTT",
                DeviceLifecycleStatus.Active, null, null, null, UUID.randomUUID(), updatedAt,
                UUID.randomUUID(), updatedAt);
    }

    /** 避免测试直接依赖控制器内部的签名格式实现细节。 */
    private static final class HexFormatHolder {
        private static String encode(byte[] value) {
            return java.util.HexFormat.of().formatHex(value);
        }
    }
}
