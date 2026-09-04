package com.ailearn.platform.iot.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.profile.application.DeviceProfileApplicationService;
import com.ailearn.platform.iot.profile.application.DeviceProfileApplicationServiceImpl;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IotMethodSecurityTest.TestConfig.class)
class IotMethodSecurityTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestConfig {
        @Bean
        DeviceProfileRepository deviceProfileRepository() {
            return org.mockito.Mockito.mock(DeviceProfileRepository.class);
        }

        @Bean
        DeviceRepository deviceRepository() {
            return org.mockito.Mockito.mock(DeviceRepository.class);
        }

        @Bean
        IotIdempotencyExecutor iotIdempotencyExecutor() {
            return new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(), new ObjectMapper().findAndRegisterModules());
        }

        @Bean
        DeviceProfileApplicationServiceImpl deviceProfileApplicationService(DeviceProfileRepository repository,
                                                                              DeviceRepository deviceRepository,
                                                                              IotIdempotencyExecutor idempotency) {
            return new DeviceProfileApplicationServiceImpl(repository, idempotency, deviceRepository);
        }
    }

    @Autowired
    private DeviceProfileApplicationService service;

    @Autowired
    private DeviceProfileRepository repository;

    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "N/A", List.of(new SimpleGrantedAuthority("iot:device:view"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.clear();
    }

    @Test
    void manageWriteRequiresManageAuthority() {
        assertThrows(AccessDeniedException.class, () -> service.create(
                new com.ailearn.platform.iot.profile.dto.DeviceProfileCreateRequest("machine", "机台",
                        List.of(new com.ailearn.platform.iot.profile.dto.MetricDefinitionRequest(
                                "temperature", "温度", "NUMBER", "C", true)), 60),
                "security-denied"));
    }

    @Test
    void viewQueryRunsWithViewAuthorityAndTrustedTenant() {
        DeviceProfile profile = new DeviceProfile(PROFILE_ID, TENANT_ID, "machine", "机台", "ACTIVE", 60,
                List.of(new DeviceProfile.MetricDefinition("temperature", "温度", MetricValueType.NUMBER, "C", true)),
                USER_ID, NOW, USER_ID, NOW);
        when(repository.findPage(TENANT_ID, null, 0, 20)).thenReturn(List.of(profile));
        when(repository.count(TENANT_ID, null)).thenReturn(1L);

        assertEquals(1, service.page(null, 1, 20).records().size());
    }
}
