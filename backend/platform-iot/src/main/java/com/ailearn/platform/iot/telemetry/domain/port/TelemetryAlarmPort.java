package com.ailearn.platform.iot.telemetry.domain.port;

import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;

/**
 * Task 19 告警边界端口。
 * Task 18 只通知已保存且推动状态的消息，不在此处实现阈值、生命周期或告警持久化。
 */
public interface TelemetryAlarmPort {

    /**
     * 用途：把已保存的有效消息交给后续告警模块；入参为原命令和最新状态，无返回值。
     */
    void onTelemetryAccepted(TelemetryIngestionCommand command, DeviceStatus status);
}
