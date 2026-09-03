package com.ailearn.platform.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Jackson 序列化配置测试")
class JacksonConfigTest {

    static class SampleDto {
        private OffsetDateTime dateTime;
        private BigDecimal amount;

        public SampleDto() {}

        public SampleDto(OffsetDateTime dateTime, BigDecimal amount) {
            this.dateTime = dateTime;
            this.amount = amount;
        }

        public OffsetDateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(OffsetDateTime dateTime) {
            this.dateTime = dateTime;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    @Test
    @DisplayName("测试 OffsetDateTime 东八区格式化与 BigDecimal 不失真输出")
    void testSerialization() throws Exception {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        // 构造 UTC 时间 2026-09-01T07:30:00Z (东八区为 15:30:00+08:00)
        OffsetDateTime utcTime = OffsetDateTime.of(2026, 9, 1, 7, 30, 0, 0, ZoneOffset.UTC);
        BigDecimal amount = new BigDecimal("10000000.50");

        SampleDto dto = new SampleDto(utcTime, amount);
        String json = mapper.writeValueAsString(dto);

        // 验证东八区时间转换与格式
        assertTrue(json.contains("2026-09-01T15:30:00+08:00"), "时间应转换为+08:00时区格式: " + json);
        // 验证无科学计数法
        assertTrue(json.contains("10000000.50"), "BigDecimal 应保持纯数值格式: " + json);
        assertFalse(json.contains("1E"), "BigDecimal 不应出现科学计数法: " + json);

        // 验证反序列化
        SampleDto deserialized = mapper.readValue(json, SampleDto.class);
        assertTrue(deserialized.getDateTime().isEqual(utcTime));
    }
}
