package com.ailearn.platform.auth;

import com.ailearn.platform.auth.config.TimestampWithTimeZoneLocalDateTimeTypeHandler;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostgreSQL TIMESTAMPTZ 与 LocalDateTime 类型处理器单元测试。
 */
class TimestampWithTimeZoneLocalDateTimeTypeHandlerTest {

    private final TimestampWithTimeZoneLocalDateTimeTypeHandler handler =
            new TimestampWithTimeZoneLocalDateTimeTypeHandler();

    /**
     * 校验 PostgreSQL 驱动常见的 OffsetDateTime 返回值可以正确转换。
     * 入参：无；出参：无；流程：模拟结果集返回带时区时间并断言本地时间部分保持一致。
     *
     * @throws Exception 模拟 JDBC 访问失败时抛出
     */
    @Test
    @DisplayName("TIMESTAMPTZ 返回 OffsetDateTime 时应转换为 LocalDateTime")
    void shouldConvertOffsetDateTimeResult() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        OffsetDateTime value = OffsetDateTime.of(2026, 9, 1, 10, 20, 30, 0, ZoneOffset.ofHours(8));
        when(resultSet.getObject("created_at")).thenReturn(value);

        assertEquals(value.toLocalDateTime(), handler.getNullableResult(resultSet, "created_at"));
    }

    /**
     * 校验 H2 等测试数据库返回的 Timestamp 仍可读取。
     * 入参：无；出参：无；流程：模拟结果集返回 Timestamp 并断言转换结果。
     *
     * @throws Exception 模拟 JDBC 访问失败时抛出
     */
    @Test
    @DisplayName("普通 Timestamp 返回值应保持兼容")
    void shouldConvertTimestampResult() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        LocalDateTime value = LocalDateTime.of(2026, 9, 1, 10, 20, 30);
        when(resultSet.getObject(1)).thenReturn(Timestamp.valueOf(value));

        assertEquals(value, handler.getNullableResult(resultSet, 1));
    }

    /**
     * 校验写入参数使用标准 Timestamp，避免 PostgreSQL 驱动尝试直接转换 LocalDateTime。
     * 入参：无；出参：无；流程：模拟预编译语句并验证写入值。
     *
     * @throws Exception 模拟 JDBC 访问失败时抛出
     */
    @Test
    @DisplayName("LocalDateTime 写入应使用 Timestamp")
    void shouldWriteAsTimestamp() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        LocalDateTime value = LocalDateTime.of(2026, 9, 1, 10, 20, 30);

        handler.setNonNullParameter(statement, 2, value, null);

        verify(statement).setTimestamp(2, Timestamp.valueOf(value));
    }
}
