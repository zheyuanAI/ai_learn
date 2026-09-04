package com.ailearn.platform.core.gis.ports;

import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import java.util.Objects;

/**
 * GIS 点位幂等查询结果。
 * <p>
 * 摘要来自服务端规范化命令，不能由客户端提供；应用层用它判断跨请求重放是否载荷一致。
 * </p>
 */
public record MapPointIdempotencyRecord(MapPointConfiguration point, String payloadDigest) {

    public MapPointIdempotencyRecord {
        Objects.requireNonNull(point, "点位不能为空");
        if (payloadDigest == null || !payloadDigest.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("点位 payloadDigest 必须是 64 位十六进制字符串");
        }
        payloadDigest = payloadDigest.toLowerCase(java.util.Locale.ROOT);
    }
}
