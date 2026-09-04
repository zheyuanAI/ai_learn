package com.ailearn.platform.core.gis.dto;

import com.ailearn.platform.core.gis.domain.MapAssetMetadata;

/** 创建二维地图配置命令，不包含客户端 tenantId。 */
public record CreateSiteMapCommand(String mapCode, String mapName, MapAssetMetadata asset) {
}
