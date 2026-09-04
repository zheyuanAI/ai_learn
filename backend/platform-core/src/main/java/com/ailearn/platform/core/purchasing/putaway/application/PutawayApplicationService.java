package com.ailearn.platform.core.purchasing.putaway.application;

import com.ailearn.platform.core.purchasing.putaway.dto.PutawayConfirmRequest;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayTaskPageView;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayTaskView;
import java.util.UUID;

/**
 * 采购放行货物上架应用端口。
 */
public interface PutawayApplicationService {

    PutawayTaskView confirm(UUID taskId, PutawayConfirmRequest request, String idempotencyKey);

    PutawayTaskPageView page(String status, int page, int size);
}
