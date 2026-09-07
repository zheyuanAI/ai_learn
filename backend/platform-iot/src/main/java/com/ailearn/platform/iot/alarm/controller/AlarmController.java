package com.ailearn.platform.iot.alarm.controller;

import com.ailearn.platform.iot.alarm.application.AlarmApplicationService;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.dto.AckAlarmRequest;
import com.ailearn.platform.iot.alarm.dto.AlarmPageResult;
import com.ailearn.platform.iot.alarm.dto.AlarmView;
import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationService;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkResult;
import com.ailearn.platform.iot.contextlink.dto.ManualBusinessContextRequest;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** IoT 告警查询与确认 API。 */
@RestController
@RequestMapping("/api/device-alarms")
public class AlarmController {
    private final AlarmApplicationService service;
    private final AlarmContextLinkApplicationService contextLinkService;

    public AlarmController(AlarmApplicationService service,
                           AlarmContextLinkApplicationService contextLinkService) {
        this.service = service;
        this.contextLinkService = contextLinkService;
    }

    /** 查询当前租户可见告警；时间、状态、上下文筛选和分页边界由应用服务处理。 */
    @GetMapping
    public ApiResponse<AlarmPageResult> page(
            @RequestParam(name = "device_id", required = false) UUID deviceId,
            @RequestParam(name = "status", required = false) AlarmStatus status,
            @RequestParam(name = "alarm_level", required = false) String alarmLevel,
            @RequestParam(name = "date_from", required = false) OffsetDateTime from,
            @RequestParam(name = "date_to", required = false) OffsetDateTime to,
            @RequestParam(name = "context_status", required = false) String contextStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.page(deviceId, status, alarmLevel, from, to, contextStatus, page, size));
    }

    /** 查询告警详情；不存在、跨租户或不可见记录统一由应用服务拒绝。 */
    @GetMapping("/{id}")
    public ApiResponse<AlarmView> detail(@PathVariable("id") UUID id) {
        return ApiResponse.success(service.detail(id));
    }

    /** 幂等执行人工告警确认，不在 Controller 层推进告警状态。 */
    @PostMapping("/{id}/ack")
    public ApiResponse<AlarmView> ack(@PathVariable("id") UUID id, @RequestBody AckAlarmRequest request,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(service.ack(id, request == null ? null : request.ackComment(), idempotencyKey));
    }

    /**
     * 人工补充或更正告警业务上下文；租户从可信请求上下文读取，原始告警时间线保持不变。
     *
     * @param id 告警标识
     * @param request 至少包含一个业务上下文标识
     * @param idempotencyKey 人工写操作幂等键
     * @return 上下文补链结果
     */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/business-context")
    public ApiResponse<ContextLinkResult> businessContext(
            @PathVariable("id") UUID id,
            @RequestBody ManualBusinessContextRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(contextLinkService.linkManually(TenantContextHolder.requireTenantId(), id,
                request == null ? null : request.operationExecutionId(),
                request == null ? null : request.workOrderId(), idempotencyKey));
    }
}
