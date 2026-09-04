package com.ailearn.platform.iot.telemetry.application;

import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 设备离线扫描器。
 * 用途：按设备模型的离线超时配置周期性刷新状态；入参由可信平台时钟生成，出参为本次变更行数。
 * 流程：调用状态端口的条件批量更新；数据库暂不可用时记录告警并等待下一轮，不阻断遥测接收线程。
 */
@Component
public class OfflineDeviceScanner {
    private static final Logger log = LoggerFactory.getLogger(OfflineDeviceScanner.class);
    private final DeviceStatusPort statusPort;

    public OfflineDeviceScanner(DeviceStatusPort statusPort) {
        this.statusPort = statusPort;
    }

    /** 供定时任务和 focused 测试调用的一次扫描。 */
    public int scanNow() {
        return statusPort.markOfflineIfTimedOut(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** 默认 30 秒扫描一次；超时阈值仍以每台设备模型配置为准。 */
    @Scheduled(fixedDelayString = "${iot.telemetry.offline-scan-delay-ms:30000}")
    public void scheduledScan() {
        try {
            scanNow();
        } catch (RuntimeException exception) {
            log.warn("IoT 设备离线扫描暂时失败，将在下一轮重试", exception);
        }
    }
}
