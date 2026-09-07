package com.ailearn.platform.iot.contextlink.application;

import com.ailearn.platform.iot.contextlink.domain.port.AlarmContextLinkRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 告警上下文补链后台调度器；只领取到期任务，Core 不可用时由应用服务保留 Retry 状态。
 */
@Component
public class AlarmContextLinkRetryScheduler {
    private final AlarmContextLinkRepository repository;
    private final AlarmContextLinkApplicationService service;
    private final Clock clock;

    /** 生产构造器。 */
    @Autowired
    public AlarmContextLinkRetryScheduler(AlarmContextLinkRepository repository,
                                          AlarmContextLinkApplicationService service) {
        this(repository, service, Clock.systemUTC());
    }

    /** 测试构造器；允许固定时钟验证任务调度边界。 */
    public AlarmContextLinkRetryScheduler(AlarmContextLinkRepository repository,
                                          AlarmContextLinkApplicationService service, Clock clock) {
        this.repository = repository;
        this.service = service;
        this.clock = clock;
    }

    /** 每隔固定延迟扫描到期租户；单次每租户最多领取 50 条，避免阻塞遥测线程。 */
    @Scheduled(fixedDelayString = "${iot.context-link.retry-delay-ms:5000}")
    public void retryDueTasks() {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        for (UUID tenantId : repository.findDueTenantIds(now, 16)) {
            service.retryDue(tenantId, 50);
        }
    }
}
