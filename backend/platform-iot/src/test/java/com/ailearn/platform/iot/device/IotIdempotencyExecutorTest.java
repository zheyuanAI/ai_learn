package com.ailearn.platform.iot.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;

class IotIdempotencyExecutorTest {

    @Test
    void sameCommandRunsOnceAndSensitiveCacheContainsOnlyRedactedResult() {
        IdempotencyStorage storage = new InMemoryIdempotencyStorage();
        IotIdempotencyExecutor executor = new IotIdempotencyExecutor(storage, new ObjectMapper());
        AtomicInteger calls = new AtomicInteger();
        java.util.UUID tenantId = java.util.UUID.randomUUID();

        String first = executor.execute("iot:credential:create", tenantId, "secret-key", "hash",
                String.class, () -> {
                    calls.incrementAndGet();
                    return "plain-secret";
                }, ignored -> "redacted");
        String replay = executor.execute("iot:credential:create", tenantId, "secret-key", "hash",
                String.class, () -> {
                    calls.incrementAndGet();
                    return "unexpected-second-secret";
                }, ignored -> "redacted");

        assertEquals("plain-secret", first);
        assertEquals("redacted", replay);
        assertEquals(1, calls.get());
        assertTrue(storage.getRecord("iot:credential:create", "secret-key", java.util.UUID.randomUUID()).isEmpty(),
                "不同租户的幂等记录必须隔离");
    }

    @Test
    void sameRawKeyCanBeUsedByDifferentOperations() {
        IdempotencyStorage storage = new InMemoryIdempotencyStorage();
        IotIdempotencyExecutor executor = new IotIdempotencyExecutor(storage, new ObjectMapper());
        java.util.UUID tenantId = java.util.UUID.randomUUID();

        assertEquals("profile", executor.execute("iot:profile:create", tenantId, "same-key", "profile-hash",
                String.class, () -> "profile"));
        assertEquals("device", executor.execute("iot:device:create", tenantId, "same-key", "device-hash",
                String.class, () -> "device"));
        assertEquals(IdempotentRecord.Status.SUCCESS,
                storage.getRecord("iot:profile:create", "same-key", tenantId).orElseThrow().getStatus());
        assertEquals(IdempotentRecord.Status.SUCCESS,
                storage.getRecord("iot:device:create", "same-key", tenantId).orElseThrow().getStatus());
    }

    @Test
    void successfulTransactionCachesOnlyAtBeforeCommit() {
        IdempotencyStorage storage = new InMemoryIdempotencyStorage();
        IotIdempotencyExecutor executor = new IotIdempotencyExecutor(storage, new ObjectMapper());
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        try {
            TransactionSynchronizationManager.initSynchronization();
            executor.execute("iot:device:create", tenantId, "transaction-key", "hash", String.class,
                    () -> "created");
            assertEquals(IdempotentRecord.Status.PENDING,
                    storage.getRecord("iot:device:create", "transaction-key", tenantId).orElseThrow().getStatus());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.beforeCommit(false);
            }
            assertEquals(IdempotentRecord.Status.SUCCESS,
                    storage.getRecord("iot:device:create", "transaction-key", tenantId).orElseThrow().getStatus());
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void failedTransactionReleasesItsClaimAfterRollback() {
        IdempotencyStorage storage = new InMemoryIdempotencyStorage();
        IotIdempotencyExecutor executor = new IotIdempotencyExecutor(storage, new ObjectMapper());
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        try {
            TransactionSynchronizationManager.initSynchronization();
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> executor.execute("iot:device:create", tenantId, "rollback-key", "hash", String.class,
                            () -> { throw new IllegalStateException("failed"); }));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertTrue(storage.getRecord("iot:device:create", "rollback-key", tenantId).isEmpty());
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }
}
