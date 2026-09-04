package com.ailearn.platform.iot.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.iot.alarm.application.AlarmApplicationServiceImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

/** 告警写命令的权限与回滚契约测试。 */
class AlarmWriteContractTest {
    @Test
    void acknowledgementRequiresPermissionAndRollbackForAnyException() throws Exception {
        Method method = Arrays.stream(AlarmApplicationServiceImpl.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("ack"))
                .findFirst().orElseThrow();
        assertEquals("hasAuthority('iot:alarm:ack')", method.getAnnotation(PreAuthorize.class).value());
        assertTrue(Arrays.asList(method.getAnnotation(Transactional.class).rollbackFor()).contains(Exception.class));
    }
}
