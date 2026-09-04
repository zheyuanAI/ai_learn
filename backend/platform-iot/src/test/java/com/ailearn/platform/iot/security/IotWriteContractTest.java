package com.ailearn.platform.iot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.iot.credential.application.DeviceCredentialApplicationServiceImpl;
import com.ailearn.platform.iot.device.application.DeviceApplicationServiceImpl;
import com.ailearn.platform.iot.device.config.IoTMyBatisConfig;
import com.ailearn.platform.iot.profile.application.DeviceProfileApplicationServiceImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class IotWriteContractTest {

    @Test
    void managementWritesDeclarePermissionAndRollbackForAnyException() throws Exception {
        assertWriteContract(DeviceApplicationServiceImpl.class, "create",
                "hasAuthority('iot:device:manage')");
        assertWriteContract(DeviceApplicationServiceImpl.class, "changeLifecycle",
                "hasAuthority('iot:device:manage')");
        assertWriteContract(DeviceProfileApplicationServiceImpl.class, "create",
                "hasAuthority('iot:device:manage')");
        assertWriteContract(DeviceProfileApplicationServiceImpl.class, "createRule",
                "hasAuthority('iot:device:manage')");
        assertWriteContract(DeviceCredentialApplicationServiceImpl.class, "create",
                "hasAuthority('iot:device:manage')");
        assertWriteContract(DeviceCredentialApplicationServiceImpl.class, "revoke",
                "hasAuthority('iot:device:manage')");
    }

    @Test
    void mybatisOnlyScansExplicitMapperInterfaces() {
        MapperScan scan = IoTMyBatisConfig.class.getAnnotation(MapperScan.class);

        assertEquals(Mapper.class, scan.annotationClass());
    }

    private void assertWriteContract(Class<?> type, String methodName, String permission) throws Exception {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        assertEquals(permission, method.getAnnotation(PreAuthorize.class).value());
        Class<?>[] rollbackFor = method.getAnnotation(Transactional.class).rollbackFor();
        assertTrue(Arrays.asList(rollbackFor).contains(Exception.class));
    }
}
