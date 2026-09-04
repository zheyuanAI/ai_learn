package com.ailearn.platform.core.sales.application;

import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.dto.SalesOrderPageResult;
import com.ailearn.platform.core.sales.dto.SalesOrderSaveRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import java.util.UUID;

/**
 * 销售订单基础应用端口。
 */
public interface SalesOrderApplicationService {

    SalesOrderPageResult page(SalesOrderPageQuery query);

    SalesOrderView detail(UUID id);

    SalesOrderView create(SalesOrderSaveRequest request, String idempotencyKey);

    SalesOrderView update(UUID id, SalesOrderSaveRequest request, String idempotencyKey);

    SalesOrderView submit(UUID id, String idempotencyKey);

    SalesOrderView approve(UUID id, String idempotencyKey);

    SalesOrderView manuallyComplete(UUID id, SalesOrderCompleteRequest request, String idempotencyKey);
}
