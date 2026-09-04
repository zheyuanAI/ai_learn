package com.ailearn.platform.core.inventory.controller;

import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.InventoryReservationPage;
import com.ailearn.platform.core.inventory.application.InventoryReservationQuery;
import com.ailearn.platform.core.inventory.application.InventoryTransactionPage;
import com.ailearn.platform.core.inventory.application.InventoryTransactionQuery;
import com.ailearn.platform.shared.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存事实查询 REST API；不提供绕过库存应用服务的写接口。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryQueryService queryService;

    /**
     * 注入库存查询端口。
     *
     * @param queryService 库存查询应用端口
     */
    public InventoryController(InventoryQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 查询余额、实物、预留和可用量。
     * 入参：可选产品/仓库/库位/批次和分页；出参：可信租户内余额分页；流程：服务端补可信租户后委托查询端口。
     */
    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('inv:balance:view')")
    public ApiResponse<InventoryBalancePage> balances(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String lotNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(queryService.queryBalances(
                new InventoryBalanceQuery(null, productId, warehouseId, locationId, lotNo, page, size)));
    }

    /**
     * 查询预留及其库位分配。
     * 入参：来源、状态、库存维度和分页；出参：可信租户内预留分页；流程：不接受客户端租户覆盖。
     */
    @GetMapping("/reservations")
    @PreAuthorize("hasAuthority('inv:reservation:view')")
    public ApiResponse<InventoryReservationPage> reservations(
            @RequestParam(required = false) UUID reservationId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) UUID sourceLineId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String lotNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(queryService.queryReservations(new InventoryReservationQuery(
                null, reservationId, sourceType, sourceId, sourceLineId, status, productId,
                warehouseId, locationId, lotNo, page, size)));
    }

    /**
     * 查询只追加库存流水。
     * 入参：交易、来源、维度、时间和分页；出参：可信租户内流水分页；流程：时间由 Spring 按 ISO-8601 解析。
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('inv:transaction:view')")
    public ApiResponse<InventoryTransactionPage> transactions(
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) UUID sourceLineId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime occurredTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(queryService.queryTransactions(new InventoryTransactionQuery(
                null, transactionType, sourceType, sourceId, sourceLineId, productId, warehouseId,
                locationId, lotNo, occurredFrom, occurredTo, page, size)));
    }
}
