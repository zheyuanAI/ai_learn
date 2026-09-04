package com.ailearn.platform.iot.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.ailearn.platform.iot.device.infrastructure.PostgresIotRepository;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class PostgresIotRepositoryTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void profilePageLoadsMetricWhitelistFromDatabase() {
        DeviceProfile profileWithoutMetrics = new DeviceProfile(PROFILE_ID, TENANT_ID, "machine", "机台", "ACTIVE",
                60, List.of(), USER_ID, NOW, USER_ID, NOW);
        DeviceProfile.MetricDefinition metric = new DeviceProfile.MetricDefinition(
                "temperature", "温度", MetricValueType.NUMBER, "C", true);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            return sql.contains("iot_device_profile_metric") ? List.of(metric) : List.of(profileWithoutMetrics);
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        List<DeviceProfile> result = new PostgresIotRepository(jdbc).findPage(TENANT_ID, null, 0, 20);

        assertEquals(List.of(metric), result.getFirst().metrics());
    }
}
