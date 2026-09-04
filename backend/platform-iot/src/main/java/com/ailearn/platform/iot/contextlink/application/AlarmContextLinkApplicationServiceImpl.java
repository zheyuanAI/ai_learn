package com.ailearn.platform.iot.contextlink.application;

import com.ailearn.platform.iot.contextlink.domain.AlarmContextCandidate;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkResult;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkTask;
import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.ailearn.platform.iot.contextlink.domain.port.AlarmContextLinkRepository;
import com.ailearn.platform.iot.contextlink.domain.port.ProductionContextQueryPort;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IoT 侧上下文补链服务。
 * 流程：读取告警 -> 以 tenant/device/alarmTime 查询 Core 只读端口 -> 严格校验返回摘要 ->
 * 成功更新已有告警上下文字段；无匹配、Core 不可用或结果不一致均只进入重试队列。
 */
@Service
public class AlarmContextLinkApplicationServiceImpl implements AlarmContextLinkApplicationService {
    private static final String LINKED_STATUS = "Linked";
    private static final String PENDING_STATUS = "Pending";
    private static final String AUTOMATIC_SOURCE = "Automatic";
    private static final int MAX_ERROR_LENGTH = 512;
    private final AlarmContextLinkRepository repository;
    private final ProductionContextQueryPort contextQuery;
    private final Clock clock;
    private final IotIdempotencyExecutor idempotency;

    public AlarmContextLinkApplicationServiceImpl(AlarmContextLinkRepository repository,
                                                   ProductionContextQueryPort contextQuery) {
        this(repository, contextQuery, Clock.systemUTC(), defaultIdempotency());
    }

    /** 测试构造入口；生产构造仍使用系统 UTC 时钟。 */
    public AlarmContextLinkApplicationServiceImpl(AlarmContextLinkRepository repository,
                                                   ProductionContextQueryPort contextQuery,
                                                   Clock clock) {
        this(repository, contextQuery, clock, defaultIdempotency());
    }

    /** 生产装配构造器：使用 IoT PostgreSQL 幂等存储，避免人工上下文命令重启后重复写入。 */
    @Autowired
    public AlarmContextLinkApplicationServiceImpl(AlarmContextLinkRepository repository,
                                                   ProductionContextQueryPort contextQuery,
                                                   IotIdempotencyExecutor idempotency) {
        this(repository, contextQuery, Clock.systemUTC(), idempotency);
    }

