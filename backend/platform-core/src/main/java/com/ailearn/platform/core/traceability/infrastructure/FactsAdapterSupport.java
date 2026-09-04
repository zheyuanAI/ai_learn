package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.ports.FactQueryUnavailableException;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** S7 真实 Facts 适配器共用的边界校验和时间转换工具。 */
final class FactsAdapterSupport {
    private FactsAdapterSupport() {
    }

    static FactsQueryContext context(FactsQueryRequest request) {
        return request == null ? null : request.context();
    }

    static FactsQueryContext context(TraceQuery query) {
        return query == null ? null : query.context();
    }

    static UUID filterUuid(FactsQueryRequest request, String key) {
        String value = request == null || request.filters() == null ? null : request.filters().get(key);
        return parseUuid(value, key);
    }

    static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new GisException(GisErrorCode.GIS_TENANT_001, field + " 不是有效的 UUID");
        }
    }

    static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    static boolean inRange(OffsetDateTime value, FactsQueryRequest request) {
        if (value == null || request == null) {
            return false;
        }
        Instant instant = value.toInstant();
        return !instant.isBefore(request.from()) && instant.isBefore(request.to());
    }

    static Instant later(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    static BigDecimal add(BigDecimal left, BigDecimal right) {
        return (left == null ? BigDecimal.ZERO : left)
                .add(right == null ? BigDecimal.ZERO : right);
    }

    static FactQueryUnavailableException unavailable(String source, RuntimeException exception) {
        if (exception instanceof FactQueryUnavailableException unavailable) {
            return unavailable;
        }
        return new FactQueryUnavailableException(source + " 事实查询不可用", exception);
    }
}
