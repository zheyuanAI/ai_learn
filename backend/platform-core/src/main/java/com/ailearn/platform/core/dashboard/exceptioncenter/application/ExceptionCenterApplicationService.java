package com.ailearn.platform.core.dashboard.exceptioncenter.application;

import com.ailearn.platform.core.dashboard.exceptioncenter.domain.ExceptionCenterPage;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;

/** 异常中心只读应用端口；异常记录从库存、制造和 IoT Facts 即时派生。 */
public interface ExceptionCenterApplicationService {
    /** 查询当前租户异常分页。 */
    ExceptionCenterPage query(FactsQueryContext context, String timeRange, String source,
                              String severity, int page, int size);
}
