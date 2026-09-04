package com.ailearn.platform.core.gis.domain;

import java.util.Locale;
import java.util.Objects;

/** 二维底图的不可变资源元数据，实际文件存储由宿主资源服务负责。 */
public record MapAssetMetadata(String storageKey, String mimeType, long sizeBytes, String sha256) {

    public MapAssetMetadata {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("底图 storageKey 不能为空");
        }
        Objects.requireNonNull(mimeType, "底图 mimeType 不能为空");
        String normalizedMime = mimeType.toLowerCase(Locale.ROOT);
        if (!SetOfSupportedMimeTypes.contains(normalizedMime)) {
            throw new IllegalArgumentException("底图只支持 PNG/JPEG/WebP");
        }
        if (sizeBytes <= 0 || sizeBytes > 5 * 1024 * 1024L) {
            throw new IllegalArgumentException("底图大小必须在 1 字节至 5 MiB 之间");
        }
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("底图 sha256 必须是 64 位十六进制字符串");
        }
        mimeType = normalizedMime;
        sha256 = sha256.toLowerCase(Locale.ROOT);
    }

    private static final class SetOfSupportedMimeTypes {
        private static boolean contains(String mimeType) {
            return "image/png".equals(mimeType) || "image/jpeg".equals(mimeType)
                    || "image/webp".equals(mimeType);
        }
    }
}
