package com.ailearn.platform.core.gis.dto;

import com.ailearn.platform.core.gis.domain.MapEntityType;
import java.util.UUID;

/** 保存二维点位命令；租户从可信上下文取得。 */
public record SaveMapPointCommand(UUID siteMapId, MapEntityType entityType, UUID entityId,
                                  double xPercent, double yPercent, double rotation,
                                  String linkedPage) {
}
