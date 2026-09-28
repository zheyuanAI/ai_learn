package com.ailearn.platform.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryApplicationService;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.inventory.infrastructure.InventoryRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 库存应用入口的幂等所有权回归：用可控过期和事务回调重现旧请求与重入请求的交错，不依赖休眠或数据库。
 */
@ExtendWith(MockitoExtension.class)
class InventoryIdempotencyOwnershipTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final String OPERATION = "inventory:increase";
    private static final String KEY = "inventory-ownership-test";
    private static final OffsetDateTime BUSINESS_TIME = OffsetDateTime.parse("2026-09-26T00:00:00Z");
    private static final InventoryDimension DIMENSION = new InventoryDimension(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000001"), "LOT-1");
    private static final InventoryBalance LOCKED = new InventoryBalance(UUID.randomUUID(), TENANT_ID,
            DIMENSION, new BigDecimal("10.000000"), BigDecimal.ZERO.setScale(6), 0L, BUSINESS_TIME);

    @Mock
    private InventoryRepository repository;

    @Mock
    private InventoryLocationPort locationPort;

    private InMemoryIdempotencyStorage storage;
    private InventoryApplicationService service;

    /**
     * 入参：无；出参：测试夹具；流程：建立可信上下文并为每个用例创建独立幂等存储。
     */
    @BeforeEach
    void setUp() {
        trustedContext();
        storage = new InMemoryIdempotencyStorage();
        service = new InventoryApplicationService(repository, locationPort, storage,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 入参：无；出参：无；流程：清理手工事务同步与用户上下文，避免回调泄漏到后续测试。
     */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        UserContextHolder.clear();
    }

    /**
     * 入参：无；出参：无；流程：让 A 执行失败，模拟 B 重入后再触发 A 的回滚清理，验证 B 的 token 保留。
     */
    @Test
    void rollbackOfOldClaimDoesNotReleaseReplacementClaim() {
        stubLocation();
        when(repository.lockOrCreateBalance(TENANT_ID, DIMENSION, USER_ID))
                .thenThrow(new IllegalStateException("模拟库存失败"));
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(IllegalStateException.class, () -> service.increase(command()));
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        IdempotencyClaim replacement = replaceExpiredClaim();
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertPendingOwner(replacement);
    }

    /**
     * 入参：无；出参：无；流程：在 A 写完业务事实后由 B 取得新 token，A 必须拒绝完成且不能释放 B。
     */
    @Test
    void ownershipLostDuringActionRejectsSuccessfulReturn() {
        stubIncrease();
        AtomicReference<IdempotencyClaim> replacement = new AtomicReference<>();
        when(repository.appendTransaction(any(InventoryTransaction.class))).thenAnswer(invocation -> {
            replacement.set(replaceExpiredClaim());
            return invocation.getArgument(0);
        });

        assertThrows(ServiceUnavailableException.class, () -> service.increase(command()));

        assertPendingOwner(replacement.get());
    }

    /**
     * 入参：无；出参：无；流程：A 返回后、事务提交前模拟 B 重入，A 的提交校验须失败且回滚清理不影响 B。
     */
    @Test
    void beforeCommitOfOldClaimCannotCompleteReplacementClaim() {
        stubIncrease();
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        service.increase(command());
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        IdempotencyClaim replacement = replaceExpiredClaim();

        assertThrows(ServiceUnavailableException.class,
                () -> callbacks.forEach(callback -> callback.beforeCommit(false)));
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertPendingOwner(replacement);
    }

    /**
     * 入参：无；出参：无；流程：当前 claim 的业务执行失败后触发回滚，验证自己的 PENDING 被释放并可重试。
     */
    @Test
    void rollbackReleasesCurrentPendingClaim() {
        stubLocation();
        when(repository.lockOrCreateBalance(TENANT_ID, DIMENSION, USER_ID))
                .thenThrow(new IllegalStateException("模拟库存失败"));
        TransactionSynchronizationManager.initSynchronization();
        assertThrows(IllegalStateException.class, () -> service.increase(command()));
        assertTrue(storage.getRecord(OPERATION, KEY, TENANT_ID).isPresent());

        TransactionSynchronizationManager.getSynchronizations().forEach(
                callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertFalse(storage.getRecord(OPERATION, KEY, TENANT_ID).isPresent());
        assertTrue(storage.tryAcquireClaim(OPERATION, KEY, TENANT_ID, Duration.ofHours(24), "retry").isPresent());
    }

    /**
     * 入参：无；出参：无；流程：用门闩暂停首次库存写，再并发调用同 Key，验证重复请求拒绝且只写一次。
     */
    @Test
    void concurrentSameKeyRejectsDuplicateWithoutSecondInventoryWrite() throws Exception {
        stubIncrease();
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.lockOrCreateBalance(TENANT_ID, DIMENSION, USER_ID)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return LOCKED;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<InventoryMutationResult> first = executor.submit(() -> {
                trustedContext();
                try {
                    return service.increase(command());
                } finally {
                    UserContextHolder.clear();
                }
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertThrows(InventoryException.class, () -> service.increase(command()));
            release.countDown();
            assertEquals("INCREASE", first.get(10, TimeUnit.SECONDS).operation());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        verify(repository, times(1)).updateBalance(any(), any(), any(), any(), any());
        verify(repository, times(1)).appendTransaction(any());
    }

    /**
     * 入参：无；出参：无；流程：为当前线程补齐租户、用户、会话和请求标识。
     */
    private void trustedContext() {
        TenantContextHolder.setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti("jti-inventory-owner");
        RequestContextHolder.getContext().setRequestId("request-inventory-owner");
    }

    /**
     * 入参：无；出参：增加库存命令；流程：使用固定业务字段保证并发请求载荷相同。
     */
    private InventoryIncreaseCommand command() {
        return new InventoryIncreaseCommand(new InventoryCommandMetadata(TENANT_ID, USER_ID,
                "jti-inventory-owner", "request-inventory-owner", KEY, "client-digest", "TEST",
                SOURCE_ID, null, "RECEIPT", BUSINESS_TIME), DIMENSION, new BigDecimal("2.000000"));
    }

    /**
     * 入参：无；出参：无；流程：注册当前租户已启用的普通存储位。
     */
    private void stubLocation() {
        when(locationPort.findByTenantIdAndId(TENANT_ID, DIMENSION.locationId())).thenReturn(new LocationSnapshot(
                DIMENSION.locationId(), TENANT_ID, DIMENSION.warehouseId(), LocationType.Storage, "ACTIVE"));
    }

    /**
     * 入参：无；出参：无；流程：模拟已锁定余额及增加后的余额，流水行为由各用例控制。
     */
    private void stubIncrease() {
        stubLocation();
        when(repository.lockOrCreateBalance(TENANT_ID, DIMENSION, USER_ID)).thenReturn(LOCKED);
        when(repository.updateBalance(eq(LOCKED), any(), any(), eq(BUSINESS_TIME), eq(USER_ID)))
                .thenReturn(new InventoryBalance(LOCKED.id(), TENANT_ID, DIMENSION,
                        new BigDecimal("12.000000"), BigDecimal.ZERO.setScale(6), 1L, BUSINESS_TIME));
    }

    /**
     * 入参：无；出参：替代 claim；流程：直接推进旧记录过期时间后原子重入，确定性模拟所有权切换。
     */
    private IdempotencyClaim replaceExpiredClaim() {
        IdempotentRecord old = storage.getRecord(OPERATION, KEY, TENANT_ID).orElseThrow();
        old.setExpireAt(OffsetDateTime.now().minusSeconds(1));
        return storage.tryAcquireClaim(OPERATION, KEY, TENANT_ID, Duration.ofHours(24),
                old.getRequestHash()).orElseThrow();
    }

    /**
     * 入参：应保留的 claim；出参：无；流程：检查替代记录仍为 PENDING 且 token 未被旧执行者覆盖。
     */
    private void assertPendingOwner(IdempotencyClaim claim) {
        IdempotentRecord current = storage.getRecord(OPERATION, KEY, TENANT_ID).orElseThrow();
        assertEquals(IdempotentRecord.Status.PENDING, current.getStatus());
        assertEquals(claim.token(), current.getClaimToken());
    }
}
