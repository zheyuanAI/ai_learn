package com.ailearn.platform.iot.telemetry.application;

/**
 * 遥测摄取应用端口。
 * 入参为已经完成设备凭证解析的遥测命令，出参为统一摄取结果；MQTT 和模拟入口都必须复用该端口。
 */
public interface TelemetryIngestionService {

    /**
     * 用途：接收一条设备遥测消息并完成校验、去重、事实保存和状态推进。
     * 入参：包含可信租户/设备上下文的遥测命令；出参：新消息或重复消息的摄取结果。
     * 流程：完整校验后原子声明去重键，追加遥测事实，再按设备时间推进状态。
     */
    TelemetryIngestionResult ingest(TelemetryIngestionCommand command);
}