    /** 创建可替换时间和幂等器的上下文补链服务。 */
    public AlarmContextLinkApplicationServiceImpl(AlarmContextLinkRepository repository,
                                                   ProductionContextQueryPort contextQuery,
                                                   Clock clock,
                                                   IotIdempotencyExecutor idempotency) {
        this.repository = repository;
        this.contextQuery = contextQuery;
        this.clock = clock;
        this.idempotency = idempotency;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContextLinkResult link(UUID tenantId, UUID alarmId) {
        requireIds(tenantId, alarmId);
        Optional<AlarmContextCandidate> candidate = repository.findAlarm(tenantId, alarmId);
        if (candidate.isEmpty()) {
            return new ContextLinkResult(alarmId, ContextLinkResult.Status.NOT_FOUND, null, 0,
                    "告警不存在或不属于当前租户");
        }
        if (isLinked(candidate.get())) {
            return new ContextLinkResult(alarmId, ContextLinkResult.Status.ALREADY_LINKED, null, 0,
                    "告警已有业务上下文");
        }
        OffsetDateTime now = now();
        repository.enqueue(tenantId, alarmId, now);
        Optional<ContextLinkTask> task = repository.claimDue(tenantId, alarmId, now);
        return task.map(value -> attempt(candidate.get(), value))
                .orElseGet(() -> new ContextLinkResult(alarmId, ContextLinkResult.Status.NOT_DUE,
                        null, 0, "补链任务已存在且尚未到重试时间"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('iot:alarm:context')")
    public ContextLinkResult linkManually(UUID tenantId, UUID alarmId, UUID operationExecutionId,
                                           UUID workOrderId, String idempotencyKey) {
        requireIds(tenantId, alarmId);
        if (operationExecutionId == null || workOrderId == null) {
            throw new IotException(IotErrorCode.CONTEXT_INVALID,
                    "人工补链必须同时提供 operation_execution_id 与 work_order_id");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IotException(IotErrorCode.CONTEXT_INVALID,
                    "人工补链必须提供 1 到 128 个字符的 Idempotency-Key");
        }
        // 触发一次可信用户读取，保证内部误调用不会绕过人工补链的身份审计边界。
        UserContextHolder.requireUserId();
        String hash = digest(alarmId, operationExecutionId, workOrderId);
        return idempotency.execute("iot:alarm:context", tenantId, idempotencyKey.trim(), hash,
                ContextLinkResult.class,
                () -> doManualLink(tenantId, alarmId, operationExecutionId, workOrderId));
    }

    /** 执行一次人工上下文更新；只改写 IoT 告警的关联字段。 */
    private ContextLinkResult doManualLink(UUID tenantId, UUID alarmId, UUID operationExecutionId,
                                           UUID workOrderId) {
        if (repository.findAlarm(tenantId, alarmId).isEmpty()) {
            return new ContextLinkResult(alarmId, ContextLinkResult.Status.NOT_FOUND, null, 0,
                    "告警不存在或不属于当前租户");
        }
        boolean linked = repository.linkManually(tenantId, alarmId, operationExecutionId, workOrderId, now());
        if (!linked) {
            return new ContextLinkResult(alarmId, ContextLinkResult.Status.NOT_FOUND, null, 0,
                    "告警不存在或不属于当前租户");
        }
        return new ContextLinkResult(alarmId, ContextLinkResult.Status.LINKED, null, 0,
                "人工生产上下文已补充");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryDue(UUID tenantId, int limit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
        }
        int attempted = 0;
        OffsetDateTime now = now();
        while (attempted < limit) {
            Optional<ContextLinkTask> task = repository.claimNextDue(tenantId, now);
            if (task.isEmpty()) {
                break;
            }
            attempted++;
            Optional<AlarmContextCandidate> candidate = repository.findAlarm(tenantId, task.get().alarmId());
            if (candidate.isPresent() && !isLinked(candidate.get())) {
                attempt(candidate.get(), task.get());
            } else {
                repository.markCompleted(tenantId, task.get().id(), now);
            }
        }
        return attempted;
    }

    private ContextLinkResult attempt(AlarmContextCandidate alarm, ContextLinkTask task) {
        try {
            Optional<ProductionContextView> context = contextQuery.findActive(
                    alarm.tenantId(), alarm.deviceId(), alarm.alarmTime());
            if (context.isEmpty()) {
                return retry(alarm.id(), task, "Core 未返回告警时刻的唯一活动工序");
            }
            ProductionContextView value = validateContext(alarm, context.get());
            boolean linked = repository.linkAutomatically(alarm.tenantId(), alarm.id(), value, now());
            if (!linked) {
                Optional<AlarmContextCandidate> latest = repository.findAlarm(alarm.tenantId(), alarm.id());
                if (latest.isPresent() && isLinked(latest.get())) {
                    repository.markCompleted(alarm.tenantId(), task.id(), now());
                    return new ContextLinkResult(alarm.id(), ContextLinkResult.Status.ALREADY_LINKED,
                            null, task.retryCount(), "告警已由并发请求补链");
                }
                return retry(alarm.id(), task, "告警上下文已变化，未覆盖现有事实");
            }
            repository.markCompleted(alarm.tenantId(), task.id(), now());
            return new ContextLinkResult(alarm.id(), ContextLinkResult.Status.LINKED, value,
                    task.retryCount(), "自动生产上下文已补充");
        } catch (RuntimeException exception) {
            return retry(alarm.id(), task, safeMessage(exception));
        }
    }

    private ProductionContextView validateContext(AlarmContextCandidate alarm, ProductionContextView value) {
        if (!alarm.tenantId().equals(value.tenantId()) || !alarm.deviceId().equals(value.deviceId())
                || value.startedAt().isAfter(alarm.alarmTime()) || value.eventAt().isAfter(alarm.alarmTime())) {
            throw new IllegalArgumentException("Core 返回的生产上下文与告警租户、设备或时间不一致");
        }
        return value;
    }

    private ContextLinkResult retry(UUID alarmId, ContextLinkTask task, String error) {
        int retryCount = task.retryCount() + 1;
        OffsetDateTime next = now().plus(backoff(retryCount));
        repository.markRetry(task.tenantId(), task.id(), retryCount, next, error, now());
        return new ContextLinkResult(alarmId, ContextLinkResult.Status.RETRY_SCHEDULED,
                null, retryCount, error);
    }

    private Duration backoff(int retryCount) {
        long seconds = Math.min(3600L, 5L * (1L << Math.min(9, Math.max(0, retryCount - 1))));
        return Duration.ofSeconds(seconds);
    }

    private boolean isLinked(AlarmContextCandidate candidate) {
        return LINKED_STATUS.equals(candidate.contextStatus())
                && candidate.operationExecutionId() != null && candidate.workOrderId() != null;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Core 生产上下文查询失败";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private void requireIds(UUID tenantId, UUID alarmId) {
        if (tenantId == null || alarmId == null) {
            throw new IllegalArgumentException("tenantId 和 alarmId 不能为空");
        }
    }

    /** 计算人工补链载荷摘要，保证同一幂等键不能替换上下文标识。 */
    private String digest(UUID alarmId, UUID operationExecutionId, UUID workOrderId) {
        String value = alarmId + "\n" + operationExecutionId + "\n" + workOrderId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    /** 构造 focused 测试用的内存幂等器；生产由 Spring 注入 PostgreSQL 存储。 */
    private static IotIdempotencyExecutor defaultIdempotency() {
        return new IotIdempotencyExecutor(new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules());
    }
}
