package com.ailearn.platform.iot.contextlink;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationService;
import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkRetryScheduler;
import com.ailearn.platform.iot.contextlink.domain.port.AlarmContextLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证上下文补链后台任务按到期租户领取，并把每租户批量上限固定为 50。 */
class AlarmContextLinkRetrySchedulerTest {

    @Test
    void productionConstructorIsMarkedForSpringInjection() {
        boolean hasSpringConstructor = false;
        for (Constructor<?> constructor : AlarmContextLinkRetryScheduler.class.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 2 && constructor.isAnnotationPresent(Autowired.class)) {
                hasSpringConstructor = true;
                break;
            }
        }

        assertTrue(hasSpringConstructor, "生产构造器必须显式标记 @Autowired，避免测试构造器让 Spring 误判为无参装配");
    }

    @Test
    void retriesDueTasksByTenantWithBoundedBatchSize() {
        UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        AlarmContextLinkRepository repository = Mockito.mock(AlarmContextLinkRepository.class);
        AlarmContextLinkApplicationService service = Mockito.mock(AlarmContextLinkApplicationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneOffset.UTC);
        when(repository.findDueTenantIds(OffsetDateTime.parse("2026-09-04T02:00:00Z"), 16))
                .thenReturn(Set.of(tenantId));

        new AlarmContextLinkRetryScheduler(repository, service, clock).retryDueTasks();

        verify(service).retryDue(tenantId, 50);
    }
}
