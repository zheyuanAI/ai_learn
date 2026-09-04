package com.ailearn.platform.core.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.masterdata.domain.entity.Customer;
import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.sales.application.SalesOrderApplicationServiceImpl;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderLineRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderSaveRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 销售订单基础应用服务测试。
 * <p>
 * 不连接数据库，覆盖双轴状态、数量派生、草稿冻结、可信审计、主数据租户隔离和操作域幂等。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID LINE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000002");
    private static final String SESSION_ID = "jti-sales-test";
    private static final String REQUEST_ID = "request-sales-test";
    private static final OffsetDateTime TIME = OffsetDateTime.of(2026, 9, 3, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private SalesOrderRepository repository;

    @Mock
    private MasterDataRepository<Customer> customerRepository;

    @Mock
    private MasterDataRepository<Product> productRepository;

    private SalesOrderApplicationServiceImpl service;

    /**
     * 设置可信租户、用户、会话和请求上下文。
     */
    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_A);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti(SESSION_ID);
        RequestContextHolder.getContext().setRequestId(REQUEST_ID);
        lenient().when(customerRepository.findById(eq(TENANT_A), eq(CUSTOMER_ID))).thenReturn(Optional.of(customer()));
        lenient().when(productRepository.findById(eq(TENANT_A), eq(PRODUCT_ID))).thenReturn(Optional.of(product()));
        service = new SalesOrderApplicationServiceImpl(repository, customerRepository, productRepository);
    }

    /**
     * 清理线程上下文，避免测试间串租户和会话。
     */
    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    /**
     * 创建订单为 Draft，履约状态为 NotStarted，且数量初始化为零。
     */
    @Test
    void createStartsDraftWithDerivedNotStarted() {
        when(repository.insert(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderView result = service.create(saveRequest("SO-001", "10"), "sales-create-1");

        assertEquals("Draft", result.getStatus());
        assertEquals("NotStarted", result.getFulfillmentStatus());
        assertEquals("10.000000", result.getLines().getFirst().orderedQty());
        assertEquals("0.000000", result.getLines().getFirst().activeReservedQty());
        assertEquals(List.of("update", "submit"), result.getAllowedActions().stream()
                .map(action -> action.getAction()).toList());
    }

    /**
     * 状态按 Draft -> Submitted -> Approved -> Completed 推进，审核不触发库存调用（本服务没有库存依赖）。
     */
    @Test
    void lifecycleTransitionsAreStrict() {
        SalesOrder draft = order(SalesOrderStatus.Draft, line("10", "0", "0", "0"), 0);
        SalesOrder submitted = draft.submit(USER_ID, TIME);
        SalesOrder approved = submitted.approve(USER_ID, TIME);
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(draft));
        when(repository.updateState(any(SalesOrder.class), any(Long.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("Submitted", service.submit(ORDER_ID, "sales-submit-1").getStatus());
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(submitted));
        assertEquals("Approved", service.approve(ORDER_ID, "sales-approve-1").getStatus());
        assertEquals(SalesOrderStatus.Approved, approved.status());
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(approved));
        assertThrows(SalesOrderException.class, () -> service.submit(ORDER_ID, "sales-submit-invalid-1"));
    }

    /**
     * 订单行派生五类数量和履约状态，人工完成后仍保留真实已发货数量。
     */
    @Test
    void derivedQuantitiesFollowTheFourCumulativeFacts() {
        SalesOrderLine line = new SalesOrderLine(LINE_ID, TENANT_A, 1, PRODUCT_ID, "件",
                qty("100"), qty("40"), qty("40"), qty("20"));
        SalesOrder order = order(SalesOrderStatus.Approved, line, 3);
        SalesOrderView view = new SalesOrderView(order, List.of());

        assertEquals("60.000000", view.getLines().getFirst().unreservedQty());
        assertEquals("0.000000", view.getLines().getFirst().unpickedQty());
        assertEquals("20.000000", view.getLines().getFirst().shippingStagedQty());
        assertEquals("20.000000", view.getLines().getFirst().activeReservedQty());
        assertEquals("80.000000", view.getLines().getFirst().unshippedQty());
        assertEquals("InProgress", view.getFulfillmentStatus());
    }

    /**
     * 非 Draft 订单修改被拒绝，不覆盖订单或明细。
     */
    @Test
    void onlyDraftCanBeUpdated() {
        SalesOrder approved = order(SalesOrderStatus.Approved, line("10", "0", "0", "0"), 1);
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(approved));

        assertThrows(SalesOrderException.class,
                () -> service.update(ORDER_ID, saveRequest("SO-001", "20"), "sales-update-locked-1"));

        verify(repository, never()).update(any(SalesOrder.class), any(Long.class));
    }

    /**
     * 人工完成必须拒绝发货暂存数量，并在成功时只记录可信审计，不伪造履约事实。
     */
    @Test
    void manualCompletionRequiresNoShippingStagingAndRecordsTrustedAudit() {
        SalesOrder staged = order(SalesOrderStatus.Approved, line("10", "5", "5", "3"), 1);
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(staged));
        SalesOrderCompleteRequest request = completeRequest("客户取消剩余需求");

        assertThrows(SalesOrderException.class,
                () -> service.manuallyComplete(ORDER_ID, request, "sales-complete-staged-1"));

        SalesOrder ready = order(SalesOrderStatus.Approved, line("10", "5", "5", "5"), 1);
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(ready));
        when(repository.updateState(any(SalesOrder.class), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderView result = service.manuallyComplete(ORDER_ID, request, "sales-complete-ok-1");

        assertEquals("Completed", result.getStatus());
        assertEquals("Manual", result.getCompletionType());
        assertEquals("客户取消剩余需求", result.getCompletionReason());
        assertEquals(USER_ID, result.getCompletedBy());
        assertEquals(SESSION_ID, result.getCompletedSessionId());
        assertNotNull(result.getCompletedAt());
        assertEquals("5.000000", result.getLines().getFirst().shippedQty());
    }

    /**
     * 跨租户客户或产品按不可见处理，并且不写入销售订单。
     */
    @Test
    void masterDataMustBelongToTrustedTenant() {
        when(customerRepository.findById(TENANT_A, CUSTOMER_ID)).thenReturn(Optional.empty());
        assertThrows(SalesOrderException.class, () -> service.create(saveRequest("SO-CROSS-CUSTOMER", "1"),
                "sales-cross-customer-1"));
        verify(repository, never()).insert(any(SalesOrder.class));

        when(customerRepository.findById(TENANT_A, CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(TENANT_A, PRODUCT_ID)).thenReturn(Optional.empty());
        assertThrows(SalesOrderException.class, () -> service.create(saveRequest("SO-CROSS-PRODUCT", "1"),
                "sales-cross-product-1"));
    }

    /**
     * 同一租户、同一操作域、同一幂等键同载荷重放首次结果，不重复写入。
     */
    @Test
    void sameCreatePayloadIsIdempotent() {
        when(repository.insert(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SalesOrderSaveRequest request = saveRequest("SO-IDEMPOTENT", "3");

        SalesOrderView first = service.create(request, "sales-create-repeat-1");
        SalesOrderView replay = service.create(request, "sales-create-repeat-1");

        assertEquals(first.getId(), replay.getId());
        assertEquals(first.getLines().getFirst().orderedQty(), replay.getLines().getFirst().orderedQty());
        verify(repository).insert(any(SalesOrder.class));
    }

    /**
     * 同一操作域和幂等键携带不同载荷时返回冲突，不得新增订单。
     */
    @Test
    void differentCreatePayloadWithSameKeyIsRejected() {
        when(repository.insert(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(saveRequest("SO-IDEMPOTENT-CONFLICT", "3"), "sales-create-conflict-1");

        assertThrows(BaseException.class,
                () -> service.create(saveRequest("SO-IDEMPOTENT-CONFLICT", "4"), "sales-create-conflict-1"));
        verify(repository).insert(any(SalesOrder.class));
    }

    /**
     * 同一原始 Key 在不同销售操作域中独立计数，避免创建结果污染提交命令。
     */
    @Test
    void idempotencyKeyIsScopedByOperation() {
        when(repository.insert(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SalesOrder draft = order(SalesOrderStatus.Draft, line("3", "0", "0", "0"), 0);
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(draft));
        when(repository.updateState(any(SalesOrder.class), eq(0L))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(saveRequest("SO-IDEMPOTENT-SCOPE", "3"), "sales-shared-key-1");
        SalesOrderView submitted = service.submit(ORDER_ID, "sales-shared-key-1");

        assertEquals("Submitted", submitted.getStatus());
        verify(repository).updateState(any(SalesOrder.class), eq(0L));
    }

    /**
     * 四类订单行数量不满足单调链时拒绝构造，避免履约边界被绕过。
     */
    @Test
    void invalidQuantityChainIsRejected() {
        assertThrows(SalesOrderException.class, () -> new SalesOrderLine(LINE_ID, TENANT_A, 1, PRODUCT_ID, "件",
                qty("10"), qty("4"), qty("5"), qty("0")));
    }

    /**
     * 订单数量必须能够无损写入 NUMERIC(19,6)。
     */
    @Test
    void quantityScaleMustFitDatabaseContract() {
        assertThrows(SalesOrderException.class, () -> new SalesOrderLine(LINE_ID, TENANT_A, 1, PRODUCT_ID, "件",
                new BigDecimal("1.0000001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    /**
     * 构造有效客户主数据。
     */
    private Customer customer() {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        customer.setTenantId(TENANT_A);
        customer.setStatus("ACTIVE");
        customer.setCustomerName("测试客户");
        return customer;
    }

    /**
     * 构造有效产品主数据。
     */
    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setTenantId(TENANT_A);
        product.setStatus("ACTIVE");
        product.setUom("件");
        return product;
    }

    private SalesOrderSaveRequest saveRequest(String soNo, String quantity) {
        SalesOrderLineRequest line = new SalesOrderLineRequest();
        line.setLineNo(1);
        line.setProductId(PRODUCT_ID);
        line.setUom("件");
        line.setOrderedQty(quantity);
        SalesOrderSaveRequest request = new SalesOrderSaveRequest();
        request.setSoNo(soNo);
        request.setCustomerId(CUSTOMER_ID);
        request.setLines(List.of(line));
        return request;
    }

    private SalesOrderCompleteRequest completeRequest(String reason) {
        SalesOrderCompleteRequest request = new SalesOrderCompleteRequest();
        request.setCompletionReason(reason);
        return request;
    }

    private SalesOrder order(SalesOrderStatus status, SalesOrderLine line, long version) {
        return new SalesOrder(ORDER_ID, TENANT_A, "SO-001", CUSTOMER_ID, null, status,
                null, null, null, null, null, null, version, USER_ID, TIME, USER_ID, TIME, List.of(line));
    }

    private SalesOrderLine line(String ordered, String reserved, String picked, String shipped) {
        return new SalesOrderLine(LINE_ID, TENANT_A, 1, PRODUCT_ID, "件", qty(ordered), qty(reserved),
                qty(picked), qty(shipped));
    }

    private BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
